package com.serialcraft.client.ui.pages;

import com.fazecast.jSerialComm.SerialPort;
import com.serialcraft.SerialCraft;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.client.ui.widget.MethodCard;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.connection.WifiHandler;
import com.serialcraft.screen.PanelUI;
import com.serialcraft.util.NetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla de bienvenida: selección de método de conexión (USB / Wi-Fi),
 * panel de configuración con copiado individual de parámetros (IP, Puerto, Token),
 * guía rápida de conexión y redirección automática al conectar con éxito.
 */
public class WelcomePage implements Page {

    private static final Identifier LOGO_TEXTURE =
            Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "textures/gui/logo-txt.png");

    private static final int LOGO_SRC_W = 779;
    private static final int LOGO_SRC_H = 261;
    private static final int LOGO_WIDTH = 190;
    private static final int LOGO_Y     = 14;
    private static final int CARD_WIDTH = 340;

    private static final int METHOD_H     = 42;
    private static final int METHOD_GAP   = 8;
    private static final int WIFI_INFO_H_LISTENING = 96;
    private static final int WIFI_INFO_H_CONNECTED = 34;
    private static final int HELPER_LINK_H = 16;
    private static final int HELPER_PANEL_H = 138;

    /** Baudios usados al conectar por USB desde esta pantalla. */
    private static final int DEFAULT_USB_BAUD = 9600;

    /** Plataformas que ofrece la ayuda de conexion. */
    private static final int PLATFORM_ESP32 = 0;
    private static final int PLATFORM_UNO_Q = 1;
    private static final int PLATFORM_PI    = 2;
    private static final int PLATFORM_COUNT = 3;

    private final List<PanelUI.DeviceInfo> devices = new ArrayList<>();

    private PanelUI panel;
    private String hostIp = "";
    private boolean showHelper = false;
    private int helperPlatform = PLATFORM_ESP32;

    /** Boton de "Copiar" activo y hasta cuando debe seguir diciendo "Copiado". */
    private IconTextButton pendingCopyButton;
    private Component pendingCopyOriginalLabel;
    private long copyFeedbackUntilMs;

    private static final SecureRandom RANDOM = new SecureRandom();

    // ════════════════════════════════════════════════════════════════════════════
    //  LAYOUT DINAMICO
    // ════════════════════════════════════════════════════════════════════════════

    private record Layout(int logoHeight, int subtitleY, int methodY,
                          int wifiInfoY, int wifiInfoH,
                          int helperLinkY, int helperPanelY, int helperPanelH,
                          int deviceListY) {}

    private Layout layout() {
        int logoHeight = (LOGO_WIDTH * LOGO_SRC_H) / LOGO_SRC_W;
        int subtitleY  = LOGO_Y + logoHeight + 4;
        int methodY    = subtitleY + 18;

        WifiHandler wifi = ConnectionManager.getWifi();
        int wifiInfoH = switch (wifi.getState()) {
            case STOPPED    -> 0;
            case LISTENING  -> WIFI_INFO_H_LISTENING;
            case CONNECTED  -> WIFI_INFO_H_CONNECTED;
        };
        int wifiInfoY = methodY + METHOD_H + 8;

        int helperLinkY  = wifiInfoY + wifiInfoH + (wifiInfoH > 0 ? 8 : 4);
        int helperPanelY = helperLinkY + HELPER_LINK_H + 6;
        int helperPanelH = showHelper ? HELPER_PANEL_H : 0;

        int deviceListY = helperPanelY + (showHelper ? helperPanelH + 10 : 0);

        return new Layout(logoHeight, subtitleY, methodY, wifiInfoY, wifiInfoH,
                helperLinkY, helperPanelY, helperPanelH, deviceListY);
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  INIT
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public void init(PanelUI panelUi, int width, int height) {
        this.panel = panelUi;
        if (ConnectionManager.getWifi().isServerRunning() && (hostIp == null || hostIp.isEmpty())) {
            hostIp = NetUtils.findLocalIpv4();
        }

        Layout l = layout();
        int x = (width - CARD_WIDTH) / 2;

        scanUsbPorts();
        buildMethodCards(panelUi, x, l.methodY());
        if (l.wifiInfoH() > 0) buildWifiInfoWidgets(panelUi, x, l.wifiInfoY(), l.wifiInfoH());
        buildHelperLink(panelUi, x, l.helperLinkY());
        if (showHelper) buildHelperPanel(panelUi, x, l.helperPanelY());
        buildDeviceButtons(panelUi, x, l.deviceListY());
    }

    @Override
    public void tick() {
        // Revertir el boton de "Copiado" pasado el tiempo de gracia.
        if (pendingCopyButton != null && System.currentTimeMillis() > copyFeedbackUntilMs) {
            pendingCopyButton.setMessage(pendingCopyOriginalLabel);
            pendingCopyButton = null;
        }

        // Redirigir automaticamente a HomePage cuando se realice una conexion exitosa (Wi-Fi o USB).
        if (panel == null) return;

        if (ConnectionManager.isAnyConnected()) {
            PanelUI.DeviceInfo dev = PanelUI.getSelectedDevice();
            if (dev != null) {
                panel.connectDevice(dev);
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  TARJETAS DE METODO
    // ════════════════════════════════════════════════════════════════════════════

    private void buildMethodCards(PanelUI panelUi, int x, int y) {
        int cardW = (CARD_WIDTH - METHOD_GAP) / 2;

        long usbCount = devices.stream().filter(d -> "USB".equals(d.type())).count();
        Component usbStatus = usbCount == 0
                ? Component.translatable("gui.serialcraft.welcome.method_usb_empty")
                : Component.translatable("gui.serialcraft.welcome.method_usb_count", usbCount);

        panelUi.addWidget(new MethodCard(x, y, cardW, METHOD_H, SpriteIcon.USB,
                Component.translatable("gui.serialcraft.welcome.method_usb"),
                usbStatus, UiTheme.TEXT_ON_DARK,
                UiTheme.TAB_INACTIVE_BG, UiTheme.TAB_INACTIVE_BORDER,
                () -> { scanUsbPorts(); panel.refreshWelcome(); }));

        WifiHandler wifi = ConnectionManager.getWifi();
        Component wifiStatus;
        int wifiBg;
        int wifiBorder;
        switch (wifi.getState()) {
            case CONNECTED -> {
                wifiStatus = Component.translatable("gui.serialcraft.welcome.method_wifi_connected", wifi.getRemoteIp());
                wifiBg     = UiTheme.OK_DARK;
                wifiBorder = UiTheme.OK;
            }
            case LISTENING -> {
                wifiStatus = Component.translatable("gui.serialcraft.welcome.method_wifi_listening");
                wifiBg     = UiTheme.INFO_DARK;
                wifiBorder = UiTheme.INFO;
            }
            default -> {
                wifiStatus = Component.translatable("gui.serialcraft.welcome.method_wifi_start");
                wifiBg     = UiTheme.TAB_INACTIVE_BG;
                wifiBorder = UiTheme.TAB_INACTIVE_BORDER;
            }
        }

        panelUi.addWidget(new MethodCard(x + cardW + METHOD_GAP, y, cardW, METHOD_H, SpriteIcon.WIFI,
                Component.translatable("gui.serialcraft.welcome.method_wifi"),
                wifiStatus, UiTheme.TEXT_ON_DARK, wifiBg, wifiBorder,
                this::toggleWifiServer));
    }

    private void toggleWifiServer() {
        WifiHandler wifi = ConnectionManager.getWifi();
        if (wifi.isServerRunning()) {
            wifi.disconnect();
            hostIp = "";
        } else {
            hostIp = NetUtils.findLocalIpv4();
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

    // ════════════════════════════════════════════════════════════════════════════
    //  PANEL DE DATOS WI-FI CON COPIADO INDIVIDUAL
    // ════════════════════════════════════════════════════════════════════════════

    private void buildWifiInfoWidgets(PanelUI panelUi, int x, int y, int h) {
        WifiHandler wifi = ConnectionManager.getWifi();

        if (wifi.getState() == WifiHandler.State.LISTENING) {
            int btnW = 54;
            int btnH = 16;
            int btnX = x + CARD_WIDTH - btnW - 8;

            // Boton individual: Copiar Host IP
            panelUi.addWidget(new IconTextButton(
                    btnX, y + 22, btnW, btnH, null,
                    Component.translatable("gui.serialcraft.welcome.copy"),
                    btn -> copyWithFeedback(btn, getEffectiveHostIp()),
                    0xFF37474F, 0xFF455A64, UiTheme.TEXT_INVERSE));

            // Boton individual: Copiar Puerto
            panelUi.addWidget(new IconTextButton(
                    btnX, y + 44, btnW, btnH, null,
                    Component.translatable("gui.serialcraft.welcome.copy"),
                    btn -> copyWithFeedback(btn, String.valueOf(WifiHandler.DEFAULT_PORT)),
                    0xFF37474F, 0xFF455A64, UiTheme.TEXT_INVERSE));

            // Boton individual: Copiar Token
            panelUi.addWidget(new IconTextButton(
                    btnX, y + 66, btnW, btnH, null,
                    Component.translatable("gui.serialcraft.welcome.copy"),
                    btn -> copyWithFeedback(btn, wifi.getPairingToken()),
                    0xFF00695C, 0xFF00897B, UiTheme.TEXT_INVERSE));

        } else if (wifi.getState() == WifiHandler.State.CONNECTED) {
            panelUi.addWidget(new IconTextButton(
                    x + CARD_WIDTH - 92, y + (h - 18) / 2, 86, 18, SpriteIcon.DISCONNECT,
                    Component.translatable("gui.serialcraft.welcome.wifi_disconnect"),
                    btn -> toggleWifiServer(),
                    UiTheme.ERROR_DARK, UiTheme.ERROR));
        }
    }

    private String getEffectiveHostIp() {
        return (hostIp != null && !hostIp.isEmpty()) ? hostIp : NetUtils.findLocalIpv4();
    }

    private String wifiSnippetOneLine(WifiHandler wifi) {
        return getEffectiveHostIp() + ":" + WifiHandler.DEFAULT_PORT + " token:" + wifi.getPairingToken();
    }

    private void renderWifiInfoPanel(GuiGraphicsExtractor gui, Font font, int x, int y, int h) {
        if (h <= 0) return;

        WifiHandler wifi = ConnectionManager.getWifi();

        if (wifi.getState() == WifiHandler.State.LISTENING) {
            // Fondo general de la tarjeta Wi-Fi
            UiDraw.card(gui, x, y, CARD_WIDTH, h);

            // Barra superior de encabezado
            gui.fill(x, y, x + CARD_WIDTH, y + 17, 0xFFE1F5FE);
            gui.fill(x, y + 17, x + CARD_WIDTH, y + 18, 0xFFB3E5FC);

            // Indicador activo (punto verde brillante)
            gui.fill(x + 8, y + 5, x + 14, y + 11, UiTheme.OK);
            gui.text(font, Component.translatable("gui.serialcraft.welcome.wifi_server_title"),
                    x + 18, y + 4, UiTheme.INFO_DARK, false);

            int valBoxX = x + 58;
            int valBoxW = CARD_WIDTH - 58 - 66;

            // Fila 1: Host IP
            UiDraw.badge(gui, font, x + 8, y + 23,
                    Component.translatable("gui.serialcraft.welcome.wifi_host"),
                    0xFFECEFF1, UiTheme.TEXT_SECONDARY);
            gui.fill(valBoxX, y + 22, valBoxX + valBoxW, y + 38, 0xFFF5F5F5);
            gui.outline(valBoxX, y + 22, valBoxW, 16, UiTheme.LINE_SOFT);
            gui.text(font, getEffectiveHostIp(), valBoxX + 6, y + 26, UiTheme.TEXT_PRIMARY, false);

            // Fila 2: Puerto
            UiDraw.badge(gui, font, x + 8, y + 45,
                    Component.translatable("gui.serialcraft.welcome.wifi_port"),
                    0xFFECEFF1, UiTheme.TEXT_SECONDARY);
            gui.fill(valBoxX, y + 44, valBoxX + valBoxW, y + 60, 0xFFF5F5F5);
            gui.outline(valBoxX, y + 44, valBoxW, 16, UiTheme.LINE_SOFT);
            gui.text(font, String.valueOf(WifiHandler.DEFAULT_PORT), valBoxX + 6, y + 48, UiTheme.TEXT_PRIMARY, false);

            // Fila 3: Token
            UiDraw.badge(gui, font, x + 8, y + 67,
                    Component.translatable("gui.serialcraft.welcome.wifi_token"),
                    0xFFE0F2F1, UiTheme.ACCENT_PRIMARY_DARK);
            gui.fill(valBoxX, y + 66, valBoxX + valBoxW, y + 82, 0xFFE8F5E9);
            gui.outline(valBoxX, y + 66, valBoxW, 16, 0xFFA5D6A7);
            gui.text(font, wifi.getPairingToken(), valBoxX + 6, y + 70, UiTheme.OK_DARK, false);

        } else if (wifi.getState() == WifiHandler.State.CONNECTED) {
            UiDraw.card(gui, x, y, CARD_WIDTH, h);
            gui.fill(x, y, x + 4, y + h, UiTheme.OK);

            UiDraw.badge(gui, font, x + 10, y + 9, "WI-FI", UiTheme.OK_BG, UiTheme.OK_DARK);

            Component status = Component.translatable("gui.serialcraft.welcome.wifi_connected", wifi.getRemoteIp());
            gui.text(font, status, x + 54, y + 12, UiTheme.TEXT_PRIMARY, false);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  AYUDA DE CONEXION
    // ════════════════════════════════════════════════════════════════════════════

    private void buildHelperLink(PanelUI panelUi, int x, int y) {
        Component text = Component.translatable(showHelper
                ? "gui.serialcraft.welcome.helper_link_close"
                : "gui.serialcraft.welcome.helper_link_open");
        int linkWidth = Minecraft.getInstance().font.width(text) + 24;

        panelUi.addWidget(new IconTextButton(
                x + (CARD_WIDTH - linkWidth) / 2, y, linkWidth, HELPER_LINK_H, SpriteIcon.QUEST,
                text, btn -> { showHelper = !showHelper; if (panel != null) panel.refreshWelcome(); },
                0x00000000, 0x00000000, UiTheme.INFO));
    }

    private void buildHelperPanel(PanelUI panelUi, int x, int y) {
        int tabW = (CARD_WIDTH - 2 * 4) / PLATFORM_COUNT;
        String[] tabKeys = {
                "gui.serialcraft.welcome.helper_tab_esp32",
                "gui.serialcraft.welcome.helper_tab_uno_q",
                "gui.serialcraft.welcome.helper_tab_pi",
        };
        for (int i = 0; i < PLATFORM_COUNT; i++) {
            boolean active = helperPlatform == i;
            int platform = i;
            panelUi.addWidget(new IconTextButton(
                    x + i * (tabW + 4), y + 6, tabW, 16, null,
                    Component.translatable(tabKeys[i]),
                    btn -> { helperPlatform = platform; if (panel != null) panel.refreshWelcome(); },
                    active ? UiTheme.ACCENT_PRIMARY : UiTheme.TAB_INACTIVE_BG,
                    active ? UiTheme.ACCENT_PRIMARY_DARK : UiTheme.TAB_INACTIVE_BORDER));
        }

        List<String> snippet = helperSnippetLines(helperPlatform);
        int codeBoxY = y + 26 + 14;
        int codeBoxH = snippet.size() * 11 + 8;

        IconTextButton copyBtn = new IconTextButton(
                    x + CARD_WIDTH - 96, codeBoxY + codeBoxH + 6, 96, 16, SpriteIcon.CODE,
                    Component.translatable("gui.serialcraft.welcome.copy_code"),
                    btn -> copyWithFeedback(btn, String.join("\n", snippet)),
                    UiTheme.TAB_INACTIVE_BG, UiTheme.TAB_INACTIVE_BORDER);
        panelUi.addWidget(copyBtn);
    }

    /** Lineas listas para pegar en el sketch/script, con host y token reales
     *  si el servidor Wi-Fi ya esta encendido, o marcadores si no. */
    private List<String> helperSnippetLines(int platform) {
        WifiHandler wifi = ConnectionManager.getWifi();
        boolean live  = wifi.isServerRunning();
        String host   = live ? getEffectiveHostIp()
                : Component.translatable("gui.serialcraft.welcome.helper_placeholder_ip").getString();
        String token  = live ? wifi.getPairingToken()
                : Component.translatable("gui.serialcraft.welcome.helper_placeholder_token").getString();

        return switch (platform) {
            case PLATFORM_ESP32 -> List.of(
                    "const char* host  = \"" + host + "\";",
                    "const uint16_t port = " + WifiHandler.DEFAULT_PORT + ";",
                    "const char* token = \"" + token + "\";");
            case PLATFORM_UNO_Q -> List.of(
                    "HOST = \"" + host + "\"",
                    "PORT = " + WifiHandler.DEFAULT_PORT,
                    "TOKEN = \"" + token + "\"");
            case PLATFORM_PI -> List.of( // Raspberry Pi / Python generico
                    "s = socket.create_connection((\"" + host + "\", " + WifiHandler.DEFAULT_PORT + "))",
                    "s.sendall(b\"" + token + "\\n\")");
            default -> throw new IllegalStateException("Plataforma de ayuda desconocida: " + platform);
        };
    }

    private void renderHelperPanel(GuiGraphicsExtractor gui, Font font, int x, int y) {
        UiDraw.card(gui, x, y, CARD_WIDTH, HELPER_PANEL_H);

        int textX = x + 8;
        int introY = y + 26 + 2;

        String introKey = switch (helperPlatform) {
            case PLATFORM_ESP32 -> "gui.serialcraft.welcome.helper_intro_esp32";
            case PLATFORM_UNO_Q -> "gui.serialcraft.welcome.helper_intro_uno_q";
            case PLATFORM_PI    -> "gui.serialcraft.welcome.helper_intro_pi";
            default -> throw new IllegalStateException("Plataforma de ayuda desconocida: " + helperPlatform);
        };
        gui.text(font, font.plainSubstrByWidth(Component.translatable(introKey).getString(), CARD_WIDTH - 16),
                textX, introY, UiTheme.TEXT_SECONDARY, false);

        List<String> snippet = helperSnippetLines(helperPlatform);
        int codeBoxY = introY + 12;
        int codeBoxH = snippet.size() * 11 + 8;
        gui.fill(x + 8, codeBoxY, x + CARD_WIDTH - 8, codeBoxY + codeBoxH, UiTheme.BG_CONSOLE);
        gui.outline(x + 8, codeBoxY, CARD_WIDTH - 16, codeBoxH, 0xFF37474F);

        boolean live = ConnectionManager.getWifi().isServerRunning();
        int lineY = codeBoxY + 5;
        for (String line : snippet) {
            gui.text(font, font.plainSubstrByWidth(line, CARD_WIDTH - 24),
                    x + 12, lineY, live ? UiTheme.OK : UiTheme.WARN, false);
            lineY += 11;
        }

        if (!live) {
            gui.text(font, Component.translatable("gui.serialcraft.welcome.helper_needs_wifi"),
                    textX, codeBoxY + codeBoxH + 8, UiTheme.WARN_DARK, false);
        }
    }

    private void copyWithFeedback(IconTextButton button, String text) {
        Minecraft.getInstance().keyboardHandler.setClipboard(text);
        if (pendingCopyButton != null) pendingCopyButton.setMessage(pendingCopyOriginalLabel);

        pendingCopyOriginalLabel = button.getMessage();
        pendingCopyButton = button;
        copyFeedbackUntilMs = System.currentTimeMillis() + 1500;
        button.setMessage(Component.translatable("gui.serialcraft.welcome.copied"));
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  DISPOSITIVOS USB
    // ════════════════════════════════════════════════════════════════════════════

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

    private void buildDeviceButtons(PanelUI panelUi, int x, int y) {
        for (PanelUI.DeviceInfo device : devices) {
            final PanelUI.DeviceInfo target = device;
            panelUi.addWidget(new IconTextButton(
                    x + 236, y + 14, 86, 20, SpriteIcon.CONNECT,
                    Component.translatable("gui.serialcraft.welcome.connect"),
                    btn -> panelUi.connectDevice(target),
                    0xFF2E7D32, 0xFF388E3C, UiTheme.TEXT_INVERSE));
            y += UiTheme.CARD_ROW_HEIGHT;
        }
    }

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

    // ════════════════════════════════════════════════════════════════════════════
    //  RENDER
    // ════════════════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font, int width, int height) {
        Layout l = layout();
        int x = (width - CARD_WIDTH) / 2;
        int logoX = (width - LOGO_WIDTH) / 2;

        // Cabecera superior moderna con barra coloreada
        gui.fill(0, 0, width, l.logoHeight() + 32, UiTheme.BG_NAV);
        gui.fill(0, l.logoHeight() + 32, width, l.logoHeight() + 34, 0x20000000);

        gui.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE,
                logoX, LOGO_Y, 0, 0, LOGO_WIDTH, l.logoHeight(),
                LOGO_SRC_W, LOGO_SRC_H, LOGO_SRC_W, LOGO_SRC_H);

        Component subtitle = Component.translatable("gui.serialcraft.welcome.subtitle");
        gui.text(font, subtitle, (width - font.width(subtitle)) / 2, l.subtitleY(), 0xFFE0F7FA, false);

        renderWifiInfoPanel(gui, font, x, l.wifiInfoY(), l.wifiInfoH());
        if (showHelper) renderHelperPanel(gui, font, x, l.helperPanelY());
        renderDeviceCards(gui, font, x, l.deviceListY());
    }

    private void renderDeviceCards(GuiGraphicsExtractor gui, Font font, int x, int y) {
        if (devices.isEmpty()) {
            UiDraw.card(gui, x, y, CARD_WIDTH, 44);
            gui.text(font, Component.translatable("gui.serialcraft.welcome.no_usb"),
                    x + 12, y + 10, UiTheme.TEXT_SECONDARY, false);
            gui.text(font, Component.translatable("gui.serialcraft.welcome.no_usb_hint"),
                    x + 12, y + 24, UiTheme.TEXT_MUTED, false);
            return;
        }

        for (PanelUI.DeviceInfo device : devices) {
            UiDraw.card(gui, x, y, CARD_WIDTH - 5, UiTheme.CARD_HEIGHT);

            boolean wifi = device.isWifi();
            UiDraw.badge(gui, font, x + 10, y + 14, device.type(),
                    wifi ? UiTheme.INFO_BG   : UiTheme.NEUTRAL_BG,
                    wifi ? UiTheme.INFO_DARK : UiTheme.NEUTRAL_TX);

            gui.text(font, font.plainSubstrByWidth(device.name(), 175),
                    x + 50, y + 13, UiTheme.TEXT_PRIMARY, false);
            gui.text(font, device.address(), x + 50, y + 27, UiTheme.TEXT_SECONDARY, false);

            y += UiTheme.CARD_ROW_HEIGHT;
        }
    }
}
