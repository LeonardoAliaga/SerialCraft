package com.serialcraft.signal;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Registro de todo lo que viaja entre el juego y la placa, CON LA HORA REAL
 * en que ocurrio cada cosa.
 *
 * Por que existe: la pagina Visualizar antiguo no guardaba nada; cada tick
 * (20 Hz) leia la ultima linea de texto de la consola. Eso no distingue "el
 * valor no cambio" de "no llegan datos", pierde mensajes si llegan mas de 16
 * entre tick y tick, y no sabe cuando llego cada uno.
 *
 * Cada pareja (direccion, clave) es una SERIE con su propio buffer circular:
 *   RX = placa -> juego (sensores)
 *   TX = juego -> placa (bloques IO de salida, telemetria mc_*, generador)
 *
 * Sin dependencias de Minecraft: se puede probar sola. Es seguro usarla desde
 * varios hilos (los lectores USB/Wi-Fi escriben, el hilo del cliente lee).
 */
public final class SignalRecorder {

    public enum Direction { RX, TX }

    /** Identifica una serie. */
    public record SeriesId(Direction dir, String key) {}

    /**
     * Copia de una ventana de tiempo de una serie. Si {@code hasPrior}, la
     * primera muestra es ANTERIOR al inicio de la ventana: es el valor que ya
     * estaba "mantenido" cuando la ventana empezo.
     */
    public record Snapshot(SeriesId id, long[] times, float[] values, boolean hasPrior) {
        public int size() { return times.length; }
    }

    /** Muestras que se guardan por serie (a 20 Hz son ~3,4 minutos). */
    public static final int SERIES_CAPACITY = 4096;
    /** Series simultaneas; al pasarse se descarta la que lleva mas tiempo callada. */
    public static final int MAX_SERIES = 16;
    public static final int MAX_KEY_LENGTH = 32;

    private static final Pattern KEY_OK   = Pattern.compile("[A-Za-z0-9_.\\-]{1," + MAX_KEY_LENGTH + "}");
    private static final Pattern VALUE_OK = Pattern.compile("[-+]?\\d{1,9}(?:\\.\\d{1,6})?");

    /** Instancia compartida por el mod. */
    public static final SignalRecorder INSTANCE = new SignalRecorder();

    private static final class Series {
        final SeriesId id;
        final long[] times  = new long[SERIES_CAPACITY];
        final float[] values = new float[SERIES_CAPACITY];
        int head = 0;
        int size = 0;
        long lastNanos = Long.MIN_VALUE;

        Series(SeriesId id) { this.id = id; }

        void add(long nanos, float value) {
            times[head]  = nanos;
            values[head] = value;
            head = (head + 1) % SERIES_CAPACITY;
            if (size < SERIES_CAPACITY) size++;
            lastNanos = nanos;
        }

        /** Posicion fisica de la muestra numero i (0 = la mas antigua). */
        int physical(int i) { return (head - size + i + SERIES_CAPACITY * 2) % SERIES_CAPACITY; }

        long timeAt(int i) { return times[physical(i)]; }
    }

    private final Map<SeriesId, Series> series = new HashMap<>();

    // ── Escritura ──────────────────────────────────────────────────────────

    public static boolean isValidKey(String key) {
        return key != null && KEY_OK.matcher(key).matches();
    }

    /**
     * Anota una linea "clave:valor". Lo que no tenga esa forma (banners de
     * arranque, texto libre...) se ignora sin ruido.
     *
     * @return true si se guardo
     */
    public boolean record(Direction dir, String line, long nowNanos) {
        if (line == null) return false;
        int colon = line.indexOf(':');
        if (colon <= 0 || colon >= line.length() - 1) return false;

        String key = line.substring(0, colon).trim();
        String raw = line.substring(colon + 1).trim();
        if (!isValidKey(key) || !VALUE_OK.matcher(raw).matches()) return false;

        float value;
        try { value = Float.parseFloat(raw); }
        catch (NumberFormatException e) { return false; }
        if (Float.isNaN(value) || Float.isInfinite(value)) return false;

        SeriesId id = new SeriesId(dir, key);
        synchronized (this) {
            Series s = series.get(id);
            if (s == null) {
                if (series.size() >= MAX_SERIES) evictOldest();
                s = new Series(id);
                series.put(id, s);
            }
            // Dos hilos pueden tomar "ahora" con milisegundos de diferencia: el
            // tiempo de una serie no puede retroceder (la busqueda binaria lo exige).
            s.add(Math.max(nowNanos, s.lastNanos == Long.MIN_VALUE ? nowNanos : s.lastNanos), value);
        }
        return true;
    }

    private void evictOldest() {
        series.values().stream()
                .min(Comparator.comparingLong(s -> s.lastNanos))
                .ifPresent(s -> series.remove(s.id));
    }

    public synchronized void clear() { series.clear(); }

    // ── Lectura ────────────────────────────────────────────────────────────

    public synchronized int seriesCount() { return series.size(); }

    /** Series con alguna muestra en los ultimos {@code maxAgeNanos}, las mas recientes primero. */
    public synchronized List<SeriesId> activeSeries(long nowNanos, long maxAgeNanos) {
        List<Series> list = new ArrayList<>();
        for (Series s : series.values()) {
            if (s.size > 0 && nowNanos - s.lastNanos <= maxAgeNanos) list.add(s);
        }
        list.sort(Comparator.comparingLong((Series s) -> s.lastNanos).reversed());
        List<SeriesId> ids = new ArrayList<>(list.size());
        for (Series s : list) ids.add(s.id);
        return ids;
    }

    public synchronized boolean has(SeriesId id) { return series.containsKey(id); }

    /** Ultimo valor de una serie, o null si no hay. */
    public synchronized Float lastValue(SeriesId id) {
        Series s = series.get(id);
        if (s == null || s.size == 0) return null;
        return s.values[s.physical(s.size - 1)];
    }

    public synchronized long lastNanos(SeriesId id) {
        Series s = series.get(id);
        return (s == null || s.size == 0) ? Long.MIN_VALUE : s.lastNanos;
    }

    /**
     * Copia las muestras de [from, to] mas la ultima anterior a {@code from}.
     * Devuelve null si la serie no existe.
     */
    public synchronized Snapshot snapshot(SeriesId id, long from, long to) {
        Series s = series.get(id);
        if (s == null || s.size == 0) return null;

        // Busqueda binaria: primera muestra con tiempo >= from.
        int lo = 0, hi = s.size;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (s.timeAt(mid) < from) lo = mid + 1; else hi = mid;
        }
        int first = lo;
        boolean prior = first > 0;
        int start = prior ? first - 1 : first;

        int end = first;                       // exclusivo: muestras con tiempo <= to
        while (end < s.size && s.timeAt(end) <= to) end++;

        int n = end - start;
        long[] t = new long[Math.max(n, 0)];
        float[] v = new float[Math.max(n, 0)];
        for (int i = 0; i < n; i++) {
            int p = s.physical(start + i);
            t[i] = s.times[p];
            v[i] = s.values[p];
        }
        return new Snapshot(id, t, v, prior);
    }
}
