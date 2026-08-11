package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.block.entity.ConnectorBlockEntity;
import com.serialcraft.board.BoardRegistry;
import com.serialcraft.network.guard.NetGuard;
import com.serialcraft.network.guard.PacketRateLimiter;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Registro y manejo de todos los paquetes del mod.
 *
 * Cada receptor sigue exactamente el mismo esqueleto, y es deliberado:
 *
 *      limitar tasa  ->  server.execute  ->  NetGuard.resolve  ->  permisos
 *      ->  aplicar
 *
 * En el codigo original cada handler improvisaba su propia (o ninguna)
 * validacion, y por eso las tres primeras comprobaciones faltaban en unos u
 * otros. Manteniendo el esqueleto identico, anadir un paquete nuevo no puede
 * "olvidar" una defensa por accidente.
 */
public final class ModNetworking {

    private ModNetworking() {}

    // ── Limitadores ───────────────────────────────────────────────────────
    //
    // Las cifras salen del uso real, no de un numero redondo:
    //  - SERIAL: una placa util no cambia de estado mas de ~20 veces/s (1 por
    //    tick). 40/s sostenido con rafaga de 80 deja margen de sobra para
    //    sensores rapidos y sigue siendo dos ordenes de magnitud menos que lo
    //    que puede escupir un loop() sin delay.
    //  - CONFIG/TOGGLE: son acciones de UI. Nadie pulsa 10 veces por segundo.
    //  - LIST: la UI la pide al abrir el panel y tras cada cambio.

    private static final PacketRateLimiter SERIAL_LIMITER = new PacketRateLimiter(80, 40);
    private static final PacketRateLimiter CONFIG_LIMITER = new PacketRateLimiter(20, 10);
    private static final PacketRateLimiter LIST_LIMITER   = new PacketRateLimiter(10, 5);

    // Rango valido de baudios. Fuera de esta lista el ajuste se ignora.
    private static final int[] VALID_BAUD_RATES =
            { 300, 1200, 2400, 4800, 9600, 19200, 38400, 57600, 74880, 115200, 230400, 250000 };

    private static final int MIN_SPEED_MODE = 0;
    private static final int MAX_SPEED_MODE = 2;

    // ══════════════════════════════════════════════════════════════════════
    //  REGISTRO
    // ══════════════════════════════════════════════════════════════════════

    public static void registerPayloads() {
        var c2s = PayloadTypeRegistry.serverboundPlay();
        c2s.register(ConfigPayload.TYPE,           ConfigPayload.CODEC);
        c2s.register(ConnectorPayload.TYPE,        ConnectorPayload.CODEC);
        c2s.register(ConnectorConfigPayload.TYPE,  ConnectorConfigPayload.CODEC);
        c2s.register(SerialInputPayload.TYPE,      SerialInputPayload.CODEC);
        c2s.register(BoardListRequestPayload.TYPE, BoardListRequestPayload.CODEC);
        c2s.register(RemoteTogglePayload.TYPE,     RemoteTogglePayload.CODEC);

        var s2c = PayloadTypeRegistry.clientboundPlay();
        s2c.register(SerialOutputPayload.TYPE,      SerialOutputPayload.CODEC);
        s2c.register(BoardListResponsePayload.TYPE, BoardListResponsePayload.CODEC);
    }

    public static void registerServerHandlers() {
        // Liberar el estado del limitador al desconectar, o los buckets crecen
        // sin fin en un servidor con rotacion alta de jugadores.
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            var id = handler.getPlayer().getUUID();
            SERIAL_LIMITER.forget(id);
            CONFIG_LIMITER.forget(id);
            LIST_LIMITER.forget(id);
        });

        registerConfig();
        registerRemoteToggle();
        registerSerialInput();
        registerBoardList();
        registerConnector();
        registerConnectorConfig();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RECEPTORES
    // ══════════════════════════════════════════════════════════════════════

    private static void registerConfig() {
        ServerPlayNetworking.registerGlobalReceiver(ConfigPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!CONFIG_LIMITER.tryAcquire(player)) return;

            context.server().execute(() -> {
                ArduinoIOBlockEntity io =
                        NetGuard.resolve(player, payload.pos(), ArduinoIOBlockEntity.class,
                                NetGuard.MAX_MANAGEMENT_DISTANCE);
                if (io == null) { NetGuard.logRejected("ConfigPayload", player); return; }

                if (!NetGuard.canOperate(player, io.getOwnerUUID())) {
                    NetGuard.denyOwnership(player);
                    return;
                }
                // Placa sin dueno: se reclama en vez de quedar publica.
                if (io.getOwnerUUID() == null) io.claim(player);

                io.applyConfig(
                        payload.mode(),
                        NetGuard.sanitize(payload.targetData(), BoardInfo.MAX_DATA_LENGTH,
                                ArduinoIOBlockEntity.DEFAULT_TARGET_DATA),
                        payload.signalType(),
                        payload.enabled(),
                        NetGuard.sanitize(payload.boardId(), BoardInfo.MAX_ID_LENGTH,
                                ArduinoIOBlockEntity.DEFAULT_BOARD_ID),
                        payload.logicMode()
                );
            });
        });
    }

    private static void registerRemoteToggle() {
        ServerPlayNetworking.registerGlobalReceiver(RemoteTogglePayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!CONFIG_LIMITER.tryAcquire(player)) return;

            context.server().execute(() -> {
                ArduinoIOBlockEntity io =
                        NetGuard.resolve(player, payload.targetPos(), ArduinoIOBlockEntity.class,
                                NetGuard.MAX_MANAGEMENT_DISTANCE);
                if (io == null) { NetGuard.logRejected("RemoteToggle", player); return; }

                // ESTA comprobacion faltaba por completo en el original.
                if (!NetGuard.canOperate(player, io.getOwnerUUID())) {
                    NetGuard.denyOwnership(player);
                    return;
                }
                io.setEnabled(!io.isEnabled());
            });
        });
    }

    private static void registerSerialInput() {
        ServerPlayNetworking.registerGlobalReceiver(SerialInputPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();

            // Limitar ANTES de encolar: si no, el propio server.execute() se
            // convierte en la cola sin fondo que queriamos evitar.
            if (!SERIAL_LIMITER.tryAcquire(player)) return;

            String message = payload.message();
            if (message.isBlank()) return;

            context.server().execute(() -> {
                // BoardRegistry ya devuelve solo las placas del jugador en SU
                // dimension actual. El original recorria todas las del servidor.
                for (ArduinoIOBlockEntity io : BoardRegistry.boardsOf(player)) {
                    io.acceptSerialInput(message);
                }
            });
        });
    }

    private static void registerBoardList() {
        ServerPlayNetworking.registerGlobalReceiver(BoardListRequestPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!LIST_LIMITER.tryAcquire(player)) return;

            context.server().execute(() -> {
                List<ArduinoIOBlockEntity> boards = BoardRegistry.boardsOf(player);
                List<BoardInfo> infos = new ArrayList<>(
                        Math.min(boards.size(), BoardListResponsePayload.MAX_BOARDS));

                for (ArduinoIOBlockEntity io : boards) {
                    if (infos.size() >= BoardListResponsePayload.MAX_BOARDS) break;
                    infos.add(io.toBoardInfo());
                }
                ServerPlayNetworking.send(player, new BoardListResponsePayload(infos));
            });
        });
    }

    private static void registerConnector() {
        ServerPlayNetworking.registerGlobalReceiver(ConnectorPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!CONFIG_LIMITER.tryAcquire(player)) return;

            context.server().execute(() -> {
                // El original hacia level.getBlockState(pos) sin comprobar que
                // el chunk estuviera cargado: eso generaba terreno bajo demanda
                // con coordenadas elegidas por el cliente.
                ConnectorBlockEntity connector =
                        NetGuard.resolve(player, payload.pos(), ConnectorBlockEntity.class);
                if (connector == null) { NetGuard.logRejected("ConnectorPayload", player); return; }

                connector.setConnectionState(payload.connected());
            });
        });
    }

    private static void registerConnectorConfig() {
        ServerPlayNetworking.registerGlobalReceiver(ConnectorConfigPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            if (!CONFIG_LIMITER.tryAcquire(player)) return;

            context.server().execute(() -> {
                ConnectorBlockEntity connector =
                        NetGuard.resolve(player, payload.pos(), ConnectorBlockEntity.class);
                if (connector == null) { NetGuard.logRejected("ConnectorConfig", player); return; }

                if (!isValidBaudRate(payload.baudRate())) {
                    SerialCraft.LOGGER.debug("Baudios invalidos ({}) de {}",
                            payload.baudRate(), player.getName().getString());
                    return;
                }
                int speed = Math.clamp(payload.speedMode(), MIN_SPEED_MODE, MAX_SPEED_MODE);

                connector.updateSettings(payload.baudRate(), speed);
                connector.setConnectionState(payload.connected());
            });
        });
    }

    // ══════════════════════════════════════════════════════════════════════

    private static boolean isValidBaudRate(int baud) {
        for (int valid : VALID_BAUD_RATES) if (valid == baud) return true;
        return false;
    }
}