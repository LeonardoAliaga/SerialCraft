package com.serialcraft.signal;

import com.serialcraft.signal.SignalRecorder.Snapshot;

import java.util.function.IntUnaryOperator;

/**
 * Numeros que explican como se comporta una senal.
 *
 * Todo se calcula "manteniendo" cada valor hasta la siguiente muestra: un
 * sensor que solo avisa cuando cambia no puede promediarse como si cada aviso
 * valiera lo mismo (el promedio saldria sesgado hacia los momentos de mucha
 * actividad), asi que el promedio y la desviacion se ponderan por tiempo.
 *
 * @param count         muestras dentro de la ventana
 * @param rateHz        mensajes por segundo en la ventana reciente
 * @param last          ultimo valor
 * @param ageNanos      hace cuanto llego
 * @param min           minimo de la ventana (valor mantenido incluido)
 * @param max           maximo de la ventana
 * @param mean          promedio ponderado por tiempo
 * @param stdDev        desviacion estandar ponderada por tiempo
 * @param variation     maximo - minimo en la ventana RECIENTE; con el sensor
 *                      quieto es el ruido
 * @param changes       veces que el valor cambio en la ventana reciente
 * @param mappedChanges veces que cambio el valor convertido (p. ej. a redstone)
 */
public record SignalStats(int count, double rateHz, float last, long ageNanos,
                          float min, float max, double mean, double stdDev,
                          float variation, int changes, int mappedChanges) {

    public static final long RECENT_NANOS = 3_000_000_000L;

    public static final SignalStats EMPTY =
            new SignalStats(0, 0, 0, Long.MAX_VALUE, 0, 0, 0, 0, 0, 0, 0);

    public boolean hasData() { return count > 0 || ageNanos != Long.MAX_VALUE; }

    /**
     * Zona muerta aconsejada para el sketch: dos lecturas consecutivas de un
     * sensor quieto pueden diferir hasta {@code variation}, asi que solo hay
     * que reaccionar a cambios mayores que eso.
     */
    public int suggestedDeadband() {
        return Math.max(1, (int) Math.ceil(variation));
    }

    /**
     * @param mapper convierte el valor a otra escala para contar sus cambios
     *               (p. ej. cable 0-255 -> redstone 0-15); puede ser null
     */
    public static SignalStats compute(Snapshot s, long from, long to, IntUnaryOperator mapper) {
        if (s == null || s.size() == 0 || to <= from) return EMPTY;

        final long[] t = s.times();
        final float[] v = s.values();
        final int n = s.size();
        final int firstInWindow = s.hasPrior() ? 1 : 0;

        final long lastT = t[n - 1];
        final float last = v[n - 1];

        // ── Ventana completa: minimo, maximo, promedio y desviacion ──────────
        // Cada valor "dura" hasta la siguiente muestra (o hasta el final).
        float min = Float.POSITIVE_INFINITY, max = Float.NEGATIVE_INFINITY;
        double sumW = 0, sumWV = 0;
        for (int i = 0; i < n; i++) {
            long segStart = Math.max(t[i], from);
            long segEnd   = (i + 1 < n) ? Math.min(t[i + 1], to) : to;
            boolean isPrior = i < firstInWindow;
            if (isPrior && segEnd <= segStart) continue;   // el valor previo no llego a verse en la ventana
            if (segEnd < segStart) continue;
            min = Math.min(min, v[i]);
            max = Math.max(max, v[i]);
            double w = (double) (segEnd - segStart);
            sumW += w;
            sumWV += w * v[i];
        }
        if (min == Float.POSITIVE_INFINITY) { min = last; max = last; }
        double mean = sumW > 0 ? sumWV / sumW : last;

        double sumVar = 0;
        if (sumW > 0) {
            for (int i = 0; i < n; i++) {
                long segStart = Math.max(t[i], from);
                long segEnd   = (i + 1 < n) ? Math.min(t[i + 1], to) : to;
                if (segEnd <= segStart) continue;
                double d = v[i] - mean;
                sumVar += (double) (segEnd - segStart) * d * d;
            }
        }
        double std = sumW > 0 ? Math.sqrt(sumVar / sumW) : 0;

        // ── Ventana reciente: variacion, cambios y ritmo ─────────────────────
        long recentFrom = Math.max(from, to - RECENT_NANOS);

        // h = ultima muestra ANTERIOR a la ventana reciente: es el valor que ya
        // estaba mantenido cuando esta empezo.
        int h = -1;
        for (int i = 0; i < n && t[i] < recentFrom; i++) h = i;

        float rMin = Float.POSITIVE_INFINITY, rMax = Float.NEGATIVE_INFINITY;
        int recentCount = 0, changes = 0, mappedChanges = 0;
        float prev = 0;
        boolean havePrev = false;
        for (int i = Math.max(h, 0); i < n; i++) {
            rMin = Math.min(rMin, v[i]);
            rMax = Math.max(rMax, v[i]);
            if (t[i] >= recentFrom) {
                recentCount++;
                if (havePrev) {
                    if (v[i] != prev) changes++;
                    if (mapper != null
                            && mapper.applyAsInt(Math.round(v[i])) != mapper.applyAsInt(Math.round(prev))) {
                        mappedChanges++;
                    }
                }
            }
            prev = v[i];
            havePrev = true;
        }
        float variation = (rMin == Float.POSITIVE_INFINITY) ? 0f : rMax - rMin;

        // Mensajes por segundo: se mide sobre la vida real de la serie dentro de
        // la ventana reciente, con un minimo de 1 s para que un solo mensaje
        // recien llegado no salga como "20 por segundo".
        long firstT = t[Math.min(firstInWindow, n - 1)];
        double lifeSec = (to - Math.max(firstT, recentFrom)) / 1e9;
        double rate = recentCount / Math.max(1.0, Math.min(RECENT_NANOS / 1e9, lifeSec));

        return new SignalStats(n - firstInWindow, rate, last, to - lastT, min, max, mean, std,
                variation, changes, mappedChanges);
    }
}
