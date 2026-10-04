package com.serialcraft.signal;

import com.serialcraft.signal.SignalRecorder.Snapshot;

/**
 * Convierte una serie en una banda [min, max] por cada columna de pixeles,
 * para dibujarla SIN inventar valores.
 *
 * Una senal que solo cambia cuando llega un mensaje se mantiene (escalon) hasta
 * el siguiente: entre dos muestras no hay nada, y suavizar con una curva
 * dibujaba valores que nunca existieron (un 0 -> 255 salia como rampa).
 *
 * Para cada columna se toma el valor que ya estaba mantenido al empezar la
 * columna y todas las muestras que caen dentro. Un salto aparece como una
 * barra vertical y un pico de un solo mensaje no se pierde aunque la columna
 * represente mucho tiempo.
 */
public final class StepEnvelope {

    public final int columns;
    /** false mientras todavia no habia llegado ninguna muestra. */
    public final boolean[] known;
    public final float[] lo;
    public final float[] hi;

    private StepEnvelope(int columns) {
        this.columns = columns;
        this.known = new boolean[columns];
        this.lo = new float[columns];
        this.hi = new float[columns];
    }

    public static StepEnvelope compute(Snapshot s, long from, long to, int columns) {
        StepEnvelope env = new StepEnvelope(Math.max(columns, 0));
        if (s == null || s.size() == 0 || columns <= 0 || to <= from) return env;

        long[] t = s.times();
        float[] v = s.values();
        int n = s.size();
        double span = (double) (to - from);

        int next = 0;                 // primera muestra aun no "consumida" como valor mantenido
        float hold = 0f;
        boolean have = false;

        for (int c = 0; c < columns; c++) {
            long t0 = from + (long) (span * c / columns);
            long t1 = (c == columns - 1) ? to : from + (long) (span * (c + 1) / columns);

            while (next < n && t[next] <= t0) { hold = v[next]; have = true; next++; }

            float lo = hold, hi = hold;
            boolean known = have;
            for (int j = next; j < n && t[j] <= t1; j++) {
                if (!known) { lo = hi = v[j]; known = true; }
                else { lo = Math.min(lo, v[j]); hi = Math.max(hi, v[j]); }
            }
            env.known[c] = known;
            env.lo[c] = lo;
            env.hi[c] = hi;
        }
        return env;
    }
}
