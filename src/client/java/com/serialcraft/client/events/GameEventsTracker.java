package com.serialcraft.client.events;

import com.serialcraft.connection.ConnectionManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

import java.util.EnumMap;
import java.util.Map;

/**
 * Convierte el estado del juego en lineas "CLAVE:VALOR" para la placa,
 * siguiendo el mismo protocolo de texto que ya usan los Bloques IO
 * (docs/protocol.md). Se engancha una sola vez a END_CLIENT_TICK desde
 * SerialCraftClient.
 *
 * Dos categorias de eventos, dos disciplinas distintas:
 *
 *  - PERIODIC (hora, clima, hambre...): se muestrean cada
 *    EventsConfig.intervalTicks y solo se envian si el valor cambio desde la
 *    ultima vez. Mismo principio que ArduinoIOBlockEntity.pushOutput(): un
 *    valor estable no debe generar trafico.
 *
 *  - EDGE (dano recibido, muerte): no tienen intervalo ni deduplicacion,
 *    porque cada ocurrencia es informacion nueva por definicion. Se detectan
 *    comparando la vida del jugador entre dos ticks consecutivos.
 *
 * ConnectionManager.sendMessageToBoard() no aplica ningun limite de tasa por
 * si solo (ese limite vive en el lado de ENTRADA, en onMessageReceived). Si
 * este tracker no dedupicara, un jugador con salud regenerando de fruta al
 * maximo enviaria "mc_health:20" veinte veces por segundo indefinidamente.
 */
public final class GameEventsTracker {

    private GameEventsTracker() {}

    private static int tickCounter = 0;
    private static float lastHealth = -1f;
    private static boolean wasConnected = false;

    private static final Map<GameEvent, Integer> lastSent = new EnumMap<>(GameEvent.class);

    public static void tick(Minecraft client) {
        LocalPlayer player = client.player;
        ClientLevel level  = client.level;

        if (player == null || level == null) {
            lastHealth = -1f; // el proximo mundo no debe heredar la vida de este
            return;
        }

        // La deteccion de flancos corre siempre, conectados o no: si solo se
        // comprobara con la placa enchufada, conectar el cable justo despues
        // de recibir un golpe generaria un "mc_damage" falso por la
        // diferencia acumulada mientras tanto.
        float previousHealth = lastHealth;
        float currentHealth  = player.getHealth();
        boolean firstSample  = previousHealth < 0f;
        lastHealth = currentHealth;

        boolean connected = ConnectionManager.isAnyConnected();

        // Al reconectar, reenviar el estado actual de inmediato en vez de
        // esperar al siguiente intervalo, y olvidar los ultimos valores
        // enviados: la placa que se acaba de conectar no sabe nada todavia.
        if (connected && !wasConnected) {
            lastSent.clear();
            tickCounter = EventsConfig.get().intervalTicks;
        }
        wasConnected = connected;
        if (!connected) return;

        EventsConfig cfg = EventsConfig.get();

        if (!firstSample) {
            if (currentHealth < previousHealth && cfg.isEnabled(GameEvent.DAMAGE_TAKEN)) {
                int amount = Math.clamp(Math.round(previousHealth - currentHealth), 1, 255);
                send(GameEvent.DAMAGE_TAKEN, amount);
            }
            if (previousHealth > 0f && currentHealth <= 0f && cfg.isEnabled(GameEvent.DEATH)) {
                send(GameEvent.DEATH, 1);
            }
        }

        tickCounter++;
        if (tickCounter < cfg.intervalTicks) return;
        tickCounter = 0;

        for (GameEvent event : GameEvent.values()) {
            if (!event.isPeriodic() || !cfg.isEnabled(event)) continue;

            int value = Math.clamp(event.sample(player, level), 0, 255);
            Integer previous = lastSent.get(event);
            if (previous != null && previous == value) continue;

            lastSent.put(event, value);
            send(event, value);
        }
    }

    /** Limpia el estado al salir del mundo, para que el siguiente empiece de cero. */
    public static void reset() {
        tickCounter   = 0;
        lastHealth    = -1f;
        wasConnected  = false;
        lastSent.clear();
    }

    private static void send(GameEvent event, int value) {
        ConnectionManager.sendMessageToBoard(event.wireKey() + ":" + value);
    }
}
