package com.serialcraft.client.ui.pages;

import com.fazecast.jSerialComm.SerialPort;
import com.serialcraft.SerialCraftClient;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.connection.WifiHandler;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WelcomeScreen {

    // ── Constantes ─────────────────────────────────────────────────────────
    private static final Identifier LOGO_TEXTURE =
            Identifier.fromNamespaceAndPath("serialcraft", "textures/gui/logo-txt.png");
    private static final int WIFI_SERVER_PORT = 8080;

    // ── Estado ─────────────────────────────────────────────────────────────
    private final List<PanelUI.DeviceInfo> dispositivos = new CopyOnWriteArrayList<>();
    private final List<IconTextButton>     cardButtons  = new CopyOnWriteArrayList<>();

    private PanelUI panelRef;
    private int     screenWidthRef;
    private int     layoutStartY;

    /** IP LAN del PC, calculada al pulsar el botón Wi-Fi. */
    private String localIpText = "";

    private volatile boolean needsWidgetUpdate = false;

    // ══════════════════════════════════════════════════════════════════════
    //  CICLO DE VIDA
    // ══════════════════════════════════════════════════════════════════════

    public void init(PanelUI panel, int screenWidth, int screenHeight) {
        this.panelRef       = panel;
        this.screenWidthRef = screenWidth;

        for (IconTextButton btn : cardButtons) { btn.visible = false; btn.active = false; }
        cardButtons.clear();
        dispositivos.clear();

        int logoWidth  = 200;
        int logoHeight = (logoWidth * 261) / 779;
        this.layoutStartY = 20 + logoHeight + 45;

        int cardWidth = 340;
        int startX    = (screenWidth - cardWidth) / 2;

        // ── Botón de servidor Wi-Fi ────────────────────────────────────────
        WifiHandler wifi    = ConnectionManager.getWifi();
        boolean     running = wifi.isServerRunning();

        IconTextButton wifiBtn = new IconTextButton(
                startX, layoutStartY - 10, 165, 24, SpriteIcon.WIFI,
                Component.literal(running ? "Servidor Wi-Fi Activo" : "Iniciar Servidor Wi-Fi"),
                (btn) -> toggleWifiServer(btn),
                running ? 0xff388e3c : 0xff2196f3,
                running ? 0xff1b5e20 : 0xff1976d2,
                0xffffffff
        );
        panel.addWidget(wifiBtn);

        // ── Escaneo inicial de puertos USB ────────────────────────────────
        escanearUSB();
    }

    public void tick() {
        if (needsWidgetUpdate) {
            rebuildCardButtons();
            needsWidgetUpdate = false;
        }

        // Auto-conectar cuando el Arduino establece la conexión TCP
        if (panelRef != null && PanelUI.currentConnectedDevice == null) {
            WifiHandler wifi = ConnectionManager.getWifi();
            if (wifi.getState() == WifiHandler.State.CONNECTED) {
                String ip = SerialCraftClient.wifiIp;
                PanelUI.DeviceInfo autoDevice = new PanelUI.DeviceInfo(
                        "Arduino Wi-Fi  (" + ip + ")",
                        ip + ":" + WIFI_SERVER_PORT,
                        "WIFI", "Wi-Fi",
                        () -> {}
                );
                panelRef.connectDevice(autoDevice);
            }
        }
    }

    public void onClose() { /* sin recursos propios que liberar */ }

    // ══════════════════════════════════════════════════════════════════════
    //  LÓGICA INTERNA
    // ══════════════════════════════════════════════════════════════════════

    /**
     /**
     * Alterna el servidor Wi-Fi entre STOPPED y LISTENING.
     * Llama a rebuildWelcome() para reconstruir el botón con color/texto actualizados.
     * NO realiza ningún escaneo de red.
     */
    private void toggleWifiServer(IconTextButton btn) {
        WifiHandler wifi = ConnectionManager.getWifi();
        if (wifi.isServerRunning()) {
            SerialCraftClient.detenerServidorWifi();
            localIpText = "";
        } else {
            localIpText = calcularIpLocal();
            SerialCraftClient.iniciarServidorWifi(WIFI_SERVER_PORT);
        }
        // Reconstruye la pantalla para que el botón refleje el nuevo estado
        panelRef.rebuildWelcome();
    }

    private void escanearUSB() {
        dispositivos.removeIf(d -> "USB".equals(d.tipo));
        for (SerialPort port : SerialPort.getCommPorts()) {
            String sysName      = port.getSystemPortName();
            String friendlyName = getFriendlyBoardName(port);
            String plat = friendlyName.contains("ESP32")    ? "ESP32"
                    : friendlyName.contains("Arduino")  ? "Arduino"
                    : "Genérico";

            dispositivos.add(new PanelUI.DeviceInfo(
                    friendlyName, sysName, "USB", plat,
                    () -> SerialCraftClient.conectar(sysName, 9600)
            ));
        }
        needsWidgetUpdate = true;
    }

    private void rebuildCardButtons() {
        for (IconTextButton btn : cardButtons) { btn.visible = false; btn.active = false; }
        cardButtons.clear();

        int cardWidth = 340;
        int x     = (screenWidthRef - cardWidth) / 2;
        int cardY = layoutStartY + 10;

        for (PanelUI.DeviceInfo dev : dispositivos) {
            IconTextButton connectBtn = new IconTextButton(
                    x + 230, cardY + 22, 90, 24, SpriteIcon.CONNECT,
                    Component.literal("Conectar"),
                    (btn) -> panelRef.connectDevice(dev),
                    0xff4bad00, 0xff1e9400, 0xffffffff
            );
            panelRef.addWidget(connectBtn);
            cardButtons.add(connectBtn);
            cardY += 55;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  HELPERS
    // ══════════════════════════════════════════════════════════════════════

    private String calcularIpLocal() {
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
                        if ((ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172."))
                                && !ip.startsWith("100.")) {
                            return ip;
                        }
                    }
                }
            }
        } catch (Exception ignored) {}
        return "desconocida";
    }

    private String getFriendlyBoardName(SerialPort port) {
        int vid = port.getVendorID();
        if (vid == 0x2341 || vid == 0x2A03) return "Arduino UNO (Oficial)";
        if (vid == 0x1A86)                   return "Arduino Genérico (CH340)";
        if (vid == 0x10C4)                   return "Placa Genérica (CP2102)";
        String desc = port.getDescriptivePortName();
        if (desc == null || desc.isBlank() || desc.contains("Generic")) return "Dispositivo Serial Desconocido";
        return desc;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RENDER
    // ══════════════════════════════════════════════════════════════════════

    public void render(GuiGraphics gui, int mouseX, int mouseY, Font font, int width, int height) {
        if (needsWidgetUpdate) {
            rebuildCardButtons();
            needsWidgetUpdate = false;
        }

        // ── Header ────────────────────────────────────────────────────────
        int logoWidth  = 200;
        int logoHeight = (logoWidth * 261) / 779;
        int logoX      = (width - logoWidth) / 2;
        int logoY      = 20;

        gui.fill(0, 0, width, logoHeight + 30, 0xff4995b6);
        gui.blit(RenderPipelines.GUI_TEXTURED,
                LOGO_TEXTURE,
                logoX, logoY, 0, 0,
                logoWidth, logoHeight,
                779, 261, 779, 261);

        int textY    = logoY + logoHeight + 24;
        String subT  = "Conecta tu hardware y entra al panel";
        gui.drawString(font, subT, (width - font.width(subT)) / 2, textY, 0xFF757575, false);

        // ── Estado del servidor Wi-Fi ─────────────────────────────────────
        WifiHandler.State wifiState = ConnectionManager.getWifi().getState();
        if (wifiState != WifiHandler.State.STOPPED) {
            String statusText;
            int    statusColor;
            if (wifiState == WifiHandler.State.CONNECTED) {
                statusText  = "● Arduino conectado — " + SerialCraftClient.wifiIp;
                statusColor = 0xFF4CAF50;
            } else {
                String ip  = localIpText.isEmpty() ? "tu IP local" : localIpText;
                statusText = "◌ Servidor activo — configura tu Arduino:  " + ip + ":" + WIFI_SERVER_PORT;
                statusColor = 0xFF2196F3;
            }
            gui.drawString(font, statusText,
                    (width - font.width(statusText)) / 2, textY + 14, statusColor, false);
        }

        // ── Tarjetas USB ──────────────────────────────────────────────────
        int cardWidth = 340;
        int x         = (width - cardWidth) / 2;
        int cardY     = layoutStartY + 20;

        if (dispositivos.isEmpty()) {
            gui.drawString(font,
                    "No se detectaron placas USB. Conecta una o inicia el servidor Wi-Fi.",
                    x, cardY + 10, 0xff888888, false);
        } else {
            for (PanelUI.DeviceInfo dev : dispositivos) {
                gui.fill(x, cardY,      x + 335, cardY + 48, 0xffffffff);
                gui.fill(x, cardY + 48, x + 335, cardY + 50, 0xffe0e0e0);

                int tagBg  = "WIFI".equals(dev.tipo) ? 0xffbbdefb : 0xfff5f5f5;
                int tagTxt = "WIFI".equals(dev.tipo) ? 0xff1565c0 : 0xff424242;
                gui.fill(x + 10, cardY + 14, x + 42, cardY + 28, tagBg);
                gui.drawString(font, dev.tipo,      x + 14, cardY + 17, tagTxt,    false);
                gui.drawString(font, dev.nombre,    x + 50, cardY + 14, 0xff212121, false);
                gui.drawString(font, dev.direccion, x + 50, cardY + 27, 0xff757575, false);

                cardY += 55;
            }
        }
    }
}