package com.serialcraft.client.ui.pages;

import com.fazecast.jSerialComm.SerialPort;
import com.serialcraft.SerialCraft;
import com.serialcraft.client.ui.ScrollState;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.client.ui.widget.MethodCard;
import com.serialcraft.connection.BoardTrust;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.connection.UsbBoards;
import com.serialcraft.identity.BoardIdentity;
import com.serialcraft.connection.WifiHandler;
import com.serialcraft.screen.PanelUI;
import com.serialcraft.util.NetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla de bienvenida con seleccion de metodo de conexion (USB / Wi-Fi),
 * panel de configuracion, guia rapida y dispositivos descubiertos.
 *
 * Incluye barra de desplazamiento (scroll) adaptativa para pantallas compactas.
 */
public class WelcomePage implements Page {

    private static final Identifier LOGO_TEXTURE =
            Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "textures/gui/logo-txt.png");

    private static final int LOGO_SRC_W = 779;
    private static final int LOGO_SRC_H = 261;
    private static final int LOGO_WIDTH = 190;
    private static final int LOGO_Y     = 10;
    // Ancho máximo del contenido (métodos, ayuda y lista); la franja de cabecera ocupa la pantalla.
    private static final int DEFAULT_CARD_WIDTH = 340;
    private static final int HEADER_CONTENT_GAP = 6;

    private static final int METHOD_H     = 42;
    private static final int METHOD_GAP   = 8;
    private static final int WIFI_INFO_H_LISTENING = 96;
    private static final int WIFI_INFO_H_CONNECTED = 34;
    private static final int HELPER_LINK_H = 16;
    private static final int HELPER_PANEL_H = 138;

    private static final int DEFAULT_USB_BAUD = 115200;

    private static final int PLATFORM_ESP32 = 0;
    private static final int PLATFORM_UNO_Q = 1;
    private static final int PLATFORM_PI    = 2;
    private static final int PLATFORM_COUNT = 3;

    private final List<PanelUI.DeviceInfo> devices = new ArrayList<>();
    private final ScrollState scroll = new ScrollState();

    private record ScrollableWidget(AbstractWidget widget, int baseY) {}
    private final List<ScrollableWidget> scrollableWidgets = new ArrayList<>();

    private PanelUI panel;
    private int screenHeight;
    private Layout currentLayout;
    private String hostIp = "";
    private Component wifiStartError;
    private boolean showHelper = false;
    private int helperPlatform = PLATFORM_ESP32;

    private IconTextButton pendingCopyButton;
    private Component pendingCopyOriginalLabel;
    private long copyFeedbackUntilMs;

    /** Rectángulo de una sección o elemento, siempre relativo a su contenedor. */
    private record Bounds(int x, int y, int width, int height) {
        int right() { return x + width; }
        int bottom() { return y + height; }
    }

    /** El botón pertenece a la misma fila que su tarjeta, no a una lista de Y independientes. */
    private record DeviceRow(Bounds card, Bounds connectButton) {}

    /** Geometría única que comparten el render y los widgets interactivos. */
    private record Layout(Bounds header, Bounds logo, int subtitleY,
                          Bounds usbMethod, Bounds wifiMethod, Bounds wifiInfo,
                          Bounds helpLink, Bounds helpPanel,
                          List<DeviceRow> deviceRows, Bounds emptyDevices,
                          int viewportTop, int totalHeight) {
        int cardX() { return usbMethod.x(); }
        int cardWidth() { return wifiMethod.right() - usbMethod.x(); }
    }

    /**
     * Flujo vertical: cabecera -> métodos -> estado Wi-Fi -> ayuda -> dispositivos.
     * Cada Y se obtiene del límite inferior de la sección anterior.
     * Se recalcula en init() al cambiar tamaño o al desplegar una sección.
     */
    private Layout layout(int width) {
        int cardWidth = Math.max(1, Math.min(DEFAULT_CARD_WIDTH, width - 24));
        int cardX = (width - cardWidth) / 2;

        int logoWidth = Math.max(1, Math.min(LOGO_WIDTH, width - 40));
        int logoHeight = (logoWidth * LOGO_SRC_H) / LOGO_SRC_W;
        Bounds logo = new Bounds((width - logoWidth) / 2, LOGO_Y, logoWidth, logoHeight);
        int subtitleY = logo.bottom() + 4;
        Bounds header = new Bounds(0, 0, width, subtitleY + 12);

        int methodY = header.bottom() + HEADER_CONTENT_GAP;
        int usbWidth = (cardWidth - METHOD_GAP) / 2;
        Bounds usbMethod = new Bounds(cardX, methodY, usbWidth, METHOD_H);
        Bounds wifiMethod = new Bounds(usbMethod.right() + METHOD_GAP, methodY,
                cardWidth - usbWidth - METHOD_GAP, METHOD_H);

        WifiHandler wifi = ConnectionManager.getWifi();
        int wifiHeight = switch (wifi.getState()) {
            case STOPPED   -> wifiStartError == null ? 0
                    : UiDraw.noticeHeight(Minecraft.getInstance().font, wifiStartError, cardWidth);
            case LISTENING -> WIFI_INFO_H_LISTENING;
            case CONNECTED -> WIFI_INFO_H_CONNECTED;
        };
        int wifiY = usbMethod.bottom() + (wifiHeight > 0 ? 8 : 0);
        Bounds wifiInfo = new Bounds(cardX, wifiY, cardWidth, wifiHeight);

        int helpY = (wifiHeight > 0 ? wifiInfo.bottom() + 8 : usbMethod.bottom() + 4);
        Bounds helpLink = new Bounds(cardX, helpY, cardWidth, HELPER_LINK_H);
        int helpPanelHeight = showHelper ? helperPanelHeight(cardWidth) : 0;
        Bounds helpPanel = new Bounds(cardX, helpLink.bottom() + 6, cardWidth, helpPanelHeight);

        int devicesY = showHelper ? helpPanel.bottom() + 10 : helpLink.bottom() + 6;
        List<DeviceRow> rows = new ArrayList<>();
        for (int i = 0; i < devices.size(); i++) {
            Bounds card = new Bounds(cardX, devicesY + i * UiTheme.CARD_ROW_HEIGHT,
                    cardWidth, UiTheme.CARD_HEIGHT);
            Bounds button = new Bounds(card.right() - 6 - 86,
                    card.y() + (card.height() - 20) / 2, 86, 20);
            rows.add(new DeviceRow(card, button));
        }
        Bounds emptyDevices = new Bounds(cardX, devicesY, cardWidth, 44);
        int totalHeight = devicesY + Math.max(1, devices.size()) * UiTheme.CARD_ROW_HEIGHT + 20;

        return new Layout(header, logo, subtitleY, usbMethod, wifiMethod, wifiInfo,
                helpLink, helpPanel, List.copyOf(rows), emptyDevices,
                header.bottom() + 4, totalHeight);
    }

    /** Evita que el botón de copiar invada la siguiente sección al cambiar de ejemplo. */
    private int helperPanelHeight(int width) {
        return Math.max(HELPER_PANEL_H, helperCopyOffset(width) + 16 + 6);
    }

    private Component helperIntro() {
        return Component.translatable(switch (helperPlatform) {
            case PLATFORM_UNO_Q -> "gui.serialcraft.welcome.helper_intro_uno_q";
            case PLATFORM_PI -> "gui.serialcraft.welcome.helper_intro_pi";
            default -> "gui.serialcraft.welcome.helper_intro_esp32";
        });
    }

    private int helperCodeOffset(int width) {
        Font font = Minecraft.getInstance().font;
        return 26 + font.split(helperIntro(), Math.max(1, width - 20)).size() * font.lineHeight + 4;
    }

    private int helperCopyOffset(int width) {
        int offset = helperCodeOffset(width) + helperSnippetLines(helperPlatform).size() * 11 + 8 + 6;
        if (!ConnectionManager.getWifi().isServerRunning()) {
            Font font = Minecraft.getInstance().font;
            offset += font.split(Component.translatable("gui.serialcraft.welcome.helper_needs_wifi"),
                    Math.max(1, width - 20)).size() * font.lineHeight + 6;
        }
        return offset;
    }

    // ── INIT ──────────────────────────────────────────────────────────────────

    @Override
    public void init(PanelUI panelUi, int width, int height) {
        this.panel        = panelUi;
        this.screenHeight = height;
        this.scrollableWidgets.clear();
        this.pendingCopyButton = null;

        if (ConnectionManager.getWifi().isServerRunning() && (hostIp == null || hostIp.isEmpty())) {
            hostIp = NetUtils.findLocalIpv4();
        }

        scanUsbPorts();
        currentLayout = layout(width);
        Layout l = currentLayout;

        buildMethodCards(panelUi, l.usbMethod(), l.wifiMethod());
        if (l.wifiInfo().height() > 0) {
            Bounds wifi = l.wifiInfo();
            buildWifiInfoWidgets(panelUi, wifi.x(), wifi.width(), wifi.y(), wifi.height());
        }
        buildHelperLink(panelUi, l.helpLink().x(), l.helpLink().width(), l.helpLink().y());
        if (showHelper) {
            Bounds help = l.helpPanel();
            buildHelperPanel(panelUi, help.x(), help.width(), help.y());
        }
        buildDeviceButtons(panelUi, l.deviceRows());
    }

    private void addScrollWidget(PanelUI panelUi, AbstractWidget widget, int baseY) {
        panelUi.addWidget(widget);
        scrollableWidgets.add(new ScrollableWidget(widget, baseY));
    }

    @Override
    public void tick() {
        if (pendingCopyButton != null && System.currentTimeMillis() > copyFeedbackUntilMs) {
            pendingCopyButton.setMessage(pendingCopyOriginalLabel);
            pendingCopyButton = null;
        }

        if (panel == null) return;
        if (ConnectionManager.isAnyConnected()) {
            PanelUI.DeviceInfo dev = PanelUI.getSelectedDevice();
            if (dev != null) {
                panel.connectDevice(dev);
            }
        }
    }

    // ── EVENTOS DE RATÓN Y DESPLAZAMIENTO ─────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return scroll.mouseScrolled(verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        Layout l = currentLayout;
        if (l == null) return false;
        int trackX = l.cardX() + l.cardWidth() + 4;
        int trackY = l.viewportTop();
        int trackH = Math.max(10, screenHeight - trackY - 4);
        return scroll.mouseClicked(event.x(), event.y(), event.button(), trackX, trackY, 6, trackH);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return scroll.mouseReleased(event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (currentLayout == null) return false;
        int trackY = currentLayout.viewportTop();
        int trackH = Math.max(10, screenHeight - trackY - 4);
        return scroll.mouseDragged(event.y(), trackY, trackH);
    }

    // ── TARJETAS DE METODO ────────────────────────────────────────────────────

    private void buildMethodCards(PanelUI panelUi, Bounds usb, Bounds wifiCard) {

        long usbCount = devices.stream().filter(d -> "USB".equals(d.type())).count();
        Component usbStatus = usbCount == 0
                ? Component.translatable("gui.serialcraft.welcome.method_usb_empty")
                : Component.translatable("gui.serialcraft.welcome.method_usb_count", usbCount);

        addScrollWidget(panelUi, new MethodCard(usb.x(), usb.y(), usb.width(), usb.height(), SpriteIcon.USB,
                Component.translatable("gui.serialcraft.welcome.method_usb"),
                usbStatus, UiTheme.TEXT_ON_DARK,
                UiTheme.TAB_INACTIVE_BG, UiTheme.TAB_INACTIVE_BORDER,
                () -> panel.refreshWelcome()), usb.y());

        WifiHandler wifi = ConnectionManager.getWifi();
        Component wifiStatus = switch (wifi.getState()) {
            case STOPPED   -> Component.translatable("gui.serialcraft.welcome.method_wifi_start");
            case LISTENING -> Component.translatable("gui.serialcraft.welcome.method_wifi_listening");
            case CONNECTED -> Component.translatable("gui.serialcraft.welcome.method_wifi_connected", wifi.getRemoteIp());
        };
        int wifiBg     = (wifi.getState() == WifiHandler.State.LISTENING) ? UiTheme.TEAL : UiTheme.TAB_INACTIVE_BG;
        int wifiBorder = (wifi.getState() == WifiHandler.State.LISTENING) ? UiTheme.TEAL_BORDER : UiTheme.TAB_INACTIVE_BORDER;

        addScrollWidget(panelUi, new MethodCard(wifiCard.x(), wifiCard.y(), wifiCard.width(),
                wifiCard.height(), SpriteIcon.WIFI,
                Component.translatable("gui.serialcraft.welcome.method_wifi"),
                wifiStatus, UiTheme.TEXT_ON_DARK, wifiBg, wifiBorder,
                this::toggleWifiServer), wifiCard.y());
    }

    private void toggleWifiServer() {
        WifiHandler wifi = ConnectionManager.getWifi();
        wifiStartError = null;
        if (wifi.isServerRunning()) {
            wifi.disconnect();
            hostIp = "";
        } else {
            Component result = wifi.startServer(WifiHandler.DEFAULT_PORT, generateToken());
            if (wifi.isServerRunning()) {
                hostIp = NetUtils.findLocalIpv4();
            } else {
                hostIp = "";
                wifiStartError = result;
            }
        }
        if (panel != null) panel.refreshWelcome();
    }

    private static String generateToken() { return WifiHandler.newSessionToken(); }

    // ── WIDGETS DE INFORMACION WI-FI ──────────────────────────────────────────

    private void buildWifiInfoWidgets(PanelUI panelUi, int x, int cardWidth, int y, int h) {
        WifiHandler wifi = ConnectionManager.getWifi();
        if (wifi.getState() == WifiHandler.State.LISTENING) {
            int btnW = 56;
            int btnH = 16;
            int btnX = x + cardWidth - btnW - 8;

            addScrollWidget(panelUi, new IconTextButton(
                    btnX, y + 22, btnW, btnH, null,
                    Component.translatable("gui.serialcraft.welcome.copy"),
                    btn -> copyWithFeedback(btn, getEffectiveHostIp()),
                    UiTheme.SLATE, UiTheme.SLATE_BORDER, UiTheme.TEXT_INVERSE), y + 22);

            addScrollWidget(panelUi, new IconTextButton(
                    btnX, y + 44, btnW, btnH, null,
                    Component.translatable("gui.serialcraft.welcome.copy"),
                    btn -> copyWithFeedback(btn, String.valueOf(WifiHandler.DEFAULT_PORT)),
                    UiTheme.SLATE, UiTheme.SLATE_BORDER, UiTheme.TEXT_INVERSE), y + 44);

            addScrollWidget(panelUi, new IconTextButton(
                    btnX, y + 66, btnW, btnH, null,
                    Component.translatable("gui.serialcraft.welcome.copy"),
                    btn -> copyWithFeedback(btn, wifi.getPairingToken()),
                    UiTheme.TEAL, UiTheme.TEAL_BORDER, UiTheme.TEXT_INVERSE), y + 66);

        } else if (wifi.getState() == WifiHandler.State.CONNECTED) {
            addScrollWidget(panelUi, new IconTextButton(
                    x + cardWidth - 92, y + (h - 18) / 2, 86, 18, SpriteIcon.DISCONNECT,
                    Component.translatable("gui.serialcraft.welcome.wifi_disconnect"),
                    btn -> toggleWifiServer(),
                    UiTheme.ERROR_DARK, UiTheme.ERROR), y + (h - 18) / 2);
        }
    }

    private String getEffectiveHostIp() {
        return (hostIp != null && !hostIp.isEmpty()) ? hostIp : NetUtils.findLocalIpv4();
    }

    private String wifiSnippetOneLine(WifiHandler wifi) {
        return getEffectiveHostIp() + ":" + WifiHandler.DEFAULT_PORT + " token:" + wifi.getPairingToken();
    }

    private void renderWifiInfoPanel(GuiGraphicsExtractor gui, Font font, int x, int cardWidth, int y, int h) {
        if (h <= 0) return;
        WifiHandler wifi = ConnectionManager.getWifi();

        if (wifi.getState() == WifiHandler.State.STOPPED && wifiStartError != null) {
            UiDraw.notice(gui, font, wifiStartError, x, y, cardWidth, UiTheme.ERROR_BG, UiTheme.ERROR_DARK);
        } else if (wifi.getState() == WifiHandler.State.LISTENING) {
            UiDraw.surface(gui, x, y, cardWidth, h, 0, UiTheme.INFO_BG, 18, false);
            gui.fill(x + 3, y + 18, x + cardWidth - 3, y + 19, UiTheme.INFO_LINE);

            gui.fill(x + 8, y + 5, x + 14, y + 11, UiTheme.OK);
            gui.text(font, Component.translatable("gui.serialcraft.welcome.wifi_server_title"),
                    x + 18, y + 4, UiTheme.INFO_DARK, false);

            int valBoxX = x + 58;
            int valBoxW = cardWidth - 58 - 66;

            UiDraw.badge(gui, font, x + 8, y + 23,
                    Component.translatable("gui.serialcraft.welcome.wifi_host"),
                    UiTheme.BG_BADGE, UiTheme.TEXT_SECONDARY);
            gui.fill(valBoxX, y + 22, valBoxX + valBoxW, y + 38, UiTheme.BG_FIELD);
            gui.outline(valBoxX, y + 22, valBoxW, 16, UiTheme.LINE_SOFT);
            gui.text(font, getEffectiveHostIp(), valBoxX + 6, y + 26, UiTheme.TEXT_PRIMARY, false);

            UiDraw.badge(gui, font, x + 8, y + 45,
                    Component.translatable("gui.serialcraft.welcome.wifi_port"),
                    UiTheme.BG_BADGE, UiTheme.TEXT_SECONDARY);
            gui.fill(valBoxX, y + 44, valBoxX + valBoxW, y + 60, UiTheme.BG_FIELD);
            gui.outline(valBoxX, y + 44, valBoxW, 16, UiTheme.LINE_SOFT);
            gui.text(font, String.valueOf(WifiHandler.DEFAULT_PORT), valBoxX + 6, y + 48, UiTheme.TEXT_PRIMARY, false);

            UiDraw.badge(gui, font, x + 8, y + 67,
                    Component.translatable("gui.serialcraft.welcome.wifi_token"),
                    UiTheme.TEAL_BG, UiTheme.ACCENT_PRIMARY_DARK);
            gui.fill(valBoxX, y + 66, valBoxX + valBoxW, y + 82, UiTheme.OK_BG);
            gui.outline(valBoxX, y + 66, valBoxW, 16, UiTheme.OK_LINE);
            gui.text(font, wifi.getPairingToken(), valBoxX + 6, y + 70, UiTheme.OK_DARK, false);

            int remembered = BoardTrust.store().all().size();
            if (remembered > 0) {
                gui.text(font, Component.translatable("gui.serialcraft.welcome.wifi_remembered", remembered),
                        x + 8, y + 85, UiTheme.TEXT_SECONDARY, false);
            }

        } else if (wifi.getState() == WifiHandler.State.CONNECTED) {
            UiDraw.surface(gui, x, y, cardWidth, h, UiTheme.OK, 0, 0, false);

            UiDraw.badge(gui, font, x + 10, y + 9, "WI-FI", UiTheme.OK_BG, UiTheme.OK_DARK);

            Component status = Component.translatable("gui.serialcraft.welcome.wifi_connected", wifi.getRemoteIp());
            gui.text(font, status, x + 54, y + 12, UiTheme.TEXT_PRIMARY, false);
        }
    }

    // ── AYUDA DE CONEXION ─────────────────────────────────────────────────────

    private void buildHelperLink(PanelUI panelUi, int x, int cardWidth, int y) {
        Component text = Component.translatable(showHelper
                ? "gui.serialcraft.welcome.helper_link_close"
                : "gui.serialcraft.welcome.helper_link_open");
        int linkWidth = Minecraft.getInstance().font.width(text) + 44;

        addScrollWidget(panelUi, new IconTextButton(
                x + (cardWidth - linkWidth) / 2, y, linkWidth, HELPER_LINK_H, SpriteIcon.QUEST,
                text, btn -> { showHelper = !showHelper; if (panel != null) panel.refreshWelcome(); },
                0x00000000, 0x00000000, UiTheme.INFO), y);
    }

    private void buildHelperPanel(PanelUI panelUi, int x, int cardWidth, int y) {
        int tabW = (cardWidth - 2 * 4) / PLATFORM_COUNT;
        String[] tabKeys = {
                "gui.serialcraft.welcome.helper_tab_esp32",
                "gui.serialcraft.welcome.helper_tab_uno_q",
                "gui.serialcraft.welcome.helper_tab_pi",
        };
        for (int i = 0; i < PLATFORM_COUNT; i++) {
            boolean active = helperPlatform == i;
            int platform = i;
            addScrollWidget(panelUi, new IconTextButton(
                    x + i * (tabW + 4), y + 6, tabW, 16, null,
                    Component.translatable(tabKeys[i]),
                    btn -> { helperPlatform = platform; if (panel != null) panel.refreshWelcome(); },
                    active ? UiTheme.ACCENT_PRIMARY : UiTheme.TAB_INACTIVE_BG,
                    active ? UiTheme.ACCENT_PRIMARY_DARK : UiTheme.TAB_INACTIVE_BORDER), y + 6);
        }

        List<String> snippet = helperSnippetLines(helperPlatform);
        int copyY = y + helperCopyOffset(cardWidth);

        IconTextButton copyBtn = new IconTextButton(
                    x + cardWidth - 96, copyY, 96, 16, SpriteIcon.CODE,
                    Component.translatable("gui.serialcraft.welcome.copy"),
                    btn -> copyWithFeedback(btn, String.join("\n", snippet)),
                    UiTheme.TAB_INACTIVE_BG, UiTheme.TAB_INACTIVE_BORDER);
        addScrollWidget(panelUi, copyBtn, copyY);
    }

    private List<String> helperSnippetLines(int platform) {
        WifiHandler wifi = ConnectionManager.getWifi();
        String host = getEffectiveHostIp();
        int port = WifiHandler.DEFAULT_PORT;
        String token = wifi.getPairingToken().isEmpty() ? "TU_TOKEN" : wifi.getPairingToken();

        return switch (platform) {
            case PLATFORM_ESP32 -> List.of(
                    "// ESP32: conectar al servidor Wi-Fi de SerialCraft",
                    "WiFiClient client;",
                    "if (client.connect(\"" + host + "\", " + port + ")) {",
                    "  client.println(\"" + token + "\");  // handshake obligatorio",
                    "  client.println(\"pot_val:128\");",
                    "}"
            );
            case PLATFORM_UNO_Q -> List.of(
                    "# Arduino UNO Q (Linux embebido / Python)",
                    "import socket",
                    "s = socket.create_connection((\"" + host + "\", " + port + "))",
                    "s.sendall(b\"" + token + "\\n\")  # handshake",
                    "s.sendall(b\"pot_val:128\\n\")"
            );
            case PLATFORM_PI -> List.of(
                    "# Raspberry Pi / Linux",
                    "nc " + host + " " + port,
                    "# escribe el token como primera linea:",
                    token,
                    "# ahora puedes enviar senales:",
                    "btn_rojo:255"
            );
            default -> List.of();
        };
    }

    private void renderHelperPanel(GuiGraphicsExtractor gui, Font font, int x, int cardWidth, int y, int height) {
        UiDraw.surface(gui, x, y, cardWidth, height, 0, 0, 0, false);

        int textX = x + 10;
        int introY = y + 26;
        UiDraw.wrappedText(gui, font, helperIntro(), textX, introY, cardWidth - 20, UiTheme.TEXT_SECONDARY);

        List<String> snippet = helperSnippetLines(helperPlatform);
        int codeBoxY = y + helperCodeOffset(cardWidth);
        int codeBoxH = snippet.size() * 11 + 8;
        gui.fill(x + 8, codeBoxY, x + cardWidth - 8, codeBoxY + codeBoxH, UiTheme.BG_CONSOLE);
        gui.outline(x + 8, codeBoxY, cardWidth - 16, codeBoxH, UiTheme.SLATE);

        boolean live = ConnectionManager.getWifi().isServerRunning();
        int lineY = codeBoxY + 5;
        for (String line : snippet) {
            gui.text(font, font.plainSubstrByWidth(line, cardWidth - 24),
                    x + 12, lineY, live ? UiTheme.OK : UiTheme.WARN, false);
            lineY += 11;
        }

        if (!live) {
            UiDraw.wrappedText(gui, font, Component.translatable("gui.serialcraft.welcome.helper_needs_wifi"),
                    textX, codeBoxY + codeBoxH + 6, cardWidth - 20, UiTheme.WARN_DARK);
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

    // ── DISPOSITIVOS USB ──────────────────────────────────────────────────────

    private void scanUsbPorts() {
        devices.removeIf(device -> "USB".equals(device.type()));
        for (SerialPort port : SerialPort.getCommPorts()) {
            final String systemName = port.getSystemPortName();
            BoardIdentity identity = UsbBoards.identify(port);
            String label    = describeBoard(port, identity);
            String platform = platformOf(identity);

            devices.add(new PanelUI.DeviceInfo(
                    label, systemName, "USB", platform,
                    () -> ConnectionManager.getSerial().connect(systemName, DEFAULT_USB_BAUD)));
        }
    }

    private void buildDeviceButtons(PanelUI panelUi, List<DeviceRow> rows) {
        for (int i = 0; i < devices.size(); i++) {
            PanelUI.DeviceInfo target = devices.get(i);
            Bounds button = rows.get(i).connectButton();
            addScrollWidget(panelUi, new IconTextButton(
                    button.x(), button.y(), button.width(), button.height(), SpriteIcon.CONNECT,
                    Component.translatable("gui.serialcraft.welcome.connect"),
                    btn -> panelUi.connectDevice(target),
                    UiTheme.OK_DARK, UiTheme.OK_DARK_BORDER, UiTheme.TEXT_INVERSE), button.y());
        }
    }

    private static String describeBoard(SerialPort port, BoardIdentity id) {
        switch (id.confidence()) {
            case DECLARED, MODEL:
                return id.model();
            case VENDOR:
                return Component.translatable("gui.serialcraft.board.vendor", id.model()).getString();
            case BRIDGE:
                return Component.translatable("gui.serialcraft.board.bridge", bridgeName(id.bridge())).getString();
            default:
                String description = port.getDescriptivePortName();
                return (description == null || description.isBlank() || description.contains("Generic"))
                        ? Component.translatable("gui.serialcraft.board.unknown").getString()
                        : description;
        }
    }

    private static String bridgeName(BoardIdentity.Bridge bridge) {
        return switch (bridge) {
            case CH340  -> "CH340";
            case CH9102 -> "CH9102";
            case CP210X -> "CP210x";
            case FTDI   -> "FTDI";
            case PL2303 -> "PL2303";
            default     -> "USB";
        };
    }

    private static String platformOf(BoardIdentity id) {
        String label = id.platformLabel();
        return label.isEmpty() ? Component.translatable("gui.serialcraft.board.generic").getString() : label;
    }

    // ── RENDER ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font, int width, int height) {
        Layout l = currentLayout;
        if (l == null) return;

        int headerH = l.header().height();

        // Cabecera y contenido mantienen sus fondos propios, sobre el mundo desenfocado.
        gui.fill(0, headerH, width, height, UiTheme.BG_WELCOME);
        gui.fill(0, 0, width, headerH, UiTheme.BG_WELCOME_HEADER);
        gui.fill(0, headerH, width, headerH + 2, 0x20000000);

        Bounds logo = l.logo();
        gui.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE,
                logo.x(), logo.y(), 0, 0, logo.width(), logo.height(),
                LOGO_SRC_W, LOGO_SRC_H, LOGO_SRC_W, LOGO_SRC_H);

        Component subtitle = Component.translatable("gui.serialcraft.welcome.subtitle");
        gui.text(font, subtitle, (width - font.width(subtitle)) / 2, l.subtitleY(),
                UiTheme.TEXT_ON_NAV, true);

        int viewportTop = l.viewportTop();
        int viewportBottom = height - 4;
        int viewportHeight = Math.max(10, viewportBottom - viewportTop);

        scroll.update(viewportHeight, l.totalHeight() - viewportTop);
        int scrollY = (int) scroll.getScrollAmount();

        // Los widgets usan exactamente los mismos límites que sus secciones dibujadas.
        for (ScrollableWidget sw : scrollableWidgets) {
            int currentY = sw.baseY() - scrollY;
            sw.widget().setY(currentY);
            // Screen dibuja los widgets después de esta página (sin scissor).
            // Ocultamos los que cruzan la cabecera o el pie para evitar clics fuera.
            boolean inView = currentY >= viewportTop
                    && currentY + sw.widget().getHeight() <= viewportBottom;
            sw.widget().visible = inView;
            sw.widget().active = inView;
        }

        gui.enableScissor(0, viewportTop, width, viewportBottom);

        Bounds wifi = l.wifiInfo();
        renderWifiInfoPanel(gui, font, wifi.x(), wifi.width(), wifi.y() - scrollY, wifi.height());
        if (showHelper) {
            Bounds help = l.helpPanel();
            renderHelperPanel(gui, font, help.x(), help.width(), help.y() - scrollY, help.height());
        }
        renderDeviceCards(gui, font, l, scrollY);

        gui.disableScissor();

        if (scroll.hasScroll()) {
            scroll.renderScrollbar(gui, l.cardX() + l.cardWidth() + 4,
                    viewportTop, 6, viewportHeight);
        }
    }

    private void renderDeviceCards(GuiGraphicsExtractor gui, Font font, Layout layout, int scrollY) {
        if (devices.isEmpty()) {
            Bounds empty = layout.emptyDevices();
            int x = empty.x();
            int y = empty.y() - scrollY;
            UiDraw.surface(gui, x, y, empty.width(), empty.height(), 0, 0, 0, false);
            gui.text(font, Component.translatable("gui.serialcraft.welcome.no_usb"),
                    x + 12, y + 10, UiTheme.TEXT_SECONDARY, false);
            gui.text(font, Component.translatable("gui.serialcraft.welcome.no_usb_hint"),
                    x + 12, y + 24, UiTheme.TEXT_MUTED, false);
            return;
        }

        for (int i = 0; i < devices.size(); i++) {
            PanelUI.DeviceInfo device = devices.get(i);
            Bounds card = layout.deviceRows().get(i).card();
            int x = card.x();
            int y = card.y() - scrollY;
            int cardWidth = card.width();
            UiDraw.surface(gui, x, y, cardWidth, card.height(), 0, 0, 0, false);

            boolean wifi = device.isWifi();
            UiDraw.badge(gui, font, x + 10, y + 14, device.type(),
                    wifi ? UiTheme.INFO_BG   : UiTheme.NEUTRAL_BG,
                    wifi ? UiTheme.INFO_DARK : UiTheme.NEUTRAL_TX);

            int nameW = Math.max(40, cardWidth - 150);
            gui.text(font, font.plainSubstrByWidth(device.name(), nameW),
                    x + 50, y + 13, UiTheme.TEXT_PRIMARY, false);
            gui.text(font, device.address(), x + 50, y + 27, UiTheme.TEXT_SECONDARY, false);
        }
    }

}
