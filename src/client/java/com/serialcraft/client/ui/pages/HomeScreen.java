package com.serialcraft.client.ui.pages;

import com.serialcraft.SerialCraftClient;
import com.serialcraft.client.ui.NavBar;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.connection.WifiHandler;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
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

    // ── Estado ─────────────────────────────────────────────────────────────
    private PanelUI.DeviceInfo activeDevice;
    private int   layoutStartY;
    private long  connectedAt;     // System.currentTimeMillis() al conectar

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
        this.layoutStartY  = 60;

        int navWidth = NavBar.getNavBarWidth(screenWidth);
        int cardX    = navWidth + 30;

        // Botón de desconexión: debajo de la tarjeta de información
        int btnY = layoutStartY + 148;
        IconTextButton disconnectBtn = new IconTextButton(
                cardX, btnY, 150, 26, SpriteIcon.DISCONNECT,
                Component.literal("Desconectar Placa"),
                (btn) -> panel.disconnectDevice(),
                0xffe91e63, 0xffba184f, 0xffffffff
        );
        panel.addWidget(disconnectBtn);

        // Medidor de latencia solo para Wi-Fi
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
        String ip     = partes[0];
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

        // ── Título de sección ─────────────────────────────────────────────
        float scale = 1.8f;
        gui.pose().pushMatrix();
        gui.pose().scale(scale, scale);
        gui.drawString(font, "INICIO",           (int)(x / scale),                             (int)(22 / scale), 0xFFE91E63, false);
        gui.drawString(font, "PANEL DE CONTROL", (int)(x / scale) + font.width("INICIO") + 4, (int)(22 / scale), 0xFF000000, false);
        gui.pose().popMatrix();

        if (activeDevice == null) return;

        boolean isWifi = "WIFI".equals(activeDevice.tipo);

        // ══════════════════════════════════════════════════════════════════
        //  TARJETA UNIFICADA DE DISPOSITIVO
        //  Altura fija: 140px — contiene TODA la info relevante de la placa
        // ══════════════════════════════════════════════════════════════════
        int cardW = 310;
        int cardH = 140;
        int cy    = layoutStartY;

        // Fondo + borde inferior
        gui.fill(x,        cy,         x + cardW, cy + cardH,     0xFFFFFFFF);
        gui.fill(x,        cy + cardH, x + cardW, cy + cardH + 2, 0xFFE0E0E0);

        // ── HEADER de la tarjeta ──────────────────────────────────────────
        // Franja de color según tipo de conexión
        int headerBg = isWifi ? 0xFFE3F2FD : 0xFFF1F8E9;
        gui.fill(x, cy, x + cardW, cy + 30, headerBg);

        // LED de estado (verde = conectado)
        gui.fill(x + 12, cy + 12, x + 18, cy + 18, 0xFF4CAF50);

        // Badge "CONECTADO"
        int badgeW = font.width("CONECTADO") + 8;
        gui.fill(x + 24, cy + 8, x + 24 + badgeW, cy + 22, 0xFFE8F5E9);
        gui.drawString(font, "CONECTADO", x + 28, cy + 11, 0xFF2E7D32, false);

        // Badge de plataforma (Arduino / ESP32 / Wi-Fi)
        int platX = x + 24 + badgeW + 6;
        int platW = font.width(activeDevice.plataforma) + 8;
        int platBg  = isWifi ? 0xFFBBDEFB : 0xFFFFF9C4;
        int platTxt = isWifi ? 0xFF1565C0 : 0xFF827717;
        gui.fill(platX, cy + 8, platX + platW, cy + 22, platBg);
        gui.drawString(font, activeDevice.plataforma, platX + 4, cy + 11, platTxt, false);

        // Badge de tipo (USB / WIFI)
        int tipoX = platX + platW + 6;
        int tipoBg  = isWifi ? 0xFFBBDEFB : 0xFFF5F5F5;
        int tipoTxt = isWifi ? 0xFF1565C0 : 0xFF424242;
        int tipoW   = font.width(activeDevice.tipo) + 8;
        gui.fill(tipoX, cy + 8, tipoX + tipoW, cy + 22, tipoBg);
        gui.drawString(font, activeDevice.tipo, tipoX + 4, cy + 11, tipoTxt, false);

        // ── Nombre del dispositivo ────────────────────────────────────────
        gui.drawString(font, activeDevice.nombre, x + 12, cy + 36, 0xFF212121, false);

        // ── Separador ─────────────────────────────────────────────────────
        gui.fill(x + 10, cy + 50, x + cardW - 10, cy + 51, 0xFFE0E0E0);

        // ── Filas de detalle ──────────────────────────────────────────────
        int rowY = cy + 58;
        int col1 = x + 12;
        int col2 = x + 105;

        if (isWifi) {
            renderFila(gui, font, col1, rowY,      "Arduino IP",  SerialCraftClient.wifiIp.isEmpty() ? "—" : SerialCraftClient.wifiIp, 0xFF212121);
            renderFila(gui, font, col1, rowY + 13, "Servidor PC", getLocalIp() + ":8080", 0xFF1976D2);

            // Latencia con color semáforo
            String pingTxt   = currentPingMs == -1 ? "Midiendo..." : (currentPingMs >= 999 ? "Sin respuesta" : currentPingMs + " ms");
            int    pingColor  = currentPingMs > 0 && currentPingMs < 999
                    ? (currentPingMs <= 50 ? 0xFF4CAF50 : currentPingMs <= 150 ? 0xFFFFC107 : 0xFFFF5252)
                    : 0xFF9E9E9E;
            renderFila(gui, font, col1, rowY + 26, "Latencia", pingTxt, pingColor);

            // Estado del servidor
            WifiHandler.State state = ConnectionManager.getWifi().getState();
            String stateStr   = state == WifiHandler.State.CONNECTED  ? "Activo — placa conectada"
                    : state == WifiHandler.State.LISTENING   ? "Escuchando en puerto 8080"
                    : "Servidor detenido";
            int stateColor    = state == WifiHandler.State.CONNECTED  ? 0xFF4CAF50
                    : state == WifiHandler.State.LISTENING   ? 0xFF2196F3
                    : 0xFF9E9E9E;
            renderFila(gui, font, col1, rowY + 39, "Servidor", stateStr, stateColor);

        } else {
            // ── Información USB ───────────────────────────────────────────
            renderFila(gui, font, col1, rowY,      "Puerto",     activeDevice.direccion, 0xFF212121);
            renderFila(gui, font, col1, rowY + 13, "Baud Rate",  ConnectionManager.getSerial().getBaudRate() + " bps", 0xFF212121);
            renderFila(gui, font, col1, rowY + 26, "Protocolo",  "Serial USB", 0xFF212121);
            renderFila(gui, font, col1, rowY + 39, "Latencia",   "Local (< 1 ms)", 0xFF4CAF50);
        }

        // ── Separador inferior ────────────────────────────────────────────
        gui.fill(x + 10, cy + 112, x + cardW - 10, cy + 113, 0xFFE0E0E0);

        // ── Tiempo de conexión ────────────────────────────────────────────
        long elapsed = (System.currentTimeMillis() - connectedAt) / 1000;
        String tiempoStr = formatTiempo(elapsed);
        gui.drawString(font, "Conectado hace   " + tiempoStr, x + 12, cy + 118, 0xFF9E9E9E, false);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS DE RENDER
    // ══════════════════════════════════════════════════════════════════════

    /** Dibuja una fila etiqueta:valor con colores diferenciados. */
    private static void renderFila(GuiGraphics gui, Font font,
                                   int x, int y, String label, String value, int valueColor) {
        gui.drawString(font, label, x, y, 0xFF757575, false);
        gui.drawString(font, value, x + 93, y, valueColor, false);
    }

    /** Formatea segundos en "Xm Ys" o "Xh Ym". */
    private static String formatTiempo(long segundos) {
        if (segundos < 60)   return segundos + "s";
        if (segundos < 3600) return (segundos / 60) + "m " + (segundos % 60) + "s";
        return (segundos / 3600) + "h " + ((segundos % 3600) / 60) + "m";
    }

    /** Devuelve la IPv4 LAN del PC o "127.0.0.1". */
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