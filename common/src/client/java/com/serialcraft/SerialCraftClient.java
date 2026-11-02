package com.serialcraft;

import com.mojang.blaze3d.platform.InputConstants;
import com.serialcraft.block.HardwareIOBlock;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.client.SerialDebugHud;
import com.serialcraft.client.events.GameEventsTracker;
import com.serialcraft.connection.BoardTrust;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.network.BoardListResponsePayload;
import com.serialcraft.network.SerialOutputPayload;
import com.serialcraft.network.ConfigResultPayload;
import com.serialcraft.screen.PanelUI;
import com.serialcraft.platform.ClientNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class SerialCraftClient  {

    private static final KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "general")
    );

    private static KeyMapping debugHudKey;

    public static KeyMapping createDebugKey() {
        return debugHudKey = new KeyMapping("key.serialcraft.debug_hud", InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F7, KEY_CATEGORY);
    }
    public static void tick(Minecraft client) {
        while (debugHudKey != null && debugHudKey.consumeClick()) SerialDebugHud.toggle();
        GameEventsTracker.tick(client);
        ConnectionManager.tick(client);
    }
    public static void joined(Minecraft client) {
        ConnectionManager.onWorldJoined(client);
        SerialDebugHud.addLog("Mundo cargado. Servidor Wi-Fi en espera.");
        BoardTrust.onWorldJoin();
    }
    public static void stopping() { ConnectionManager.disconnectAll(); }
    public static void disconnected() {
        ConnectionManager.disconnectAll();
        ConnectionManager.clearHistory();
        PanelUI.clearSelectedDevice();
        GameEventsTracker.reset();
        com.serialcraft.signal.SignalRecorder.INSTANCE.clear();
        SerialDebugHud.addLog("Desconectado del mundo. Estado limpiado.");
    }

    public static void registerNetworkHandlers() {
        ClientNetworking.registerGlobalReceiver(ConfigResultPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().gui.screen() instanceof PanelUI panel) panel.updateConfigResult(payload);
                }));
        ClientNetworking.registerGlobalReceiver(SerialOutputPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    ConnectionManager.sendHardwareOutput(payload.message(), payload.safetyStop());
                }));

        ClientNetworking.registerGlobalReceiver(BoardListResponsePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    var level = context.client().level;
                    if (level == null || !level.dimension().identifier().toString().equals(payload.dimension())) return;
                    if (context.client().gui.screen() instanceof PanelUI panel) {
                        panel.updateBoardList(payload.boards());
                    }
                }));
    }

    public static InteractionResult interact(net.minecraft.world.entity.player.Player player, net.minecraft.world.level.Level level, InteractionHand hand, net.minecraft.world.phys.BlockHitResult hit) {
            if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) {
                return InteractionResult.PASS;
            }

            BlockPos pos = hit.getBlockPos();
            var state = level.getBlockState(pos);
            Minecraft client = Minecraft.getInstance();

            if (state.is(ModBlocks.CONNECTOR_BLOCK)) {
                client.setScreenAndShow(new PanelUI(pos));
                return InteractionResult.SUCCESS;
            }

            if (state.is(ModBlocks.IO_BLOCK)) {
                // Si el clic cayo sobre un conector lateral, dejar pasar la
                // interaccion: ese caso lo gestiona el servidor en
                // HardwareIOBlock.useWithoutItem, que es donde vive la logica
                // de alternar el lado.
                if (state.getBlock() instanceof HardwareIOBlock block) {
                    Vec3 localHit = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
                    if (block.getHitButton(localHit) != null) return InteractionResult.PASS;
                }

                // NOTA: aqui NO se comprueba el dueno. El cliente no puede
                // decidir permisos (mentiria si lo intentara), y el original
                // hacia esa comprobacion aqui como si fuera seguridad real.
                // Abrir el editor no hace dano: el servidor rechazara el
                // ConfigPayload si el jugador no es el dueno.
                client.setScreenAndShow(new PanelUI(null, pos));
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.PASS;

    }
}
