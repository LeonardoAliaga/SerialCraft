package com.serialcraft.connection;

import com.serialcraft.client.ui.pages.VisualizePage;
import com.serialcraft.identity.BoardIdentity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorOutputTest {
    private Socket peer;
    private HardwareOutbox outbox;

    @BeforeEach void connectLocalBoard() throws Exception {
        var field = ConnectionManager.class.getDeclaredField("OUTBOX");
        field.setAccessible(true);
        outbox = (HardwareOutbox) field.get(null);
        int port;
        try (ServerSocket available = new ServerSocket(0)) { port = available.getLocalPort(); }
        int previousEpoch = ConnectionManager.sessionEpoch();
        var wifi = ConnectionManager.getWifi();
        wifi.startServer(port, "ABC234");
        peer = new Socket(InetAddress.getLoopbackAddress(), port);
        peer.setSoTimeout(2000);
        new PrintWriter(peer.getOutputStream(), true, StandardCharsets.UTF_8).println("ABC234");
        var reader = new BufferedReader(new InputStreamReader(peer.getInputStream(), StandardCharsets.UTF_8));
        assertEquals("OK", reader.readLine());
        long deadline = System.nanoTime() + 2_000_000_000L;
        while ((!wifi.isConnected() || ConnectionManager.sessionEpoch() == previousEpoch)
                && System.nanoTime() < deadline) Thread.sleep(5);
        assertNotEquals(previousEpoch, ConnectionManager.sessionEpoch());
        assertTrue(wifi.isConnected());
    }

    @AfterEach void disconnectLocalBoard() throws Exception {
        ConnectionManager.disconnectAll();
        if (peer != null) peer.close();
    }

    @Test void samplesAndStopUseTheValidatedStartChannelEvenIfTheDraftChanges() throws Exception {
        synchronized (ConnectionManager.class) {
            synchronized (outbox) {
                for (boolean closeScreen : new boolean[]{false, true}) {
                    var page = new VisualizePage();
                    setDraft(page, " led_a ");
                    toggle(page);
                    assertTrue(page.isGeneratingSignal());
                    setDraft(page, "led_b");
                    page.backgroundTick();
                    assertTrue(outbox.poll().text().startsWith("led_a:"));
                    outbox.offer("led_a:200", HardwareOutbox.Kind.SIGNAL, true, false);
                    if (closeScreen) page.onClose(); else toggle(page);
                    assertFalse(page.isGeneratingSignal());
                    assertEquals("led_a:0", outbox.poll().text());
                    assertNull(outbox.poll());
                    toggle(page);
                    page.backgroundTick();
                    assertTrue(outbox.poll().text().startsWith("led_b:"));
                    page.onClose();
                    assertEquals("led_b:0", outbox.poll().text());
                    assertNull(outbox.poll());
                }
            }
        }
    }

    @Test void oldExecutionCannotSendOrDiscardValuesInANewSession() throws Exception {
        synchronized (ConnectionManager.class) {
            synchronized (outbox) {
                for (int exit = 0; exit < 3; exit++) {
                    var page = new VisualizePage();
                    setDraft(page, "led_a");
                    toggle(page);
                    page.backgroundTick();
                    assertNotNull(outbox.poll());
                    // A newly connected board clears the old queue and advances the epoch.
                    ConnectionManager.onLinkConnected(ConnectionManager.getWifi(), BoardIdentity.unknown());
                    outbox.offer("led_a:123", HardwareOutbox.Kind.SIGNAL, true, false);
                    if (exit == 0) page.onClose();
                    else if (exit == 1) toggle(page);
                    else page.backgroundTick();
                    assertFalse(page.isGeneratingSignal());
                    assertEquals("led_a:123", outbox.poll().text());
                    assertNull(outbox.poll());
                }
            }
        }
    }

    private static void setDraft(VisualizePage page, String key) throws Exception {
        var field = VisualizePage.class.getDeclaredField("genKey");
        field.setAccessible(true);
        field.set(page, key);
    }

    private static void toggle(VisualizePage page) throws Exception {
        var method = VisualizePage.class.getDeclaredMethod("toggleGenerator");
        method.setAccessible(true);
        method.invoke(page);
    }
}
