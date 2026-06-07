package com.serialcraft.client.ui.pages;

import com.serialcraft.SerialCraftClient;
import com.serialcraft.client.ui.NavBar;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.connection.WifiHandler;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;

public class HomeScreen {

    // ── Dimensiones de tarjeta ─────────────────────────────────────────────
    private static final int CARD_W        = 320;
    private static final int CARD_H        = 140;
    private static final int CARD_START_Y  = 60;
    private static final int BTN_GAP       = 8;

    // ── Estado ─────────────────────────────────────────────────────────────
    private PanelUI.DeviceInfo activeDevice;
    private long  connectedAt;

    private EditBox baudBox;
    private EditBox terminalBox;

    // Latencia (solo Wi-Fi)
    private volatile int currentPingMs = -1;
    private Timer pingTimer;

    // ══════════════════════════════════════════════════════════════════════
    //  CICLO DE VIDA
    // ══════════════════════════════════════════════════════════════════════

    public void init(PanelUI panel, int screenWidth, int screenHeight, PanelUI.DeviceInfo device) {
        this.activeDevice  = device;
        this.currentPingMs = -1;
        this.connectedAt   = System.currentTimeMillis();

        int navWidth = NavBar.getNavBarWidth(screenWidth);
        int cardX    = navWidth + 30;

        int btnY = CARD_START_Y + CARD_H + BTN_GAP + 4;
        panel.addWidget(new IconTextButton(
                cardX, btnY, 156, 24, SpriteIcon.DISCONNECT,
                Component.literal("Desconectar Placa"),
                (btn) -> panel.disconnectDevice(),
                0xffe91e63, 0xffba184f, 0xffffffff
        ));

        // ── Configuraciones USB (Baud Rate) ──────────────────────────────
        if (activeDevice != null && "USB".equals(activeDevice.tipo)) {
            baudBox = new EditBox(Minecraft.getInstance().font, cardX + 165, btnY, 65, 24, Component.literal("Bauds"));
            baudBox.setValue(String.valueOf(ConnectionManager.getSerial().getBaudRate()));
            baudBox.setTextColor(0xFFE0E0E0);
            panel.addWidget(baudBox);

            SolidButton applyBaudBtn = SolidButton.primary(cardX + 235, btnY, 85, 24, Component.literal("Aplicar"), btn -> {
                try {
                    int newBaud = Integer.parseInt(baudBox.getValue());
                    String port = ConnectionManager.getSerial().getPortName();
                    ConnectionManager.getSerial().disconnect();
                    Component result = ConnectionManager.getSerial().connect(port, newBaud);
                    if (Minecraft.getInstance().player != null) {
                        Minecraft.getInstance().player.displayClientMessage(result, false);
                    }
                } catch(Exception ignored) {}
            });
            panel.addWidget(applyBaudBtn);
        }

        // ── Consola / Terminal Bidireccional ─────────────────────────────
        if (activeDevice != null) {
            int termY = btnY + 35;
            terminalBox = new EditBox(Minecraft.getInstance().font, cardX, termY + 110, CARD_W - 85, 20, Component.literal("Comando"));
            terminalBox.setMaxLength(128);
            terminalBox.setTextColor(0xFFFFFFFF);
            panel.addWidget(terminalBox);

            SolidButton sendBtn = SolidButton.success(cardX + CARD_W - 80, termY + 110, 80, 20, Component.literal("Enviar"), btn -> {
                String txt = terminalBox.getValue();
                if (!txt.isEmpty()) {
                    ConnectionManager.sendMessageToBoard(txt);
                    terminalBox.setValue("");
                }
            });
            panel.addWidget(sendBtn);
        }

        if (activeDevice != null && "WIFI".equals(activeDevice.tipo)) {
            iniciarMedidorLatencia();
        }
    }

    public void onClose() {
        detenerPing();
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LATENCIA
    // ══════════════════════════════════════════════════════════════════════

    private void iniciarMedidorLatencia() {
        detenerPing();
        pingTimer = new Timer(true);

        String[] partes = activeDevice.direccion.split(":");
        if (partes.length < 2) return;
        String ip    = partes[0];
        int    puerto = Integer.parseInt(partes[1]);

        pingTimer.scheduleAtFixedRate(new TimerTask() {
            @Override public void run() {
                long t0 = System.currentTimeMillis();
                try {
                    if (InetAddress.getByName(ip).isReachable(1000)) {
                        currentPingMs = (int)(System.currentTimeMillis() - t0);
                        return;
                    }
                } catch (Exception ignored) {}
                t0 = System.currentTimeMillis();
                try (Socket s = new Socket()) {
                    s.connect(new InetSocketAddress(ip, puerto), 1000);
                    currentPingMs = (int)(System.currentTimeMillis() - t0);
                } catch (java.net.ConnectException e) {
                    currentPingMs = (int)(System.currentTimeMillis() - t0);
                } catch (Exception e) {
                    currentPingMs = 999;
                }
            }
        }, 0, 2000);
    }

    private void detenerPing() {
        if (pingTimer != null) { pingTimer.cancel(); pingTimer = null; }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RENDER
    // ══════════════════════════════════════════════════════════════════════

    public void render(GuiGraphics gui, int mouseX, int mouseY, Font font, int width, int height) {
        int navWidth = NavBar.getNavBarWidth(width);
        int x        = navWidth + 30;

        float scale = 1.8f;
        gui.pose().pushMatrix();
        gui.pose().scale(scale, scale);
        gui.drawString(font, "INICIO", (int)(x / scale), (int)(22 / scale), 0xFFE91E63, false);
        gui.drawString(font, "PANEL DE CONTROL", (int)(x / scale) + font.width("INICIO") + 4, (int)(22 / scale), 0xFF000000, false);
        gui.pose().popMatrix();

        if (activeDevice == null) return;

        boolean isWifi = "WIFI".equals(activeDevice.tipo);
        int cy = CARD_START_Y;

        gui.fill(x + 3, cy + CARD_H + 1, x + CARD_W + 3, cy + CARD_H + 3, 0x22000000);
        gui.fill(x, cy, x + CARD_W, cy + CARD_H, 0xFFFFFFFF);
        gui.fill(x, cy + CARD_H, x + CARD_W, cy + CARD_H + 2, 0xFFE0E0E0);

        int headerBg = isWifi ? 0xFFE3F2FD : 0xFFF1F8E9;
        gui.fill(x, cy, x + CARD_W, cy + 30, headerBg);

        gui.fill(x + 12, cy + 12, x + 18, cy + 18, 0xFF4CAF50);

        int badgeW = font.width("CONECTADO") + 8;
        gui.fill(x + 24, cy + 8, x + 24 + badgeW, cy + 22, 0xFFE8F5E9);
        gui.drawString(font, "CONECTADO", x + 28, cy + 11, 0xFF2E7D32, false);

        int platX   = x + 24 + badgeW + 6;
        int platW   = font.width(activeDevice.plataforma) + 8;
        int platBg  = isWifi ? 0xFFBBDEFB : 0xFFFFF9C4;
        int platTxt = isWifi ? 0xFF1565C0 : 0xFF827717;
        gui.fill(platX, cy + 8, platX + platW, cy + 22, platBg);
        gui.drawString(font, activeDevice.plataforma, platX + 4, cy + 11, platTxt, false);

        int tipoX   = platX + platW + 6;
        int tipoBg  = isWifi ? 0xFFBBDEFB : 0xFFF5F5F5;
        int tipoTxt = isWifi ? 0xFF1565C0 : 0xFF424242;
        int tipoW   = font.width(activeDevice.tipo) + 8;
        gui.fill(tipoX, cy + 8, tipoX + tipoW, cy + 22, tipoBg);
        gui.drawString(font, activeDevice.tipo, tipoX + 4, cy + 11, tipoTxt, false);

        gui.drawString(font, activeDevice.nombre, x + 12, cy + 36, 0xFF212121, false);
        gui.fill(x + 10, cy + 50, x + CARD_W - 10, cy + 51, 0xFFE0E0E0);

        int rowY = cy + 58;

        if (isWifi) {
            renderFila(gui, font, x + 12, rowY, "Arduino IP", SerialCraftClient.wifiIp.isEmpty() ? "—" : SerialCraftClient.wifiIp, 0xFF212121);
            renderFila(gui, font, x + 12, rowY + 13, "Servidor PC", getLocalIp() + ":8080", 0xFF1976D2);

            String pingTxt  = currentPingMs == -1 ? "Midiendo..." : currentPingMs >= 999 ? "Sin respuesta" : currentPingMs + " ms";
            int pingColor = currentPingMs > 0 && currentPingMs < 999 ? (currentPingMs <= 50 ? 0xFF4CAF50 : currentPingMs <= 150 ? 0xFFFFC107 : 0xFFFF5252) : 0xFF9E9E9E;
            renderFila(gui, font, x + 12, rowY + 26, "Latencia", pingTxt, pingColor);

            WifiHandler.State state = ConnectionManager.getWifi().getState();
            String stateStr  = state == WifiHandler.State.CONNECTED ? "Activo — placa conectada" : state == WifiHandler.State.LISTENING ? "Escuchando en puerto 8080" : "Servidor detenido";
            int stateColor = state == WifiHandler.State.CONNECTED ? 0xFF4CAF50 : state == WifiHandler.State.LISTENING ? 0xFF2196F3 : 0xFF9E9E9E;
            renderFila(gui, font, x + 12, rowY + 39, "Servidor", stateStr, stateColor);
        } else {
            renderFila(gui, font, x + 12, rowY, "Puerto", activeDevice.direccion, 0xFF212121);
            renderFila(gui, font, x + 12, rowY + 13, "Baud Rate", ConnectionManager.getSerial().getBaudRate() + " bps", 0xFF212121);
            renderFila(gui, font, x + 12, rowY + 26, "Protocolo", "Serial USB", 0xFF212121);
            renderFila(gui, font, x + 12, rowY + 39, "Latencia", "Local (< 1 ms)", 0xFF4CAF50);
        }

        gui.fill(x + 10, cy + 112, x + CARD_W - 10, cy + 113, 0xFFE0E0E0);

        long elapsed = (System.currentTimeMillis() - connectedAt) / 1000;
        gui.drawString(font, "Conectado hace " + formatTiempo(elapsed), x + 12, cy + 118, 0xFF9E9E9E, false);

        // ── Renderizado del Terminal Bidireccional ────────────────────────
        int btnY = cy + CARD_H + BTN_GAP + 4;
        int termY = btnY + 35;

        gui.drawString(font, "Terminal / Comunicación Bidireccional", x, termY, 0xFF212121, false);
        gui.fill(x, termY + 12, x + CARD_W, termY + 105, 0xFF1A1A1A); // Fondo consola

        int textY = termY + 18;
        for (String msg : ConnectionManager.messageHistory) {
            int color = msg.startsWith("TX:") ? 0xFF4CAF50 : (msg.startsWith("RX:") ? 0xFF2196F3 : 0xFFF44336);
            gui.drawString(font, msg, x + 6, textY, color, false);
            textY += 10;
        }
    }

    private static void renderFila(GuiGraphics gui, Font font, int x, int y, String label, String value, int valueColor) {
        gui.drawString(font, label, x, y, 0xFF757575, false);
        gui.drawString(font, value, x + 93, y, valueColor, false);
    }

    private static String formatTiempo(long segundos) {
        if (segundos < 60) return segundos + "s";
        if (segundos < 3600) return (segundos / 60) + "m " + (segundos % 60) + "s";
        return (segundos / 3600) + "h " + ((segundos % 3600) / 60) + "m";
    }

    private static String getLocalIp() {
        try {
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface iface = ifaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;
                Enumeration<InetAddress> addrs = iface.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172."))
                            return ip;
                    }
                }
            }
        } catch (Exception ignored) {}
        return "127.0.0.1";
    }
}