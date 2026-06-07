package com.serialcraft.connection;

import com.serialcraft.client.SerialDebugHud;
import com.serialcraft.network.SerialInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class ConnectionManager {
    private static final SerialHandler serialHandler = new SerialHandler();
    private static final WifiHandler wifiHandler = new WifiHandler();

    // Historial para la consola del HomeScreen (Bidireccional)
    public static final List<String> messageHistory = new CopyOnWriteArrayList<>();

    public static SerialHandler getSerial() { return serialHandler; }
    public static WifiHandler getWifi() { return wifiHandler; }

    public static void sendMessageToBoard(String msg) {
        boolean sent = false;
        if (serialHandler.isConnected()) {
            serialHandler.send(msg);
            sent = true;
        }
        if (wifiHandler.isConnected()) {
            wifiHandler.send(msg);
            sent = true;
        }

        if (!sent) {
            SerialDebugHud.addLog("Error: Ninguna placa conectada (USB/Wi-Fi).");
            addHistory("Error: No conectado", true);
        } else {
            addHistory("TX: " + msg, false);
        }
    }

    public static void onMessageReceived(String msg) {
        SerialDebugHud.addLog("RX: " + msg);
        addHistory("RX: " + msg, false);

        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.execute(() -> ClientPlayNetworking.send(new SerialInputPayload(msg)));
        }
    }

    public static void disconnectAll() {
        serialHandler.disconnect();
        wifiHandler.disconnect();
    }

    private static void addHistory(String msg, boolean error) {
        // Mantiene solo los últimos 8 mensajes para el terminal visual
        if (messageHistory.size() >= 8) {
            messageHistory.remove(0);
        }
        messageHistory.add(msg);
    }
}