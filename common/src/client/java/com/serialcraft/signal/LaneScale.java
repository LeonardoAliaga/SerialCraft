package com.serialcraft.signal;

import java.util.Locale;

/**
 * Escala vertical de una pista.
 *
 * Los canales de telemetria del juego (mc_*) tienen un rango conocido y se
 * dibujan con el. El resto usa la escala del cable del mod (0..255), que se
 * amplia solo si algun valor la supera (un ADC de 10 bits, un sensor de
 * temperatura...). Fijar la escala, en vez de ajustarla a cada instante,
 * mantiene comparables las pistas entre si y evita que el ruido de un sensor
 * quieto parezca una gran senal.
 */
public final class LaneScale {

    private LaneScale() {}

    public record Range(float lo, float hi) {
        public float span() { return hi - lo; }
    }

    public static Range of(String key, float observedMin, float observedMax) {
        String k = key == null ? "" : key.toLowerCase(Locale.ROOT);
        switch (k) {
            case "mc_time":       return new Range(0, 23999);
            case "mc_isday":
            case "mc_fire":
            case "mc_death":      return new Range(0, 1);
            case "mc_weather":    return new Range(0, 2);
            case "mc_hunger":
            case "mc_saturation": return new Range(0, 20);
            case "mc_air":        return new Range(0, 100);
            case "mc_health":     return new Range(0, Math.max(20f, observedMax));
            case "mc_damage":     return new Range(0, Math.max(20f, observedMax));
            case "mc_level":      return new Range(0, Math.max(30f, observedMax));
            default:              break;
        }

        float lo = Math.min(0f, observedMin);
        float top = observedMax;
        float hi;
        if (top <= 255f)        hi = 255f;
        else if (top <= 1023f)  hi = 1023f;
        else if (top <= 4095f)  hi = 4095f;
        else if (top <= 65535f) hi = 65535f;
        else                    hi = top;
        if (hi - lo < 1e-3f) hi = lo + 1f;
        return new Range(lo, hi);
    }

    /**
     * Canales que son SUCESOS puntuales y no un estado: llegan una sola vez y
     * no se repiten. Dibujarlos como valor "mantenido" diria que el dano sigue
     * siendo 6 durante los segundos siguientes, y no es cierto: se dibujan como
     * impulsos en el instante en que ocurrieron.
     */
    public static boolean isEvent(String key) {
        if (key == null) return false;
        String k = key.toLowerCase(Locale.ROOT);
        return k.equals("mc_damage") || k.equals("mc_death");
    }

    /** Valor -> posicion 0..1 dentro de la escala (se recorta). */
    public static float normalize(Range r, float value) {
        if (r.span() <= 0) return 0.5f;
        return Math.clamp((value - r.lo()) / r.span(), 0f, 1f);
    }

    /** Texto corto para etiquetas: sin decimales si es entero. */
    public static String format(float v) {
        if (Float.isNaN(v) || Float.isInfinite(v)) return "0";
        if (v == (long) v) return String.format(Locale.ROOT, "%d", (long) v);
        return String.format(Locale.ROOT, "%.1f", v);
    }
}
