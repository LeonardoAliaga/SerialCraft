package com.serialcraft.connection;

import com.fazecast.jSerialComm.SerialPort;
import com.serialcraft.SerialCraftClient;
import net.minecraft.network.chat.Component;

import java.nio.charset.StandardCharsets;

public class SerialHandler {
    private Thread serialThread;
    private volatile boolean running = false;

    public boolean isConnected() {
        return SerialCraftClient.arduinoPort != null && SerialCraftClient.arduinoPort.isOpen();
    }

    public String getPortName() {
        return isConnected() ? SerialCraftClient.arduinoPort.getSystemPortName() : "None";
    }

    public int getBaudRate() {
        return isConnected() ? SerialCraftClient.arduinoPort.getBaudRate() : 0;
    }

    public Component connect(String puerto, int baudRate) {
        if (isConnected()) return Component.translatable("message.serialcraft.already_connected");
        try {
            SerialPort[] ports = SerialPort.getCommPorts();
            if (ports.length == 0) return Component.translatable("message.serialcraft.no_ports");
            for (SerialPort p : ports) {
                if (p.getSystemPortName().equalsIgnoreCase(puerto)) {
                    SerialCraftClient.arduinoPort = p;
                    SerialCraftClient.arduinoPort.setBaudRate(baudRate);
                    if (SerialCraftClient.arduinoPort.openPort()) {
                        SerialCraftClient.arduinoPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 50, 0);
                        running = true;
                        serialThread = new Thread(this::readLoop, "SerialCraft-Reader");
                        serialThread.start();
                        return Component.translatable("message.serialcraft.connected", puerto);
                    }
                }
            }
            return Component.translatable("message.serialcraft.port_not_found", puerto);
        } catch (Exception e) {
            return Component.translatable("message.serialcraft.error", e.getMessage());
        }
    }

    public void disconnect() {
        running = false;
        if (SerialCraftClient.arduinoPort != null) {
            SerialCraftClient.arduinoPort.closePort();
            SerialCraftClient.arduinoPort = null;
        }
        if (serialThread != null) { try { serialThread.join(500); } catch (InterruptedException ignored) {} }
    }

    public void send(String msg) {
        if (isConnected()) {
            try {
                byte[] bytes = (msg + "\n").getBytes(StandardCharsets.UTF_8);
                SerialCraftClient.arduinoPort.writeBytes(bytes, bytes.length);
            } catch (Exception e) { e.printStackTrace(); }
        }
    }

    private void readLoop() {
        StringBuilder localBuffer = new StringBuilder();
        byte[] readBuffer = new byte[1024];
        long lastDispatchTime = 0;
        String pendingMessage = null;

        while (running && isConnected()) {
            try {
                int numRead = SerialCraftClient.arduinoPort.readBytes(readBuffer, readBuffer.length);
                if (numRead > 0) {
                    String chunk = new String(readBuffer, 0, numRead, StandardCharsets.UTF_8);
                    localBuffer.append(chunk);
                    int newlineIndex;
                    while ((newlineIndex = localBuffer.indexOf("\n")) != -1) {
                        String fullMessage = localBuffer.substring(0, newlineIndex).trim();
                        localBuffer.delete(0, newlineIndex + 1);
                        if (!fullMessage.isEmpty()) {
                            if (SerialCraftClient.globalSerialSpeed == 2) ConnectionManager.onMessageReceived(fullMessage);
                            else pendingMessage = fullMessage;
                        }
                    }
                }
                if (SerialCraftClient.globalSerialSpeed != 2 && pendingMessage != null) {
                    long now = System.currentTimeMillis();
                    int delay = (SerialCraftClient.globalSerialSpeed == 0) ? 200 : 50;
                    if (now - lastDispatchTime >= delay) {
                        ConnectionManager.onMessageReceived(pendingMessage);
                        pendingMessage = null;
                        lastDispatchTime = now;
                    }
                }
                Thread.sleep(2);
            } catch (Exception e) { e.printStackTrace(); disconnect(); }
        }
    }
}