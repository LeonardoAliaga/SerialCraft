package com.serialcraft.connection;

import com.serialcraft.SerialCraftClient;
import com.serialcraft.client.SerialDebugHud;
import net.minecraft.network.chat.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class WifiHandler {
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private Thread wifiThread;
    private volatile boolean running = false;
    private PrintWriter out;

    // --- MÁQUINA DE ESTADOS PARA LA UI ---
    public enum State {
        STOPPED,    // Servidor apagado
        LISTENING,  // Servidor encendido, esperando conexión de Arduino
        CONNECTED   // Arduino conectado y transmitiendo
    }
    private State currentState = State.STOPPED;

    public State getState() {
        return currentState;
    }

    public boolean isConnected() {
        return currentState == State.CONNECTED && clientSocket != null && !clientSocket.isClosed();
    }

    public boolean isServerRunning() {
        return running && serverSocket != null && !serverSocket.isClosed();
    }

    public Component startServer(int port) {
        if (isServerRunning()) return Component.translatable("El servidor Wi-Fi ya está activo.");
        try {
            serverSocket = new ServerSocket(port);
            // CRUCIAL: Permite re-vincular el puerto inmediatamente después de reiniciar el mundo
            serverSocket.setReuseAddress(true);

            running = true;
            currentState = State.LISTENING;

            wifiThread = new Thread(this::serverLoop, "SerialCraft-WiFi-Listener");
            wifiThread.start();
            SerialDebugHud.addLog("Server Wi-Fi esperando en puerto " + port);
            return Component.translatable("Servidor Wi-Fi iniciado en puerto " + port);
        } catch (Exception e) {
            currentState = State.STOPPED;
            return Component.translatable("Error al iniciar Wi-Fi: " + e.getMessage());
        }
    }

    public void disconnect() {
        running = false;
        currentState = State.STOPPED;
        try {
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
            if (wifiThread != null) wifiThread.join(500);
        } catch (Exception ignored) {}

        SerialCraftClient.wifiIp = "";
        SerialCraftClient.isWifiConnected = false;
        out = null;
        clientSocket = null;
    }

    public void send(String msg) {
        if (isConnected() && out != null) {
            out.println(msg);
            out.flush();
        }
    }

    private void serverLoop() {
        while (running && !serverSocket.isClosed()) {
            try {
                Socket newClient = serverSocket.accept();
                if (clientSocket != null && !clientSocket.isClosed()) {
                    clientSocket.close();
                }
                clientSocket = newClient;

                SerialCraftClient.wifiIp = clientSocket.getInetAddress().getHostAddress();
                SerialCraftClient.isWifiConnected = true;
                currentState = State.CONNECTED; // Notifica a la UI que ya hay alguien

                out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(), StandardCharsets.UTF_8), true);
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(), StandardCharsets.UTF_8));

                SerialDebugHud.addLog("Wi-Fi conectado: " + SerialCraftClient.wifiIp);

                String line;
                while (running && (line = in.readLine()) != null) {
                    if (!line.trim().isEmpty()) {
                        ConnectionManager.onMessageReceived(line.trim());
                    }
                }

                // Si la placa se desconecta, volvemos a estado de escucha
                SerialDebugHud.addLog("Cliente Wi-Fi desconectado.");
                clientSocket.close();
                clientSocket = null;
                SerialCraftClient.isWifiConnected = false;
                SerialCraftClient.wifiIp = "";
                out = null;

                if (running) currentState = State.LISTENING;

            } catch (Exception e) {
                if (running) {
                    SerialDebugHud.addLog("Error en socket Wi-Fi.");
                    currentState = State.LISTENING;
                }
            }
        }
        currentState = State.STOPPED;
    }
}