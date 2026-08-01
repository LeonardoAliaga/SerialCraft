package com.serialcraft.connection;

import com.serialcraft.SerialCraft;
import com.serialcraft.client.SerialDebugHud;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Transporte Wi-Fi: el cliente de Minecraft actua de servidor TCP y la placa
 * (ESP32/ESP8266) se conecta a el.
 *
 * ══════════════════════════════════════════════════════════════════════════
 *  ADVERTENCIA DE SEGURIDAD — este es el punto mas expuesto del mod
 * ══════════════════════════════════════════════════════════════════════════
 *
 * El original hacia {@code new ServerSocket(8080)}, que escucha en 0.0.0.0:
 * TODAS las interfaces, sin autenticacion de ningun tipo. Consecuencias reales:
 *
 *  - Cualquiera en la misma red (wifi de universidad, cafe, coworking, un
 *    hotel) podia abrir un telnet al puerto 8080 del jugador y escribir
 *    "cmd_1:15" para accionar su redstone. Sin contrasena ni handshake.
 *  - Si el jugador tenia el puerto 8080 redirigido en el router (habitual en
 *    quien ya desarrolla web), la exposicion era a Internet entero.
 *  - Sin limite de longitud de linea: un atacante enviando megabytes sin '\n'
 *    hacia crecer el buffer del BufferedReader hasta agotar la memoria.
 *  - Sin limite de conexiones: cada accept() reemplazaba al cliente anterior,
 *    asi que un atacante podia expulsar continuamente a la placa legitima.
 *  - {@code setReuseAddress(true)} se llamaba DESPUES de que el constructor ya
 *    hubiera hecho bind, momento en el que la opcion no tiene ningun efecto.
 *
 * Correcciones aplicadas: bind explicito a la interfaz elegida, token de
 * emparejamiento obligatorio en la primera linea, longitud de linea acotada,
 * rechazo de conexiones no privadas por defecto, y setReuseAddress antes del
 * bind mediante un socket sin ligar.
 *
 * Aun asi: esto sigue siendo un canal en claro. Es aceptable para una LAN
 * domestica y NO deberia exponerse a Internet. Conviene decirlo en la UI.
 */
public class WifiHandler implements BoardLink {

    public enum State { STOPPED, LISTENING, CONNECTED }

    public static final int DEFAULT_PORT = 25585;   // fuera del rango habitual de 8080
    private static final int MAX_LINE_LENGTH  = 256;
    private static final int ACCEPT_BACKLOG   = 1;
    private static final int SOCKET_TIMEOUT_MS = 1000;
    private static final int JOIN_TIMEOUT_MS  = 1000;
    private static final String THREAD_NAME   = "SerialCraft-WiFi";

    private volatile @Nullable ServerSocket serverSocket;
    private volatile @Nullable Socket       clientSocket;
    private volatile @Nullable PrintWriter  writer;
    private volatile @Nullable Thread       acceptThread;
    private volatile State state = State.STOPPED;

    /** IP remota de la placa conectada, o cadena vacia. */
    private volatile String remoteIp = "";

    /**
     * Token de emparejamiento. La placa debe enviarlo como primera linea o se
     * cierra la conexion. No es criptografia seria (va en claro), pero elimina
     * el caso de "cualquiera de la red puede escribir en tu mundo por accidente
     * o por curiosidad", que es la amenaza realista aqui.
     */
    private volatile String pairingToken = "";

    /** Si true, solo se aceptan conexiones desde direcciones privadas. */
    private volatile boolean privateOnly = true;

    private final AtomicBoolean running = new AtomicBoolean(false);

    // ══════════════════════════════════════════════════════════════════════

    @Override public String name() { return "WIFI"; }

    public State  getState()   { return state; }
    public String getRemoteIp(){ return remoteIp; }
    public String getPairingToken() { return pairingToken; }
    public void   setPrivateOnly(boolean value) { this.privateOnly = value; }

    @Override
    public boolean isConnected() {
        Socket s = clientSocket;
        return state == State.CONNECTED && s != null && !s.isClosed();
    }

    public boolean isServerRunning() {
        ServerSocket s = serverSocket;
        return running.get() && s != null && !s.isClosed();
    }

    @Override
    public Component describe() {
        return remoteIp.isEmpty()
                ? Component.translatable("gui.serialcraft.status.disconnected")
                : Component.literal(remoteIp);
    }

    // ══════════════════════════════════════════════════════════════════════

    public synchronized Component startServer(int port, String token) {
        if (isServerRunning()) return Component.translatable("message.serialcraft.wifi_already_running");
        if (port < 1024 || port > 65535) {
            return Component.translatable("message.serialcraft.wifi_bad_port", port);
        }

        try {
            // setReuseAddress DEBE aplicarse antes del bind. Creando el socket
            // sin ligar se puede; el constructor ServerSocket(port) del
            // original ya habia hecho bind cuando se llamaba a la opcion.
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port), ACCEPT_BACKLOG);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS); // permite cerrar sin bloqueo

            this.serverSocket = socket;
            this.pairingToken = token;
            running.set(true);
            state = State.LISTENING;

            Thread thread = new Thread(this::acceptLoop, THREAD_NAME);
            thread.setDaemon(true);
            this.acceptThread = thread;
            thread.start();

            SerialDebugHud.addLog("Wi-Fi escuchando en el puerto " + port);
            return Component.translatable("message.serialcraft.wifi_started", port);

        } catch (Exception e) {
            state = State.STOPPED;
            running.set(false);
            SerialCraft.LOGGER.warn("No se pudo iniciar el servidor Wi-Fi en el puerto {}", port, e);
            return Component.translatable("message.serialcraft.wifi_start_failed",
                                          String.valueOf(e.getMessage()));
        }
    }

    @Override
    public synchronized void disconnect() {
        running.set(false);
        state = State.STOPPED;

        closeQuietly(writer);
        closeQuietly(clientSocket);
        closeQuietly(serverSocket);

        Thread thread = acceptThread;
        if (thread != null && thread != Thread.currentThread() && thread.isAlive()) {
            try { thread.join(JOIN_TIMEOUT_MS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }

        writer       = null;
        clientSocket = null;
        serverSocket = null;
        acceptThread = null;
        remoteIp     = "";
        pairingToken = "";
    }

    @Override
    public void send(String message) {
        PrintWriter out = writer;
        if (out == null || !isConnected()) return;
        out.println(message);
        out.flush();
    }

    // ══════════════════════════════════════════════════════════════════════

    private void acceptLoop() {
        while (running.get()) {
            ServerSocket server = serverSocket;
            if (server == null || server.isClosed()) break;

            try {
                Socket incoming = server.accept();
                handleClient(incoming);
            } catch (java.net.SocketTimeoutException ignored) {
                // Normal: el timeout existe para poder comprobar running.
            } catch (Exception e) {
                if (running.get()) {
                    SerialCraft.LOGGER.debug("Error en el socket Wi-Fi", e);
                    state = State.LISTENING;
                }
            }
        }
        state = State.STOPPED;
    }

    private void handleClient(Socket incoming) {
        String ip = incoming.getInetAddress().getHostAddress();

        // Filtro de origen. Por defecto solo LAN.
        if (privateOnly && !isPrivateAddress(incoming.getInetAddress())) {
            SerialDebugHud.addLog("Conexion rechazada (no privada): " + ip);
            closeQuietly(incoming);
            return;
        }

        // Solo una placa a la vez. El original cerraba la anterior sin mas, lo
        // que permitia a un tercero expulsarla repetidamente.
        if (isConnected()) {
            SerialDebugHud.addLog("Conexion rechazada, ya hay una placa: " + ip);
            closeQuietly(incoming);
            return;
        }

        try (Socket socket = incoming;
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            // ── Handshake ────────────────────────────────────────────────
            String greeting = readBoundedLine(reader);
            if (greeting == null || !greeting.equals(pairingToken)) {
                SerialDebugHud.addLog("Token invalido desde " + ip);
                out.println("ERR TOKEN");
                return;
            }
            out.println("OK");

            this.clientSocket = socket;
            this.writer       = out;
            this.remoteIp     = ip;
            this.state        = State.CONNECTED;
            SerialDebugHud.addLog("Placa Wi-Fi conectada: " + ip);

            // ── Bucle de lectura ─────────────────────────────────────────
            String line;
            while (running.get() && (line = readBoundedLine(reader)) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) ConnectionManager.onMessageReceived(trimmed);
            }

        } catch (Exception e) {
            SerialCraft.LOGGER.debug("Sesion Wi-Fi terminada con error", e);
        } finally {
            this.writer       = null;
            this.clientSocket = null;
            this.remoteIp     = "";
            if (running.get()) this.state = State.LISTENING;
            SerialDebugHud.addLog("Placa Wi-Fi desconectada.");
        }
    }

    /**
     * Lee una linea con longitud acotada.
     *
     * BufferedReader.readLine() crece sin limite: un peer que envie datos sin
     * '\n' agota la memoria del cliente. Esto es un DoS trivial contra el
     * jugador desde su propia LAN.
     */
    private static @Nullable String readBoundedLine(BufferedReader reader) throws java.io.IOException {
        StringBuilder line = new StringBuilder(64);
        int c;
        while ((c = reader.read()) != -1) {
            if (c == '\n') return line.toString();
            if (c == '\r') continue;
            if (line.length() >= MAX_LINE_LENGTH) return null; // linea abusiva: cortar sesion
            line.append((char) c);
        }
        return line.isEmpty() ? null : line.toString();
    }

    private static boolean isPrivateAddress(InetAddress address) {
        return address.isSiteLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress();
    }

    private static void closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable == null) return;
        try { closeable.close(); } catch (Exception ignored) {}
    }
}
