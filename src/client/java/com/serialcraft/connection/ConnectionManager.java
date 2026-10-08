package com.serialcraft.connection;

import com.serialcraft.client.SerialDebugHud;
import com.serialcraft.identity.BannerSniffer;
import com.serialcraft.identity.BoardHello;
import com.serialcraft.identity.BoardIdentity;
import com.serialcraft.signal.SignalRecorder;
import com.serialcraft.signal.SignalRecorder.Direction;
import com.serialcraft.network.SerialInputPayload;
import com.serialcraft.network.HardwareLinkPayload;
import com.serialcraft.network.ChannelInbox;
import com.serialcraft.network.SignalProtocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Punto unico de control de las conexiones de hardware del cliente.
 *
 * Antes el estado estaba repartido entre tres clases: el puerto en un campo
 * publico estatico de SerialCraftClient, el dispositivo activo en otro campo
 * estatico de PanelUI, y la velocidad en un tercero que nunca se escribia. Con
 * tres duenos, cerrar la conexion desde un sitio dejaba a los otros creyendo
 * que seguia abierta: de ahi las "conexiones fantasma" que el propio codigo
 * original mencionaba en un comentario.
 */
public final class ConnectionManager {

    private ConnectionManager() {}

    private static final SerialHandler SERIAL = new SerialHandler();
    private static final WifiHandler   WIFI   = new WifiHandler();
    private static final List<BoardLink> LINKS = List.of(SERIAL, WIFI);

    // ── Consola visual ─────────────────────────────────────────────────
    //
    // ArrayDeque en vez de CopyOnWriteArrayList. El original hacia
    // messageHistory.remove(0) sobre una CopyOnWriteArrayList: cada mensaje
    // copiaba el array DOS veces (una al quitar, otra al anadir). A 40
    // mensajes/segundo eso son 80 copias de array por segundo solo para
    // mantener ocho lineas en pantalla.
    private static final int MAX_HISTORY = 64;
    private static final Deque<String> HISTORY = new ArrayDeque<>(MAX_HISTORY);

    // ── Control de tasa de salida ──────────────────────────────────────
    //
    // El servidor ya limita la tasa de entrada, pero limitar tambien aqui evita
    // que el cliente se auto-desconecte por spam de paquetes (Minecraft expulsa
    // a los clientes que exceden su presupuesto) y ahorra ancho de banda.
    private static final ChannelInbox INBOX = new ChannelInbox();
    private static int reportedEpoch = -1;
    private static boolean reportedConnected;
    private static String reportedDimension = "";
    private static long bootResyncAt;

    // ── Identidad de la placa conectada ────────────────────────────────
    //
    // Tres fuentes, de menor a mayor fiabilidad (BoardIdentity.best se queda
    // con la mejor): descriptores USB, banner de arranque de la ROM del ESP, y
    // lo que la propia placa anuncia con mc_id. Un chip puente (CH340...) no
    // dice que placa hay detras; por eso, si no hay mejor dato, se pregunta con
    // la sonda mc_who unos segundos despues de conectar.
    private static final long[] PROBE_DELAYS_MS = {1500, 4000, 9000};
    private static final ScheduledExecutorService PROBES =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "SerialCraft-Probe");
                t.setDaemon(true);
                return t;
            });
    private static volatile BoardIdentity announced = BoardIdentity.unknown();
    private static volatile int linkEpoch = 0;
    private static final HardwareOutbox OUTBOX = new HardwareOutbox();
    static { PROBES.scheduleWithFixedDelay(ConnectionManager::drainOutput, 25, 25, TimeUnit.MILLISECONDS); }

    /** Mejor identidad conocida de la placa conectada (desconocida si no hay ninguna). */
    public static BoardIdentity activeIdentity() {
        BoardIdentity base = SERIAL.isConnected() ? SERIAL.getIdentity() : BoardIdentity.unknown();
        return BoardIdentity.best(base, announced);
    }

    /**
     * Un transporte acaba de quedar conectado.
     * @param known lo que ya se sabe de la placa (p. ej. una placa recordada), o desconocida
     */
    static void onLinkConnected(BoardLink link, BoardIdentity known) {
        final int epoch;
        synchronized (ConnectionManager.class) {
            announced = known;
            INBOX.clear();
            OUTBOX.clear();
            epoch = ++linkEpoch;
        }
        if (!BoardTrust.store().probeBoards()) return;
        for (long delay : PROBE_DELAYS_MS) {
            PROBES.schedule(() -> probe(epoch), delay, TimeUnit.MILLISECONDS);
        }
    }

    /** Un transporte se cerro: lo que se sabia de esa placa ya no vale. */
    static void onLinkClosed() {
        synchronized (ConnectionManager.class) {
            linkEpoch++;                       // cancela las sondas pendientes
            if (!isAnyConnected()) announced = BoardIdentity.unknown();
        }
    }

    private static void probe(int epoch) {
        if (epoch != linkEpoch || !isAnyConnected()) return;
        if (activeIdentity().confidence() == BoardIdentity.Confidence.DECLARED) return;
        deliver(BoardHello.PROBE);             // placas antiguas: la ignoran
    }

    private static void onHello(BoardLink source, String line) {
        BoardHello.parse(line).ifPresent(id -> {
            if (source == WIFI && !WIFI.onBoardHello(id)) return;
            announced = BoardIdentity.best(announced, id);
            SerialDebugHud.addLog("Placa identificada: " + id.model()
                    + (id.hasUid() ? " [" + id.uid() + "]" : ""));
        });
    }

    public static SerialHandler getSerial() { return SERIAL; }
    public static WifiHandler   getWifi()   { return WIFI; }

    /** Sustituye a las cuatro copias de esta misma condicion en la UI. */
    public static boolean isAnyConnected() {
        return SERIAL.isConnected() || WIFI.isConnected();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  SALIDA: Minecraft -> placa
    // ══════════════════════════════════════════════════════════════════════

    public static void sendMessageToBoard(String message) {
        enqueueOutput(message, HardwareOutbox.Kind.MANUAL, false);
    }

    public static void sendHardwareOutput(String message, boolean safetyStop) {
        enqueueOutput(message, HardwareOutbox.Kind.IO, safetyStop);
    }

    private static void enqueueOutput(String message, HardwareOutbox.Kind kind, boolean safetyStop) {
        if (!isAnyConnected()) {
            SerialDebugHud.addLog("Sin placa conectada (USB/Wi-Fi).");
            addHistory("ERR: sin conexion");
        } else if (!OUTBOX.offer(message, kind, false, safetyStop)) {
            SerialDebugHud.addLog("Salida rechazada: cola llena o linea invalida.");
            addHistory("ERR: salida rechazada");
        }
    }

    /**
     * Envia una linea de telemetria (protocolo mc_clave:valor). Distinta de
     * sendMessageToBoard en dos cosas, ambas a proposito:
     *
     *  - No registra "ERR: sin conexion" si no hay placa: el tracker ya
     *    comprueba la conexion, y un enlace que se cae entre esa comprobacion
     *    y este envio no merece una linea de error por cada dato pendiente.
     *  - Se registra con el prefijo "TM:", para distinguir en la consola lo que
     *    genera la pestana Eventos de lo que generan los Bloques IO (TX:).
     *    {@code quiet} suprime el registro de los reenvios periodicos.
     *
     * @return true si la cola acepto la linea; no es una confirmacion del hardware
     */
    public static boolean sendTelemetry(String line, boolean quiet) {
        return isAnyConnected() && OUTBOX.offer(line, HardwareOutbox.Kind.TELEMETRY, quiet, false);
    }

    /**
     * Envia una senal continua (la del generador de la pestana Visualizar).
     * Como {@link #sendTelemetry} no escribe en la consola, para no llenarla
     * con diez lineas por segundo, pero SI queda anotada en el registro de
     * senales para poder verla en pantalla junto a la respuesta de la placa.
     *
     * @return true si la cola acepto la linea; el visualizador registra la escritura posterior
     */
    public static boolean sendSignal(String line) {
        return sendSignal(line, false);
    }

    public static boolean sendSignal(String line, boolean safetyStop) {
        return isAnyConnected() && OUTBOX.offer(line, HardwareOutbox.Kind.SIGNAL, true, safetyStop);
    }

    public static void discardPending(String key) { OUTBOX.discard(key); }

    private static void drainOutput() {
        try {
            if (!isAnyConnected()) { OUTBOX.clear(); return; }
            int epoch = linkEpoch;
            HardwareOutbox.Line line = OUTBOX.poll();
            if (line == null || !deliver(line.text(), epoch)) return;
            SignalRecorder.INSTANCE.record(Direction.TX, line.text(), System.nanoTime());
            if (!line.quiet()) {
                String prefix = line.kind() == HardwareOutbox.Kind.TELEMETRY ? "TM: " : "TX: ";
                SerialDebugHud.addLog(prefix + line.text());
                addHistory(prefix + line.text());
            }
        } catch (RuntimeException e) {
            com.serialcraft.SerialCraft.LOGGER.warn("Error en la cola de salida", e);
        }
    }

    private static boolean deliver(String message) {
        return deliver(message, linkEpoch);
    }

    private static boolean deliver(String message, int epoch) {
        if (message == null || message.length() > 256 || message.indexOf('\n') >= 0 || message.indexOf('\r') >= 0) return false;
        boolean delivered = false;
        for (BoardLink link : LINKS) {
            if (link.isConnected()) delivered |= link.send(message, epoch);
        }
        return delivered;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ENTRADA: placa -> Minecraft
    // ══════════════════════════════════════════════════════════════════════

    /** Reader threads record every sample; only valid IO is queued for the client tick. */
    static void onMessageReceived(BoardLink source, String message) {
        if (message == null || message.length() > 256 || message.startsWith(BoardHello.KEY_PREFIX)) return;
        SerialDebugHud.addLog("RX: " + message);
        addHistory("RX: " + message);

        // Identificacion: se queda en el cliente, nunca llega al servidor.
        if (BoardHello.isHello(message)) { onHello(source, message); return; }
        if (announced.confidence() != BoardIdentity.Confidence.DECLARED) {
            BannerSniffer.identify(message).ifPresent(id -> announced = BoardIdentity.best(announced, id));
        }

        // Registro de senales: ANTES del filtro de abajo, que descarta lo que
        // llega demasiado seguido. Lo que se ve en Visualizar es lo que la placa
        // realmente envio, no lo que sobrevivio al filtro.
        SignalRecorder.INSTANCE.record(Direction.RX, message, System.nanoTime());

        SignalProtocol.parse(message).ifPresent(sample -> INBOX.offer(sample, System.nanoTime()));
    }

    // ══════════════════════════════════════════════════════════════════════

    public static void disconnectAll() {
        for (BoardLink link : LINKS) link.disconnect();
        synchronized (ConnectionManager.class) {
            INBOX.clear();
            OUTBOX.clear();
            reportedEpoch = -1;
            reportedConnected = false;
            reportedDimension = "";
            bootResyncAt = 0;
            announced = BoardIdentity.unknown();
            linkEpoch++;
        }
    }

    /** Etiqueta corta del transporte activo, para la UI. */
    public static Component describeActive() {
        for (BoardLink link : LINKS) {
            if (link.isConnected()) return link.describe();
        }
        return Component.translatable("gui.serialcraft.status.disconnected");
    }

    // ── Historial ──────────────────────────────────────────────────────

    private static void addHistory(String entry) {
        synchronized (HISTORY) {
            if (HISTORY.size() >= MAX_HISTORY) HISTORY.removeFirst();
            HISTORY.addLast(entry);
        }
    }

    /** @return copia de las ultimas {@code limit} entradas, de mas antigua a mas nueva. */
    public static List<String> recentHistory(int limit) {
        synchronized (HISTORY) {
            int skip = Math.max(0, HISTORY.size() - limit);
            return HISTORY.stream().skip(skip).toList();
        }
    }

    public static void clearHistory() {
        synchronized (HISTORY) { HISTORY.clear(); }
    }

    /** Hardware session changes precede IO data. 2 lines/tick respects the server's 40/s budget. */
    public static void tick(Minecraft client) {
        if (client.level == null || !ClientPlayNetworking.canSend(HardwareLinkPayload.TYPE)) return;
        boolean connected = isAnyConnected();
        String dimension = client.level.dimension().identifier().toString();
        boolean dimensionChanged = !dimension.equals(reportedDimension);
        boolean previousDimension = !reportedDimension.isEmpty();
        if (reportedEpoch != linkEpoch || reportedConnected != connected || dimensionChanged) {
            ClientPlayNetworking.send(new HardwareLinkPayload(connected, false));
            reportedEpoch = linkEpoch;
            reportedConnected = connected;
            reportedDimension = dimension;
            bootResyncAt = connected && SERIAL.isConnected() ? System.nanoTime() + 2_000_000_000L : 0;
            if (!connected || dimensionChanged && previousDimension) INBOX.clear();
        }
        if (connected && bootResyncAt != 0 && System.nanoTime() >= bootResyncAt) {
            ClientPlayNetworking.send(new HardwareLinkPayload(true, true));
            bootResyncAt = 0;
        }
        if (!connected || !ClientPlayNetworking.canSend(SerialInputPayload.TYPE)) return;
        for (int i = 0; i < 2; i++) {
            var sample = INBOX.poll();
            if (sample == null) break;
            ClientPlayNetworking.send(new SerialInputPayload(sample.channel() + ':' + sample.value()));
        }
    }

    public static int sessionEpoch() { return linkEpoch; }
    public static long droppedInputs() { return INBOX.dropped(); }
    public static long droppedOutputs() { return OUTBOX.dropped(); }
}
