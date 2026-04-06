package com.serialcraft;

import com.fazecast.jSerialComm.SerialPort;
import com.mojang.blaze3d.platform.InputConstants;
import com.serialcraft.block.ArduinoIOBlock;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.client.SerialDebugHud;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.network.BoardListResponsePayload;
import com.serialcraft.network.SerialOutputPayload;
import com.serialcraft.screen.ConnectorScreen;
import com.serialcraft.screen.IOScreen;
import com.serialcraft.screen.PanelUI;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class SerialCraftClient implements ClientModInitializer {

    // --- Variables proxy públicas ---
    public static SerialPort arduinoPort    = null;
    public static int globalSerialSpeed     = 2;
    public static boolean isWifiConnected   = false;
    public static String wifiIp             = "";

    private static KeyMapping debugHudKey;
    private static final Identifier CATEGORY_ID = Identifier.parse("serialcraft:general");

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(new SerialDebugHud());

        debugHudKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.serialcraft.debug_hud",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_F7,
                new KeyMapping.Category(CATEGORY_ID)
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (debugHudKey.consumeClick()) {
                SerialDebugHud.isDebugEnabled = !SerialDebugHud.isDebugEnabled;
            }
        });

        // ── CICLO DE VIDA DE LA CONEXIÓN (MUNDO) ─────────────────────────

        // Al ENTRAR al mundo: solo registramos el evento, el servidor Wi-Fi
        // se inicia manualmente desde WelcomeScreen al pulsar "Buscar Wi-Fi".
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            SerialDebugHud.addLog("Mundo iniciado. Servidor Wi-Fi en espera (manual).");
        });

        // Al SALIR del mundo: matamos las conexiones y liberamos el puerto
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            desconectar();
            SerialDebugHud.addLog("Desconectado por salida del mundo.");
        });

        // ── Handlers de red ───────────────────────────────────────────────

        ClientPlayNetworking.registerGlobalReceiver(SerialOutputPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                String msg = payload.message();
                SerialDebugHud.addLog("TX: " + msg);
                ConnectionManager.sendMessageToBoard(msg);
            });
        });

        ClientPlayNetworking.registerGlobalReceiver(BoardListResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof ConnectorScreen screen) {
                    screen.updateBoardList(payload.boards());
                } else if (mc.screen instanceof PanelUI panel) {
                    panel.updatePlacasList(payload.boards());
                }
            });
        });

        // ── Interacción con bloques ───────────────────────────────────────
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            BlockPos pos    = hit.getBlockPos();
            var      state  = level.getBlockState(pos);
            Minecraft mc    = Minecraft.getInstance();

            if (state.is(ModBlocks.CONNECTOR_BLOCK)) {
                mc.setScreen(new PanelUI());
                return InteractionResult.SUCCESS;
            }

            if (state.is(ModBlocks.IO_BLOCK)) {
                if (state.getBlock() instanceof ArduinoIOBlock ioBlock) {
                    Vec3 hitPos = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
                    if (ioBlock.getHitButton(hitPos) != null) return InteractionResult.PASS;
                }

                int    mode = 0;
                String data = "";
                var    be   = level.getBlockEntity(pos);

                if (be instanceof ArduinoIOBlockEntity io) {
                    if (io.ownerUUID != null && !io.ownerUUID.equals(player.getUUID())) {
                        player.displayClientMessage(
                                Component.translatable("message.serialcraft.not_owner"), true);
                        return InteractionResult.FAIL;
                    }
                    mode = io.ioMode;
                    data = io.targetData;
                }

                mc.setScreen(new IOScreen(pos, mode, data));
                return InteractionResult.SUCCESS;
            }

            return InteractionResult.PASS;
        });
    }

    // ── Enrutadores a ConnectionManager ──────────────────────────────────

    public static Component conectar(String puerto, int baudRate) {
        return ConnectionManager.getSerial().connect(puerto, baudRate);
    }

    public static void desconectar() {
        ConnectionManager.disconnectAll();
    }

    public static Component iniciarServidorWifi(int puerto) {
        return ConnectionManager.getWifi().startServer(puerto);
    }

    public static void detenerServidorWifi() {
        ConnectionManager.getWifi().disconnect();
    }

    public static void enviarArduinoLocal(String msg) {
        ConnectionManager.sendMessageToBoard(msg);
    }
}