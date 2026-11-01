package com.serialcraft.connection;

import com.serialcraft.config.OutputSettings;
import com.serialcraft.config.OutputSettings.ConnectAction;
import com.serialcraft.config.OutputSettings.DisconnectAction;
import com.serialcraft.config.SerialConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

/** Real Wi-Fi writer and normal disconnect ordering, without physical hardware. */
class OutputLifecycleTest {
    private Socket peer;
    private BufferedReader reader;
    private OutputSettings previous;

    private void connect(DisconnectAction action, int value) throws Exception {
        previous = SerialConfig.get().outputSettings;
        SerialConfig.get().outputSettings = new OutputSettings(ConnectAction.CUSTOM, 80, action, value);
        int port;
        try (ServerSocket available = new ServerSocket(0)) { port = available.getLocalPort(); }
        int epoch = ConnectionManager.sessionEpoch();
        var wifi = ConnectionManager.getWifi();
        wifi.startServer(port, "ABC234");
        peer = new Socket(InetAddress.getLoopbackAddress(), port);
        peer.setSoTimeout(3000);
        reader = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
        new PrintWriter(peer.getOutputStream(), true, StandardCharsets.UTF_8).println("ABC234");
        assertEquals("OK", reader.readLine());
        long deadline = System.nanoTime() + 2_000_000_000L;
        while ((!wifi.isConnected() || ConnectionManager.sessionEpoch() == epoch)
                && System.nanoTime() < deadline) Thread.sleep(5);
        assertNotEquals(epoch, ConnectionManager.sessionEpoch());
        assertTrue(wifi.isConnected());
    }

    private String nextLamp() throws Exception {
        String line;
        while ((line = reader.readLine()) != null) if (line.startsWith("lamp:")) return line;
        return null;
    }

    @AfterEach void close() throws Exception {
        ConnectionManager.disconnectAll();
        if (previous != null) SerialConfig.get().outputSettings = previous;
        if (peer != null) peer.close();
    }

    @Test void customInitialBootRetryAndFinalValuesReachTheBoardInOrder() throws Exception {
        connect(DisconnectAction.CUSTOM, 120);
        ConnectionManager.sendHardwareOutput("lamp:255", false);
        assertEquals("lamp:80", nextLamp());
        ConnectionManager.sendHardwareOutput("lamp:255", false);
        assertEquals("lamp:80", nextLamp());
        ConnectionManager.sendHardwareOutput("lamp:127", false);
        assertEquals("lamp:127", nextLamp());
        ConnectionManager.disconnectAll();
        assertEquals("lamp:120", nextLamp());
        assertNull(nextLamp());
    }

    @Test void keepSendsNoFinalValue() throws Exception {
        connect(DisconnectAction.KEEP, 0);
        ConnectionManager.sendMessageToBoard("lamp:77");
        assertEquals("lamp:77", nextLamp());
        ConnectionManager.disconnectAll();
        assertNull(nextLamp());
    }

    @Test void zeroIsSentBeforeTheSocketCloses() throws Exception {
        connect(DisconnectAction.ZERO, 0);
        ConnectionManager.sendMessageToBoard("lamp:200");
        assertEquals("lamp:200", nextLamp());
        ConnectionManager.disconnectAll();
        assertEquals("lamp:0", nextLamp());
        assertNull(nextLamp());
    }
}
