package com.serialcraft.connection;

import com.serialcraft.SerialCraft;
import com.serialcraft.client.SerialDebugHud;
import com.serialcraft.identity.BoardHello;
import com.serialcraft.identity.BoardIdentity;
import com.serialcraft.identity.TrustAuth;
import com.serialcraft.identity.TrustedBoardStore;
import com.serialcraft.identity.WifiHandshake;
import com.serialcraft.util.NetUtils;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Transporte Wi-Fi: el cliente de Minecraft actua de servidor TCP y la placa
 * (ESP32/ESP8266) se conecta a el.
 */
public class WifiHandler implements BoardLink {

    public enum State { STOPPED, LISTENING, CONNECTED }

    public static final int DEFAULT_PORT = 25585;   // fuera del rango habitual de 8080
    private static final int MAX_LINE_LENGTH  = 256;
    private static final int ACCEPT_BACKLOG   = 1;
    private static final int SOCKET_TIMEOUT_MS = 1000;
    private static final int HANDSHAKE_TIMEOUT_MS = 5000;
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
     * cierra la conexion.
     */
    private volatile String pairingToken = "";

    /** Si true, solo se aceptan conexiones desde direcciones privadas. */
    private volatile boolean privateOnly = true;

    private final AtomicBoolean running = new AtomicBoolean(false);

    // ── Placa recordada ────────────────────────────────────────────────
    /** Como se autentico la sesion actual. */
    private volatile WifiHandshake.Method authMethod = WifiHandshake.Method.NONE;
    /** Identidad anunciada por la placa de esta sesion (con uid), pendiente de recordar. */
    private volatile BoardIdentity announcedIdentity = BoardIdentity.unknown();

    private static final SecureRandom TOKEN_RNG = new SecureRandom();

    /** Token de sesion de 6 caracteres, sin caracteres ambiguos (0/O, 1/I). */
    public static String newSessionToken() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder token = new StringBuilder(6);
        for (int i = 0; i < 6; i++) token.append(alphabet.charAt(TOKEN_RNG.nextInt(alphabet.length())));
        return token.toString();
    }

    // ════════════════════════════════════════════════════════════════════════════

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

    // ════════════════════════════════════════════════════════════════════════════

    public synchronized Component startServer(int port, String token) {
        if (isServerRunning()) return Component.translatable("message.serialcraft.wifi_already_running");
        if (port < 1024 || port > 65535) {
            return Component.translatable("message.serialcraft.wifi_bad_port", port);
        }

        try {
            ServerSocket socket = new ServerSocket();
            socket.setReuseAddress(true);
            socket.bind(new InetSocketAddress(port), ACCEPT_BACKLOG);
            socket.setSoTimeout(SOCKET_TIMEOUT_MS); // permite comprobar running periodicamente

            this.serverSocket = socket;
            this.pairingToken = token.trim();
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

    // ════════════════════════════════════════════════════════════════════════════
    //  PLACAS RECORDADAS
    // ════════════════════════════════════════════════════════════════════════════

    /** La placa conectada anuncio su identidad (mc_id). Lo llama ConnectionManager. */
    void onBoardHello(BoardIdentity id) {
        if (!isConnected() || !id.hasUid()) return;
        TrustedBoardStore store = BoardTrust.store();

        if (authMethod == WifiHandshake.Method.TRUSTED) {
            store.touch(id.uid(), id.model(), remoteIp);      // ya recordada: solo refrescar
            return;
        }
        // Entro con token: queda a la espera de "Recordar placa" (o se recuerda
        // sola si el jugador activo autoRemember).
        announcedIdentity = id;
        if (store.autoRemember()) rememberCurrentBoard();
    }

    /** Hay una placa conectada con token que se puede recordar ahora mismo. */
    public boolean canRemember() {
        return isConnected() && authMethod == WifiHandshake.Method.TOKEN
                && announcedIdentity.hasUid()
                && BoardTrust.store().find(announcedIdentity.uid()).isEmpty();
    }

    /** La placa conectada ya esta recordada. */
    public boolean isCurrentRemembered() {
        if (!isConnected()) return false;
        String uid = announcedIdentity.hasUid() ? announcedIdentity.uid() : sessionUid();
        return !uid.isEmpty() && BoardTrust.store().find(uid).isPresent();
    }

    private String sessionUid() {
        BoardIdentity id = ConnectionManager.activeIdentity();
        return id.hasUid() ? id.uid() : "";
    }

    /**
     * Genera una clave nueva, la guarda y se la entrega a la placa (mc_key).
     * Va por el canal Wi-Fi en claro: el primer emparejamiento debe hacerse en
     * una red de confianza (de ahi el filtro de IP privadas).
     */
    public boolean rememberCurrentBoard() {
        BoardIdentity id = announcedIdentity;
        if (!isConnected() || authMethod != WifiHandshake.Method.TOKEN || !id.hasUid()) return false;

        String secret = TrustAuth.newSecret();
        if (!BoardTrust.store().remember(id.uid(), id.model(), secret, remoteIp)) {
            SerialDebugHud.addLog("No se pudo guardar la placa recordada (fichero no escribible).");
            return false;
        }
        send(BoardHello.KEY_PREFIX + secret);     // directo, sin pasar por el registro de la consola
        SerialDebugHud.addLog("Placa recordada: " + id.model() + " [" + id.uid() + "]");
        return true;
    }

    /** Borra la clave de la placa conectada: la proxima vez pedira token. */
    public boolean forgetCurrentBoard() {
        String uid = announcedIdentity.hasUid() ? announcedIdentity.uid() : sessionUid();
        if (uid.isEmpty()) return false;
        boolean removed = BoardTrust.store().forget(uid);
        if (removed) SerialDebugHud.addLog("Placa olvidada: " + uid);
        return removed;
    }

    // ════════════════════════════════════════════════════════════════════════════

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

        // Filtro de origen: por defecto solo LAN y rangos privados / locales
        if (privateOnly && !NetUtils.isPrivate(incoming.getInetAddress())) {
            SerialDebugHud.addLog("Conexion rechazada (no privada): " + ip);
            SerialCraft.LOGGER.warn("Conexion Wi-Fi rechazada desde IP no privada: {}", ip);
            closeQuietly(incoming);
            return;
        }

        // Solo una placa a la vez
        if (isConnected()) {
            SerialDebugHud.addLog("Conexion rechazada, ya hay una placa: " + ip);
            closeQuietly(incoming);
            return;
        }

        try (Socket socket = incoming) {
            socket.setSoTimeout(HANDSHAKE_TIMEOUT_MS);
            socket.setTcpNoDelay(true);
            socket.setKeepAlive(true);

            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            PrintWriter out = new PrintWriter(
                    new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            // ── Handshake ────────────────────────────────────────────────
            // Dos caminos: el token de sesion de siempre, o "TRUST <uid>" con
            // reto-respuesta para una placa recordada (ver WifiHandshake).
            WifiHandshake.Result auth = WifiHandshake.perform(
                    () -> readBoundedLine(reader), out::println, pairingToken, BoardTrust.store());
            if (!auth.ok()) {
                // No se registra el token esperado ni el recibido: son credenciales.
                SerialDebugHud.addLog("Conexion rechazada desde " + ip + ": " + auth.reason());
                SerialCraft.LOGGER.warn("Handshake Wi-Fi rechazado desde {}: {}", ip, auth.reason());
                return;
            }

            // Desactivar timeout tras autenticacion exitosa para recepcion de datos
            socket.setSoTimeout(0);

            this.clientSocket = socket;
            this.writer       = out;
            this.remoteIp     = ip;
            this.authMethod   = auth.method();
            this.announcedIdentity = BoardIdentity.unknown();
            this.state        = State.CONNECTED;
            SerialDebugHud.addLog("Placa Wi-Fi conectada: " + ip
                    + (auth.method() == WifiHandshake.Method.TRUSTED ? " (recordada, sin token)" : ""));
            SerialCraft.LOGGER.info("Placa Wi-Fi conectada desde {} ({})", ip, auth.method());

            BoardIdentity known = BoardIdentity.unknown();
            if (auth.method() == WifiHandshake.Method.TRUSTED) {
                var entry = BoardTrust.store().find(auth.uid());
                if (entry.isPresent()) {
                    known = BoardHello.remembered(entry.get().name(), auth.uid());
                    BoardTrust.store().touch(auth.uid(), null, ip);
                }
            }
            ConnectionManager.onLinkConnected(this, known);

            // ── Bucle de lectura ─────────────────────────────────────────
            String line;
            while (running.get() && (line = readBoundedLine(reader)) != null) {
                String trimmed = line.trim();
                if (!trimmed.isEmpty()) ConnectionManager.onMessageReceived(trimmed);
            }

        } catch (Exception e) {
            SerialCraft.LOGGER.debug("Sesion Wi-Fi terminada", e);
        } finally {
            this.writer       = null;
            this.clientSocket = null;
            this.remoteIp     = "";
            this.authMethod   = WifiHandshake.Method.NONE;
            this.announcedIdentity = BoardIdentity.unknown();
            if (running.get()) this.state = State.LISTENING;
            ConnectionManager.onLinkClosed();
            SerialDebugHud.addLog("Placa Wi-Fi desconectada.");
        }
    }

    /**
     * Lee una linea con longitud acotada.
     */
    private static @Nullable String readBoundedLine(BufferedReader reader) throws java.io.IOException {
        StringBuilder line = new StringBuilder(64);
        int c;
        while ((c = reader.read()) != -1) {
            if (c == '\n') return line.toString();
            if (c == '\r') continue;
            if (line.length() >= MAX_LINE_LENGTH) return null;
            line.append((char) c);
        }
        return line.isEmpty() ? null : line.toString();
    }

    private static void closeQuietly(@Nullable AutoCloseable closeable) {
        if (closeable == null) return;
        try {
            closeable.close();
        } catch (Exception ignored) {}
    }
}
