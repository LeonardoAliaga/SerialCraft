package com.serialcraft.signal;

import com.serialcraft.signal.Waveform.Shape;

/**
 * Decide cuando y que valor enviar a la placa. No envia nada por si mismo:
 * devuelve una {@link Action} que la pagina ejecuta.
 *
 * Dos reglas de seguridad, porque esto mueve actuadores reales:
 *
 *  - Ritmo: como maximo un envio por tick (20 por segundo) y solo si el valor
 *    cambio. Es el mismo techo que ya usa la telemetria del juego; mas rapido
 *    solo saturaria el enlace. A 20 por segundo un seno de 2 s tiene 40 puntos
 *    por ciclo, suficientes para que se vea como una onda.
 *
 *  - Parada segura: si la pagina deja de llamar a {@link #tick} (cambiaste de
 *    pestana, el juego se congelo...) el generador se detiene y manda un valor
 *    de reposo, en vez de dejar un servo o un LED en el ultimo valor generado.
 */
public final class GeneratorController {

    /**
     * Separacion minima entre envios. El juego avanza a 20 ticks/s (uno cada 50 ms),
     * asi que en la practica sale un mensaje por tick: 20 por segundo como maximo.
     * Se deja 5 ms por debajo de 50 para que el desfase normal entre ticks no
     * haga saltarse uno.
     */
    public static final long SEND_INTERVAL_NANOS = 45_000_000L;
    public static final long MAX_TICK_GAP_NANOS  = 500_000_000L;
    /** Valor que se deja en la placa al parar. */
    public static final int REST_VALUE = 0;

    public record Action(boolean send, int value, boolean stopped) {
        static final Action NOTHING = new Action(false, 0, false);
    }

    private boolean running = false;
    private long startNanos;
    private long lastTickNanos;
    private long lastSendNanos;
    private int lastSentValue = -1;
    private boolean sentAnything = false;

    public boolean isRunning() { return running; }

    public void start(long nowNanos) {
        running = true;
        startNanos = nowNanos;
        lastTickNanos = nowNanos;
        lastSendNanos = Long.MIN_VALUE / 2;
        lastSentValue = -1;
        sentAnything = false;
    }

    /** Parada pedida por el jugador (o por cerrar la pantalla). */
    public Action stop() {
        boolean needsRest = running && sentAnything;
        running = false;
        sentAnything = false;
        return needsRest ? new Action(true, REST_VALUE, true) : new Action(false, 0, true);
    }

    public Action tick(long nowNanos, Shape shape, double periodSec, int amplitudePct) {
        if (!running) return Action.NOTHING;

        if (nowNanos - lastTickNanos > MAX_TICK_GAP_NANOS) {
            return stop();                                   // parada segura
        }
        lastTickNanos = nowNanos;

        int value = Waveform.value(shape, (nowNanos - startNanos) / 1e9, periodSec, amplitudePct);
        if (nowNanos - lastSendNanos < SEND_INTERVAL_NANOS || value == lastSentValue) {
            return Action.NOTHING;
        }
        lastSendNanos = nowNanos;
        lastSentValue = value;
        sentAnything = true;
        return new Action(true, value, false);
    }
}
