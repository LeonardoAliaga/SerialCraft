package com.serialcraft.connection;

import com.serialcraft.client.SerialDebugHud;
import com.serialcraft.network.SerialInputPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class ConnectionManager {
    private static final SerialHandler serialHandler = new SerialHandler();
    private static final WifiHandler wifiHandler = new WifiHandler();

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
        }
    }

    public static void onMessageReceived(String msg) {
        SerialDebugHud.addLog("RX: " + msg);
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            mc.execute(() -> ClientPlayNetworking.send(new SerialInputPayload(msg)));
        }
    }

    public static void disconnectAll() {
        serialHandler.disconnect();
        wifiHandler.disconnect();
    }
}