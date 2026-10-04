package com.serialcraft.signal;

import com.serialcraft.signal.Waveform.Shape;

/**
 * Decide cuando y que valor enviar a la placa. No envia nada por si mismo:
 * devuelve una {@link Action} que la pagina ejecuta.
 *
 * Dos reglas de seguridad, porque esto mueve actuadores reales:
 *
 *  - Ritmo: como maximo 10 envios por segundo y solo si el valor cambio. Es el
 *    mismo limite que ya tienen los bloques IO de salida (cada 2 ticks); mas
 *    rapido no puede producir ningun efecto en el juego y solo satura el enlace.
 *
 *  - Parada segura: si la pagina deja de llamar a {@link #tick} (cambiaste de
 *    pestana, el juego se congelo...) el generador se detiene y manda un valor
 *    de reposo, en vez de dejar un servo o un LED en el ultimo valor generado.
 */
public final class GeneratorController {

    public static final long SEND_INTERVAL_NANOS = 100_000_000L;   // 10 Hz
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
