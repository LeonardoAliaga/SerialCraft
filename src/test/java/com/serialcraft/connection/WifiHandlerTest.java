package com.serialcraft.connection;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

class WifiHandlerTest {
    @Test void onlyCompleteBoundedFramesAreAccepted() throws Exception {
        assertEquals("sensor:128", WifiHandler.readBoundedLine(new BufferedReader(new StringReader("sensor:128\r\n"))));
        assertNull(WifiHandler.readBoundedLine(new BufferedReader(new StringReader("sensor:255"))));
        assertNull(WifiHandler.readBoundedLine(new BufferedReader(new StringReader("x".repeat(257) + "\n"))));
        assertEquals("x".repeat(256), WifiHandler.readBoundedLine(new BufferedReader(new StringReader("x".repeat(256) + "\n"))));
    }

    @Test void connectionRequiresAuthenticationAndCanRestartWithoutStaleState() throws Exception {
        var wifi = new WifiHandler();
        try {
            for (int cycle = 0; cycle < 2; cycle++) {
                int port = availablePort();
                wifi.startServer(port, "ABC234");
                assertTrue(wifi.isServerRunning()); assertFalse(wifi.isConnected());
                try (Socket peer = new Socket(InetAddress.getLoopbackAddress(), port)) {
                    peer.setSoTimeout(2000);
                    var reader = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
                    var writer = new PrintWriter(peer.getOutputStream(), true, StandardCharsets.UTF_8);
                    writer.println("BAD234");
                    assertEquals("ERR TOKEN", reader.readLine());
                    assertNull(reader.readLine());
                }
                assertFalse(wifi.isConnected());
                try (Socket peer = new Socket(InetAddress.getLoopbackAddress(), port)) {
                    peer.setSoTimeout(2000);
                    var reader = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
                    var writer = new PrintWriter(peer.getOutputStream(), true, StandardCharsets.UTF_8);
                    writer.println("ABC234");
                    assertEquals("OK", reader.readLine());
                    await(wifi::isConnected);
                    assertTrue(wifi.onBoardHello(com.serialcraft.identity.BoardHello.parse("mc_id:model=Legacy").orElseThrow()));
                    int epoch = ConnectionManager.sessionEpoch();
                    assertFalse(wifi.send("led:128", epoch - 1));
                    assertTrue(wifi.send("led:255", epoch));
                    assertEquals("led:255", reader.readLine());
                    wifi.disconnect();
                    assertNull(reader.readLine());
                }
                assertFalse(wifi.isConnected()); assertFalse(wifi.isServerRunning());
            }
        } finally { wifi.disconnect(); }
    }

    @Test void stoppingServerClosesAnIncompleteHandshake() throws Exception {
        var wifi = new WifiHandler();
        try {
            int port = availablePort();
            wifi.startServer(port, "ABC234");
            try (Socket peer = new Socket(InetAddress.getLoopbackAddress(), port)) {
                peer.setSoTimeout(2000);
                peer.getOutputStream().write('A');
                peer.getOutputStream().flush();
                wifi.disconnect();
                try { assertEquals(-1, peer.getInputStream().read()); }
                catch (java.net.SocketException reset) {
                    // Closing with unread handshake bytes can produce TCP RST instead of EOF.
                }
                assertFalse(wifi.isServerRunning()); assertFalse(wifi.isConnected());
            }
        } finally { wifi.disconnect(); }
    }

    private static int availablePort() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) { return socket.getLocalPort(); }
    }
    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + 2_000_000_000L;
        while (!condition.getAsBoolean() && System.nanoTime() < deadline) Thread.sleep(5);
        assertTrue(condition.getAsBoolean(), "transport state did not update");
    }
}
