package com.serialcraft;

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import com.fazecast.jSerialComm.SerialPort;
import com.mojang.blaze3d.platform.InputConstants;
import com.serialcraft.block.ArduinoIOBlock;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.block.entity.ArduinoIOBlockEntity;
import com.serialcraft.client.SerialDebugHud;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.network.BoardListResponsePayload;
import com.serialcraft.network.ConnectorPayload;
import com.serialcraft.network.SerialOutputPayload;
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

    // ── Variables proxy públicas ──────────────────────────────────────────
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

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            SerialDebugHud.addLog("Mundo iniciado. Servidor Wi-Fi en espera (manual).");
        });

        // Al SALIR del mundo: desconectar hardware Y limpiar el device estático
        // para que al volver no se abra el Dashboard con una "conexión fantasma".
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            desconectar();
            PanelUI.currentConnectedDevice = null;
            SerialDebugHud.addLog("Desconectado por salida del mundo. Estado limpiado.");
        });

        // ── Handlers de red ───────────────────────────────────────────────

        ClientPlayNetworking.registerGlobalReceiver(SerialOutputPayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                String msg = payload.message();
                SerialDebugHud.addLog("TX: " + msg);
                ConnectionManager.sendMessageToBoard(msg);
            });
        });

        
        // ── Lectura del chat hacia Arduino ───────────────────────────────────────
        ClientSendMessageEvents.CHAT.register(message -> {
         Minecraft mc = Minecraft.getInstance();

            // Verifica que el jugador esté dentro de un mundo
            if (mc.level == null || mc.player == null) {
                return;
            }

            // Envía solamente el texto escrito en el chat
            SerialDebugHud.addLog("CHAT -> Arduino: " + message);
            ConnectionManager.sendMessageToBoard(message);
        });

        // PanelUI es el único receptor de la lista de placas.
        // ConnectorScreen fue eliminado — ya no es necesario comprobarlo.
        ClientPlayNetworking.registerGlobalReceiver(BoardListResponsePayload.TYPE, (payload, context) -> {
            context.client().execute(() -> {
                Minecraft mc = Minecraft.getInstance();
                if (mc.screen instanceof PanelUI panel) {
                    panel.updatePlacasList(payload.boards());
                }
            });
        });

        // ── Interacción con bloques ───────────────────────────────────────
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() || hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;

            BlockPos pos   = hit.getBlockPos();
            var      state = level.getBlockState(pos);
            Minecraft mc   = Minecraft.getInstance();

            // ConnectorBlock → abre el panel principal (flujo normal)
            if (state.is(ModBlocks.CONNECTOR_BLOCK)) {
                mc.setScreen(new PanelUI(pos));
                return InteractionResult.SUCCESS;
            }

            // ArduinoIOBlock → abre PanelUI directamente en el editor de PlacasScreen.
            // Ya no existe IOScreen; PlacasScreen lee los datos del BlockEntity
            // a través del constructor PanelUI(connectorPos=null, ioEditPos=pos).
            if (state.is(ModBlocks.IO_BLOCK)) {
                if (state.getBlock() instanceof ArduinoIOBlock ioBlock) {
                    Vec3 hitPos = hit.getLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
                    if (ioBlock.getHitButton(hitPos) != null) return InteractionResult.PASS;
                }

                // Verificar propietario antes de abrir el editor
                var be = level.getBlockEntity(pos);
                if (be instanceof ArduinoIOBlockEntity io) {
                    if (io.ownerUUID != null && !io.ownerUUID.equals(player.getUUID())) {
                        player.displayClientMessage(
                                Component.translatable("message.serialcraft.not_owner"), true);
                        return InteractionResult.FAIL;
                    }
                }

                // null para connectorPos (no hay bloque Connector asociado),
                // pos como ioEditPos para ir directo al editor del bloque IO.
                mc.setScreen(new PanelUI(null, pos));
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