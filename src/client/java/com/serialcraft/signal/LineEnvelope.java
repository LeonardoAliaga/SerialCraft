package com.serialcraft.signal;

import com.serialcraft.signal.SignalRecorder.Snapshot;

/**
 * Como {@link StepEnvelope}, pero UNIENDO las muestras con rectas.
 *
 * Una senal continua (un seno que genera la Laptop, un potenciometro girando)
 * muestreada a 20 Hz se ve como una escalera si se mantiene cada valor hasta
 * el siguiente mensaje. Unir los puntos reales con segmentos rectos la dibuja
 * como la onda que es, sin inventar nada: la recta nunca sale del rango de las
 * dos muestras que une (a diferencia de una curva spline, que se pasa y dibuja
 * valores que nunca existieron).
 *
 * Una excepcion: si entre dos mensajes pasa mas de
 * {@link #MAX_INTERPOLATION_GAP_NANOS}, NO se unen. Un sensor que solo avisa
 * al cambiar y estuvo quieto 8 segundos no se movio gradualmente en ese tiempo;
 * una diagonal diria lo contrario. En ese caso el valor se mantiene hasta el
 * mensaje siguiente, igual que en el modo escalon.
 */
public final class LineEnvelope {

    private LineEnvelope() {}

    public static final long MAX_INTERPOLATION_GAP_NANOS = 300_000_000L;

    /** Valor de la senal en el instante t, o NaN si todavia no habia ninguna muestra. */
    static float valueAt(long[] t, float[] v, int n, long time) {
        // ultima muestra con tiempo <= time
        int lo = 0, hi = n;
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (t[mid] <= time) lo = mid + 1; else hi = mid;
        }
        int i = lo - 1;
        if (i < 0) return Float.NaN;
        if (i == n - 1) return v[i];                       // despues de la ultima: se mantiene
        long gap = t[i + 1] - t[i];
        if (gap > MAX_INTERPOLATION_GAP_NANOS || gap <= 0) return v[i];
        return v[i] + (v[i + 1] - v[i]) * ((float) (time - t[i]) / gap);
    }

    public static StepEnvelope compute(Snapshot s, long from, long to, int columns) {
        StepEnvelope env = StepEnvelope.blank(columns);
        if (s == null || s.size() == 0 || columns <= 0 || to <= from) return env;

        long[] t = s.times();
        float[] v = s.values();
        int n = s.size();
        double span = (double) (to - from);

        int next = 0;                                       // primera muestra con tiempo > t0
        for (int c = 0; c < columns; c++) {
            long t0 = from + (long) (span * c / columns);
            long t1 = (c == columns - 1) ? to : from + (long) (span * (c + 1) / columns);

            while (next < n && t[next] <= t0) next++;

            float lo = Float.POSITIVE_INFINITY, hi = Float.NEGATIVE_INFINITY;
            float a = valueAt(t, v, n, t0);
            if (!Float.isNaN(a)) { lo = Math.min(lo, a); hi = Math.max(hi, a); }
            float b = valueAt(t, v, n, t1);
            if (!Float.isNaN(b)) { lo = Math.min(lo, b); hi = Math.max(hi, b); }
            // En una recta los extremos estan en los bordes o en las muestras del interior
            for (int j = next; j < n && t[j] <= t1; j++) { lo = Math.min(lo, v[j]); hi = Math.max(hi, v[j]); }

            if (lo <= hi) { env.known[c] = true; env.lo[c] = lo; env.hi[c] = hi; }
        }
        return env;
    }
}
