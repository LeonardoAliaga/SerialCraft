package com.serialcraft;

import com.mojang.blaze3d.platform.InputConstants;
import com.serialcraft.block.ArduinoIOBlock;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.client.SerialDebugHud;
import com.serialcraft.client.events.GameEventsTracker;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.network.BoardListResponsePayload;
import com.serialcraft.network.SerialOutputPayload;
import com.serialcraft.screen.PanelUI;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

/**
 * Punto de entrada del cliente.
 *
 * Se eliminaron los cinco metodos de reenvio (conectar, desconectar,
 * iniciarServidorWifi, detenerServidorWifi, enviarArduinoLocal). Eran una capa
 * de indireccion de una linea sobre ConnectionManager que no anadia nada, pero
 * si daba a entender que esta clase era la duena de la conexion. Tambien se
 * eliminaron los tres campos publicos estaticos mutables (arduinoPort,
 * globalSerialSpeed, wifiIp): globalSerialSpeed no se escribia en ninguna parte
 * del proyecto, siempre valia 2, y sin embargo el bucle lector tomaba
 * decisiones a partir de el.
 */
public class SerialCraftClient implements ClientModInitializer {

    KeyMapping.Category KEY_CATEGORY = KeyMapping.Category.register(
            Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "general")
    );

    private static KeyMapping debugHudKey;

    @Override
    public void onInitializeClient() {
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "debug_hud"),
                SerialDebugHud::render
        );

        // CORRECCIÓN: Usar KeyMappingHelper en lugar de KeyBindingHelper
        debugHudKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.serialcraft.debug_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                KEY_CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (debugHudKey.consumeClick()) {
                SerialDebugHud.toggle();
            }
        });

        registerLifecycle();
        registerNetworkHandlers();
        registerBlockInteraction();

        // Telemetria de la pestana Eventos: un unico hook de tick, la logica
        // vive en GameEventsTracker para no convertir esta clase en el sitio
        // donde termina todo (ver el comentario de clase de ConnectionManager
        // sobre que paso con los tres duenos del estado de conexion).
        ClientTickEvents.END_CLIENT_TICK.register(GameEventsTracker::tick);
    }

    // ══════════════════════════════════════════════════════════════════════

    private void registerLifecycle() {
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                SerialDebugHud.addLog("Mundo cargado. Servidor Wi-Fi en espera."));

        // Al salir del mundo se cierra TODO el hardware y se limpia el estado.
        // Sin esto, volver a entrar abria el panel con una conexion que ya no
        // existia: el propio codigo original lo llamaba "conexion fantasma".
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            ConnectionManager.disconnectAll();
            ConnectionManager.clearHistory();
            PanelUI.clearSelectedDevice();
            GameEventsTracker.reset();
            SerialDebugHud.addLog("Desconectado del mundo. Estado limpiado.");
        });
    }

    private void registerNetworkHandlers() {
        ClientPlayNetworking.registerGlobalReceiver(SerialOutputPayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    SerialDebugHud.addLog("TX: " + payload.message());
                    ConnectionManager.sendMessageToBoard(payload.message());
                }));

        ClientPlayNetworking.registerGlobalReceiver(BoardListResponsePayload.TYPE, (payload, context) ->
                context.client().execute(() -> {
                    if (context.client().gui.screen() instanceof PanelUI panel) {
                        panel.updateBoardList(payload.boards());
                    }
                }));
    }

    private void registerBlockInteraction() {
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
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
                // ArduinoIOBlock.useWithoutItem, que es donde vive la logica
                // de alternar el lado.
                if (state.getBlock() instanceof ArduinoIOBlock block) {
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
        });
    }
}