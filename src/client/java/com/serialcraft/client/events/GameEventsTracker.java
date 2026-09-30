package com.serialcraft.client.events;

import com.serialcraft.connection.ConnectionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

/**
 * Convierte el estado del juego en lineas "mc_clave:valor" para la placa
 * (protocolo de telemetria, docs/protocol.md seccion 10). Se engancha una sola
 * vez a END_CLIENT_TICK desde SerialCraftClient.
 *
 * Dos categorias de eventos, dos disciplinas distintas:
 *
 *  - PERIODIC (hora, clima, hambre...): se muestrean cada
 *    EventsConfig.intervalTicks y se envian si el valor cambio, o si pasaron
 *    RESYNC_TICKS desde el ultimo envio aunque no haya cambiado.
 *
 *    El reenvio periodico no es un adorno. Un Arduino Uno se REINICIA cuando
 *    el ordenador abre su puerto USB (senal DTR) y el bootloader tarda uno o
 *    dos segundos en ceder el control; la primera instantanea completa, que
 *    se manda nada mas conectar, cae justo en ese hueco y se pierde. Sin
 *    reenvio, un valor estable (hambre 20, clima despejado) no volveria a
 *    enviarse nunca y la placa se quedaria con su valor por defecto. Tambien
 *    cubre cualquier linea perdida por ruido. Por eso la placa debe tratar
 *    estos datos como idempotentes: recibir dos veces el mismo valor no
 *    cambia nada.
 *
 *  - EDGE (dano recibido, muerte): sin intervalo ni deduplicacion, porque cada
 *    ocurrencia es informacion nueva por definicion. Se detectan comparando la
 *    vida del jugador entre dos ticks consecutivos. NUNCA se reenvian: repetir
 *    un "mc_damage:4" haria creer a la placa que el jugador recibio otro golpe.
 *
 * Nada se escribe directamente en el cable. Todo pasa por TelemetryOutbox y
 * se libera como maximo MAX_LINES_PER_TICK lineas por tick, con los sucesos
 * por delante de los estados (vease esa clase para el motivo).
 *
 * Todo el estado se toca solo desde el hilo del cliente (tick y clics de la
 * interfaz), por eso los campos estaticos no estan sincronizados.
 */
public final class GameEventsTracker {

    private GameEventsTracker() {}

    /** Reenvio de estados sin cambios: 100 ticks = 5 s. */
    static final int RESYNC_TICKS = 100;

    /** Lineas que se liberan por tick: 20 lineas/s como techo, con margen para buferes pequenos. */
    static final int MAX_LINES_PER_TICK = 1;

    private static final TelemetryOutbox OUTBOX = new TelemetryOutbox();

    /** Ticks transcurridos CON placa conectada; base del reenvio periodico. */
    private static long clockTicks = 0L;
    private static int  tickCounter = 0;
    private static boolean sampleNow = false;

    private static float lastHealth    = -1f;
    private static float lastMaxHealth = -1f;
    private static boolean wasConnected = false;

    /** Ultimo valor puesto en cola de cada estado, y en que tick. */
    private static final Map<GameEvent, Integer> lastValue = new EnumMap<>(GameEvent.class);
    private static final Map<GameEvent, Long>    lastValueAt = new EnumMap<>(GameEvent.class);
    /** Ultimo valor de cada suceso; solo para mostrarlo en la interfaz. */
    private static final Map<GameEvent, Integer> lastEdgeValue = new EnumMap<>(GameEvent.class);

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        ClientLevel level  = client.level;

        if (player == null || level == null) {
            reset(); // el proximo mundo no debe heredar nada de este
            return;
        }

        EventsConfig cfg = EventsConfig.get();

        // La deteccion de flancos corre siempre, conectados o no: si solo se
        // comprobara con la placa enchufada, conectar el cable justo despues
        // de recibir un golpe generaria un "mc_damage" falso por la
        // diferencia acumulada mientras tanto.
        float previousHealth    = lastHealth;
        float previousMaxHealth = lastMaxHealth;
        float currentHealth     = player.getHealth();
        float currentMaxHealth  = player.getMaxHealth();
        boolean firstSample     = previousHealth < 0f;
        lastHealth    = currentHealth;
        lastMaxHealth = currentMaxHealth;

        boolean connected = ConnectionManager.isAnyConnected();

        if (!connected) {
            if (wasConnected) clearLinkState(); // lo pendiente pertenecia a la conexion anterior
            wasConnected = false;
            return;
        }

        // Al conectar, muestrear de inmediato en vez de esperar al siguiente
        // intervalo, y olvidar lo enviado: la placa nueva no sabe nada todavia.
        if (!wasConnected) {
            clearLinkState();
            sampleNow = true;
        }
        wasConnected = true;
        clockTicks++;

        if (!firstSample) {
            detectEdges(cfg, previousHealth, previousMaxHealth, currentHealth, currentMaxHealth);
        }

        tickCounter++;
        if (sampleNow || tickCounter >= cfg.intervalTicks) {
            sampleNow   = false;
            tickCounter = 0;
            samplePeriodic(cfg, player, level);
        }

        drain();
    }

    // ══════════════════════════════════════════════════════════════════════

    private static void detectEdges(EventsConfig cfg, float previousHealth,
                                    float previousMaxHealth, float currentHealth,
                                    float currentMaxHealth) {
        // Si la vida maxima BAJO (p. ej. termina el efecto Health Boost), el
        // juego recorta la vida actual al nuevo maximo. Esa diferencia no es
        // dano: la vida que "se pierde" por el recorte se descuenta del punto
        // de partida. Un golpe real del mismo tick se sigue contando.
        float expected = previousHealth;
        if (currentMaxHealth < previousMaxHealth) {
            expected = Math.min(previousHealth, currentMaxHealth);
        }

        if (currentHealth < expected && cfg.isEnabled(GameEvent.DAMAGE_TAKEN)) {
            int amount = Math.max(1, Math.round(expected - currentHealth));
            offerEdge(GameEvent.DAMAGE_TAKEN, amount);
        }
        if (previousHealth > 0f && currentHealth <= 0f && cfg.isEnabled(GameEvent.DEATH)) {
            offerEdge(GameEvent.DEATH, 1);
        }
    }

    private static void samplePeriodic(EventsConfig cfg, LocalPlayer player, ClientLevel level) {
        for (GameEvent event : GameEvent.values()) {
            if (!event.isPeriodic() || !cfg.isEnabled(event)) continue;

            int value = event.sample(player, level);
            Integer previous = lastValue.get(event);
            Long    sentAt   = lastValueAt.get(event);

            boolean changed = previous == null || previous.intValue() != value;
            boolean stale   = sentAt == null || clockTicks - sentAt >= RESYNC_TICKS;
            if (!changed && !stale) continue;

            lastValue.put(event, value);
            lastValueAt.put(event, clockTicks);
            // Un reenvio de un valor sin cambios se manda "en silencio": llega a
            // la placa, pero no llena la consola con lineas repetidas.
            OUTBOX.offerState(event.wireKey(), value, !changed);
        }
    }

    private static void offerEdge(GameEvent event, int rawValue) {
        int value = event.clamp(rawValue);
        lastEdgeValue.put(event, value);
        OUTBOX.offerEdge(event.wireKey(), value);
    }

    private static void drain() {
        for (int i = 0; i < MAX_LINES_PER_TICK; i++) {
            TelemetryOutbox.Line line = OUTBOX.poll();
            if (line == null) return;
            ConnectionManager.sendTelemetry(line.text(), line.quiet());
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  API para la interfaz y el ciclo de vida
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Se llama al activar o desactivar un evento. Olvida lo enviado y lo
     * pendiente de ese canal, y fuerza un muestreo inmediato: sin eso, al
     * activar una casilla con el intervalo en 10 s la placa esperaria hasta 10 s.
     */
    public static void invalidate(GameEvent event) {
        lastValue.remove(event);
        lastValueAt.remove(event);
        lastEdgeValue.remove(event);
        OUTBOX.discard(event.wireKey());
        sampleNow = true;
    }

    /** Ultimo valor enviado (o a punto de enviarse) de un evento, o null si aun no hay ninguno. */
    public static @Nullable Integer lastValue(GameEvent event) {
        return event.isPeriodic() ? lastValue.get(event) : lastEdgeValue.get(event);
    }

    /** Limpia todo al salir del mundo, para que el siguiente empiece de cero. */
    public static void reset() {
        clearLinkState();
        lastHealth    = -1f;
        lastMaxHealth = -1f;
        wasConnected  = false;
        clockTicks    = 0L;
        tickCounter   = 0;
        sampleNow     = false;
    }

    /** Olvida cola y valores enviados: lo que se envio a una conexion no vale para la siguiente. */
    private static void clearLinkState() {
        OUTBOX.clear();
        lastValue.clear();
        lastValueAt.clear();
        lastEdgeValue.clear();
    }
}
