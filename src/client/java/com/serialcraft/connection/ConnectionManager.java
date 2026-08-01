package com.serialcraft.connection;

import com.serialcraft.client.SerialDebugHud;
import com.serialcraft.network.SerialInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

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

    // ── Consola visual ────────────────────────────────────────────────────
    //
    // ArrayDeque en vez de CopyOnWriteArrayList. El original hacia
    // messageHistory.remove(0) sobre una CopyOnWriteArrayList: cada mensaje
    // copiaba el array DOS veces (una al quitar, otra al anadir). A 40
    // mensajes/segundo eso son 80 copias de array por segundo solo para
    // mantener ocho lineas en pantalla.
    private static final int MAX_HISTORY = 64;
    private static final Deque<String> HISTORY = new ArrayDeque<>(MAX_HISTORY);

    // ── Control de tasa de salida ─────────────────────────────────────────
    //
    // El servidor ya limita la tasa de entrada, pero limitar tambien aqui evita
    // que el cliente se auto-desconecte por spam de paquetes (Minecraft expulsa
    // a los clientes que exceden su presupuesto) y ahorra ancho de banda.
    private static final long   MIN_SEND_INTERVAL_NANOS = 25_000_000L; // 40 Hz
    private static long   lastSentNanos  = 0L;
    private static String lastSentMessage = null;

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
        boolean delivered = false;
        for (BoardLink link : LINKS) {
            if (link.isConnected()) { link.send(message); delivered = true; }
        }

        if (delivered) {
            addHistory("TX: " + message);
        } else {
            SerialDebugHud.addLog("Sin placa conectada (USB/Wi-Fi).");
            addHistory("ERR: sin conexion");
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  ENTRADA: placa -> Minecraft
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Se invoca desde los hilos lectores de SerialHandler y WifiHandler.
     *
     * Dos filtros antes de gastar un paquete de red:
     *
     *  1. DEDUPLICACION. Una placa que reporta un sensor estable manda el mismo
     *     valor cientos de veces por segundo. Reenviar todas es puro
     *     desperdicio: el servidor las procesaria y descubriria que nada
     *     cambio. El original no filtraba nada.
     *
     *  2. INTERVALO MINIMO. Un tick de Minecraft dura 50 ms; enviar mas de una
     *     actualizacion por tick no puede producir ningun efecto observable,
     *     solo carga.
     */
    public static void onMessageReceived(String message) {
        SerialDebugHud.addLog("RX: " + message);
        addHistory("RX: " + message);

        long now = System.nanoTime();
        synchronized (ConnectionManager.class) {
            if (message.equals(lastSentMessage) && now - lastSentNanos < MIN_SEND_INTERVAL_NANOS) {
                return;
            }
            if (now - lastSentNanos < MIN_SEND_INTERVAL_NANOS) return;
            lastSentNanos   = now;
            lastSentMessage = message;
        }

        Minecraft client = Minecraft.getInstance();
        if (client.level == null) return;

        // El envio DEBE ocurrir en el hilo del cliente: ClientPlayNetworking
        // no es seguro desde un hilo lector arbitrario.
        client.execute(() -> {
            if (ClientPlayNetworking.canSend(SerialInputPayload.TYPE)) {
                ClientPlayNetworking.send(new SerialInputPayload(message));
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════

    public static void disconnectAll() {
        for (BoardLink link : LINKS) link.disconnect();
        synchronized (ConnectionManager.class) {
            lastSentMessage = null;
            lastSentNanos   = 0L;
        }
    }

    /** Etiqueta corta del transporte activo, para la UI. */
    public static Component describeActive() {
        for (BoardLink link : LINKS) {
            if (link.isConnected()) return link.describe();
        }
        return Component.translatable("gui.serialcraft.status.disconnected");
    }

    // ── Historial ─────────────────────────────────────────────────────────

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
}
