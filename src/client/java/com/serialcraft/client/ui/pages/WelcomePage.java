package com.serialcraft.client.ui.pages;

import com.fazecast.jSerialComm.SerialPort;
import com.serialcraft.SerialCraft;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.connection.WifiHandler;
import com.serialcraft.screen.PanelUI;
import com.serialcraft.util.NetUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla de bienvenida: elegir transporte y conectar.
 */
public class WelcomePage implements Page {

    private static final Identifier LOGO_TEXTURE =
            Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "textures/gui/logo-txt.png");

    private static final int LOGO_SRC_W = 779;
    private static final int LOGO_SRC_H = 261;
    private static final int LOGO_WIDTH = 200;
    private static final int LOGO_Y     = 20;
    private static final int CARD_WIDTH = 340;

    /** Baudios usados al conectar por USB desde esta pantalla. */
    private static final int DEFAULT_USB_BAUD = 9600;

    private final List<PanelUI.DeviceInfo> devices = new ArrayList<>();

    private PanelUI panel;
    private int screenWidth;
    private int listStartY;
    private String hostIp = "";

    private static final SecureRandom RANDOM = new SecureRandom();

    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void init(PanelUI panelUi, int width, int height) {
        this.panel       = panelUi;
        this.screenWidth = width;

        int logoHeight = (LOGO_WIDTH * LOGO_SRC_H) / LOGO_SRC_W;
        this.listStartY = LOGO_Y + logoHeight + 55;

        int startX = (width - CARD_WIDTH) / 2;
        WifiHandler wifi = ConnectionManager.getWifi();
        boolean serverUp = wifi.isServerRunning();

        panelUi.addWidget(new IconTextButton(
                startX, listStartY - 28, 162, 22, SpriteIcon.WIFI,
                Component.translatable(serverUp ? "gui.serialcraft.welcome.wifi_stop"
                                                : "gui.serialcraft.welcome.wifi_start"),
                btn -> toggleWifiServer(),
                serverUp ? 0xFF388E3C : UiTheme.INFO,
                serverUp ? 0xFF1B5E20 : 0xFF1976D2,
                UiTheme.TEXT_INVERSE));

        panelUi.addWidget(new IconTextButton(
                startX + 168, listStartY - 28, 162, 22, SpriteIcon.USB,
                Component.translatable("gui.serialcraft.welcome.scan_usb"),
                btn -> { scanUsbPorts(); panelUi.refreshWelcome(); },
                0xFF455A64, UiTheme.TAB_INACTIVE_BG, UiTheme.TEXT_INVERSE));

        scanUsbPorts();
        buildDeviceButtons(panelUi, width);
    }

    @Override
    public void tick() {
        // Auto-conectar cuando una placa completa el handshake Wi-Fi.
        if (panel == null || PanelUI.getSelectedDevice() != null) return;

        WifiHandler wifi = ConnectionManager.getWifi();
        if (wifi.getState() != WifiHandler.State.CONNECTED) return;

        String ip = wifi.getRemoteIp();
        panel.connectDevice(new PanelUI.DeviceInfo(
                Component.translatable("gui.serialcraft.welcome.wifi_board", ip).getString(),
                ip + ":" + WifiHandler.DEFAULT_PORT,
                "WIFI", "Wi-Fi",
                () -> {}));
    }

    // ══════════════════════════════════════════════════════════════════════

    private void toggleWifiServer() {
        WifiHandler wifi = ConnectionManager.getWifi();
        if (wifi.isServerRunning()) {
            wifi.disconnect();
            hostIp = "";
        } else {
            hostIp = NetUtils.findLocalIpv4();
            // Token de emparejamiento nuevo en cada arranque. Se muestra en
            // pantalla para que el jugador lo copie a su sketch. Corto a
            // proposito: tiene que caber en una linea del monitor serie.
            wifi.startServer(WifiHandler.DEFAULT_PORT, generateToken());
        }
        if (panel != null) panel.refreshWelcome();
    }

    private static String generateToken() {
        final String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // sin caracteres ambiguos
        StringBuilder token = new StringBuilder(6);
        for (int i = 0; i < 6; i++) token.append(alphabet.charAt(RANDOM.nextInt(alphabet.length())));
        return token.toString();
    }

    private void scanUsbPorts() {
        devices.removeIf(device -> "USB".equals(device.type()));
        for (SerialPort port : SerialPort.getCommPorts()) {
            final String systemName = port.getSystemPortName();
            String label    = describeBoard(port);
            String platform = platformOf(port);

            devices.add(new PanelUI.DeviceInfo(
                    label, systemName, "USB", platform,
                    () -> ConnectionManager.getSerial().connect(systemName, DEFAULT_USB_BAUD)));
        }
    }

    private void buildDeviceButtons(PanelUI panelUi, int width) {
        int x = (width - CARD_WIDTH) / 2;
        int y = listStartY + 10;

        for (PanelUI.DeviceInfo device : devices) {
            final PanelUI.DeviceInfo target = device;
            panelUi.addWidget(new IconTextButton(
                    x + 232, y + 14, 88, 20, SpriteIcon.CONNECT,
                    Component.translatable("gui.serialcraft.welcome.connect"),
                    btn -> panelUi.connectDevice(target),
                    0xFF4BAD00, 0xFF1E9400, UiTheme.TEXT_INVERSE));
            y += UiTheme.CARD_ROW_HEIGHT;
        }
    }

    /**
     * Identifica la placa por su Vendor ID USB.
     *
     * Los nombres devueltos son claves de traduccion, no cadenas en espanol.
     * En el original "Arduino Genérico (CH340)" y "Dispositivo Serial
     * Desconocido" estaban escritos en el codigo Java y no se traducian nunca.
     */
    private static String describeBoard(SerialPort port) {
        String key = switch (port.getVendorID()) {
            case 0x2341, 0x2A03 -> "gui.serialcraft.board.arduino_official";
            case 0x1A86         -> "gui.serialcraft.board.ch340";
            case 0x10C4         -> "gui.serialcraft.board.cp2102";
            case 0x0403         -> "gui.serialcraft.board.ftdi";
            default             -> null;
        };
        if (key != null) return Component.translatable(key).getString();

        String description = port.getDescriptivePortName();
        return (description == null || description.isBlank() || description.contains("Generic"))
                ? Component.translatable("gui.serialcraft.board.unknown").getString()
                : description;
    }

    private static String platformOf(SerialPort port) {
        return switch (port.getVendorID()) {
            case 0x2341, 0x2A03 -> "Arduino";
            case 0x10C4         -> "ESP32";
            default             -> Component.translatable("gui.serialcraft.board.generic").getString();
        };
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RENDER
    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, Font font, int width, int height) {
        int logoHeight = (LOGO_WIDTH * LOGO_SRC_H) / LOGO_SRC_W;
        int logoX      = (width - LOGO_WIDTH) / 2;

        gui.fill(0, 0, width, logoHeight + 35, UiTheme.BG_NAV);
        gui.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE,
                logoX, LOGO_Y, 0, 0, LOGO_WIDTH, logoHeight,
                LOGO_SRC_W, LOGO_SRC_H, LOGO_SRC_W, LOGO_SRC_H);

        Component subtitle = Component.translatable("gui.serialcraft.welcome.subtitle");
        int subtitleY = LOGO_Y + logoHeight + 3;
        gui.drawString(font, subtitle,
                (width - font.width(subtitle)) / 2, subtitleY, 0xFFCCE5F5, false);

        renderWifiStatus(gui, font, width, subtitleY + 14);
        renderDeviceCards(gui, font, width);
    }

    private void renderWifiStatus(GuiGraphics gui, Font font, int width, int y) {
        WifiHandler wifi = ConnectionManager.getWifi();
        if (wifi.getState() == WifiHandler.State.STOPPED) return;

        Component status;
        int color;

        if (wifi.getState() == WifiHandler.State.CONNECTED) {
            status = Component.translatable("gui.serialcraft.welcome.wifi_connected", wifi.getRemoteIp());
            color  = UiTheme.OK;
        } else {
            String ip = hostIp.isEmpty() ? NetUtils.FALLBACK_IP : hostIp;
            status = Component.translatable("gui.serialcraft.welcome.wifi_listening",
                    ip, WifiHandler.DEFAULT_PORT, wifi.getPairingToken());
            color  = 0xFF90CAF9;
        }
        gui.drawString(font, status, (width - font.width(status)) / 2, y, color, false);
    }

    private void renderDeviceCards(GuiGraphics gui, Font font, int width) {
        int x = (width - CARD_WIDTH) / 2;
        int y = listStartY + 10;

        if (devices.isEmpty()) {
            gui.drawString(font, Component.translatable("gui.serialcraft.welcome.no_usb"),
                    x, y + 8, 0xFF888888, false);
            gui.drawString(font, Component.translatable("gui.serialcraft.welcome.no_usb_hint"),
                    x, y + 22, 0xFF888888, false);
            return;
        }

        for (PanelUI.DeviceInfo device : devices) {
            UiDraw.card(gui, x, y, CARD_WIDTH - 5, UiTheme.CARD_HEIGHT);

            boolean wifi = device.isWifi();
            UiDraw.badge(gui, font, x + 10, y + 14, device.type(),
                    wifi ? UiTheme.INFO_BG   : UiTheme.NEUTRAL_BG,
                    wifi ? UiTheme.INFO_DARK : UiTheme.NEUTRAL_TX);

            gui.drawString(font, font.plainSubstrByWidth(device.name(), 175),
                    x + 50, y + 13, UiTheme.TEXT_PRIMARY, false);
            gui.drawString(font, device.address(), x + 50, y + 27, UiTheme.TEXT_SECONDARY, false);

            y += UiTheme.CARD_ROW_HEIGHT;
        }
    }
}
