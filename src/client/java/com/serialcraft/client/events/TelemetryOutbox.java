package com.serialcraft.client.events;

import com.serialcraft.network.TelemetryProtocol;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cola de salida de la telemetria. Sin dependencias de Minecraft, para poder
 * probarla sola.
 *
 * Existe porque hay hardware con buferes de recepcion diminutos: el
 * HardwareSerial de un Arduino Uno guarda 64 bytes. Once canales de golpe (al
 * conectar, o en cada reenvio periodico) son ~170 bytes en unos 15 ms; si el
 * sketch esta dentro de un delay(), los bytes que no caben se pierden y la
 * linea llega cortada, como "mc_hunge". El tracker saca de aqui, como maximo,
 * MAX_LINES_PER_TICK lineas por tick.
 *
 * Dos clases de contenido, con politicas distintas:
 *
 *  - ESTADOS (hora, salud...): se COALESCEN por clave. Si "mc_time" cambia
 *    tres veces antes de poder enviarse, solo importa el ultimo valor. Una
 *    clave que se reactualiza conserva su turno original, asi un canal muy
 *    activo no deja sin turno a los demas.
 *
 *  - SUCESOS (dano, muerte): cada uno es informacion nueva, no se coalescen y
 *    salen SIEMPRE antes que los estados. La cola esta acotada: si se llena,
 *    se descarta el mas antiguo y se cuenta en droppedEdges().
 */
public final class TelemetryOutbox {

    public static final int MAX_PENDING_EDGES = 16;

    /** Linea lista para enviar. {@code quiet} = no registrarla en la consola. */
    public record Line(String text, boolean quiet) {}

    private record Entry(String key, int value, boolean quiet) {
        Line toLine() { return new Line(TelemetryProtocol.format(key, value), quiet); }
    }

    private final Deque<Entry> edges = new ArrayDeque<>();
    private final Map<String, Entry> states = new LinkedHashMap<>();
    private int droppedEdges = 0;

    /**
     * Encola el estado de un canal, sustituyendo el que hubiera pendiente.
     *
     * @param quiet true para un reenvio periodico de un valor que no cambio:
     *              se envia pero no se registra, o la consola se llenaria de
     *              lineas repetidas. Si ya habia un cambio real pendiente para
     *              la misma clave, este se mantiene visible en el registro.
     */
    public void offerState(String key, int value, boolean quiet) {
        Entry previous = states.get(key);
        boolean effectiveQuiet = quiet && (previous == null || previous.quiet());
        states.put(key, new Entry(key, value, effectiveQuiet));
    }

    /** Encola un suceso puntual. Siempre visible en el registro. */
    public void offerEdge(String key, int value) {
        if (edges.size() >= MAX_PENDING_EDGES) {
            edges.removeFirst();
            droppedEdges++;
        }
        edges.addLast(new Entry(key, value, false));
    }

    /** @return la siguiente linea a enviar (sucesos primero), o null si no hay nada. */
    public @Nullable Line poll() {
        Entry edge = edges.pollFirst();
        if (edge != null) return edge.toLine();

        Iterator<Entry> it = states.values().iterator();
        if (!it.hasNext()) return null;
        Entry next = it.next();
        it.remove();
        return next.toLine();
    }

    /** Descarta lo pendiente de un canal, p. ej. porque el jugador lo desactivo. */
    public void discard(String key) {
        states.remove(key);
        edges.removeIf(e -> e.key().equals(key));
    }

    public void clear() {
        states.clear();
        edges.clear();
    }

    public int size()         { return states.size() + edges.size(); }
    public boolean isEmpty()  { return size() == 0; }
    public int droppedEdges() { return droppedEdges; }
}
