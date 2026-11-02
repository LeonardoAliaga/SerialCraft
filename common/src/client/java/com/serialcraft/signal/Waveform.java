package com.serialcraft.signal;

/**
 * Formas de onda del generador. Devuelven siempre un entero 0..255, que es la
 * escala del cable del mod (PWM / analogWrite).
 */
public final class Waveform {

    private Waveform() {}

    public enum Shape {
        /** Sube de 0 a 255 y vuelve a 0 de golpe. */
        RAMP,
        /** Sube y baja suavemente. */
        TRIANGLE,
        /** 0 la mitad del periodo y 255 la otra mitad. */
        SQUARE,
        /** Senoidal, empieza en 0. */
        SINE,
        /** 16 escalones (0, 17, 34... 255): uno por cada nivel de redstone. */
        STAIRS;

        public Shape next() { return values()[(ordinal() + 1) % values().length]; }
    }

    /**
     * @param tSec          segundos desde que arranco el generador
     * @param periodSec     duracion de un ciclo completo (&gt; 0)
     * @param amplitudePct  porcentaje de 0..255 que se usa (1..100)
     */
    public static int value(Shape shape, double tSec, double periodSec, int amplitudePct) {
        double period = periodSec > 0 ? periodSec : 1.0;
        double phase = (tSec / period) % 1.0;
        if (phase < 0) phase += 1.0;

        double unit = switch (shape) {
            case RAMP     -> phase;
            case TRIANGLE -> 1.0 - Math.abs(2.0 * phase - 1.0);
            case SQUARE   -> phase < 0.5 ? 0.0 : 1.0;
            case SINE     -> (Math.sin(2.0 * Math.PI * phase - Math.PI / 2.0) + 1.0) / 2.0;
            case STAIRS   -> Math.floor(phase * 16.0) / 15.0;
        };

        int amp = Math.clamp(amplitudePct, 1, 100);
        return Math.clamp(Math.round(unit * 255.0 * amp / 100.0), 0, 255);
    }
}
