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
    private static final int DEFAULT_CARD_WIDTH = 360;
    private static final int FRAME_PADDING = 8;
    private static final int HEADER_CONTENT_GAP = 6;

    private static final int METHOD_H     = 42;
    private static final int METHOD_GAP   = 8;
    private static final int WIFI_INFO_H_LISTENING = 96;
    private static final int WIFI_INFO_H_CONNECTED = 48;
    private static final int WIFI_COPY_WIDTH = 72;
    private static final int WIFI_REMEMBERED_Y = 92;
    private static final int WIFI_REMEMBERED_PADDING = 12;
    private static final int HELPER_LINK_H = 18;
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
        Bounds moveDown(int offset) { return new Bounds(x, y + offset, width, height); }
    }

    /** El botón pertenece a la misma fila que su tarjeta, no a una lista de Y independientes. */
    private record DeviceRow(Bounds card, Bounds connectButton) {}

    /** Geometría única que comparten el render y los widgets interactivos. */
    private record Layout(Bounds header, Bounds logo, int subtitleY, Bounds connectionPanel,
                          Bounds usbMethod, Bounds wifiMethod, Bounds wifiInfo,
                          Bounds helpLink, Bounds helpPanel,
                          List<DeviceRow> deviceRows, Bounds emptyDevices,
                          int viewportTop, int totalHeight) {
        int cardX() { return usbMethod.x(); }
        int cardWidth() { return wifiMethod.right() - usbMethod.x(); }
        int scrollbarX() { return cardX() + cardWidth() + FRAME_PADDING + 2; }
    }

    /**
     * Flujo vertical: cabecera -> métodos -> estado Wi-Fi -> ayuda -> dispositivos.
     * Cada Y se obtiene del límite inferior de la sección anterior.
     * Se recalcula en init() al cambiar tamaño o al desplegar una sección.
     */
    private Layout layout(int width, int height) {
        Font font = Minecraft.getInstance().font;
        int cardWidth = Math.max(1, Math.min(DEFAULT_CARD_WIDTH, width - 40));
        int cardX = (width - cardWidth) / 2;

        int logoWidth = Math.max(1, Math.min(height < 280 ? 150 : LOGO_WIDTH, width - 40));
        int logoHeight = (logoWidth * LOGO_SRC_H) / LOGO_SRC_W;
        Bounds logo = new Bounds((width - logoWidth) / 2, LOGO_Y, logoWidth, logoHeight);
        int subtitleY = logo.bottom() + 4;
        Bounds header = new Bounds(0, 0, width, subtitleY + 12);

        int methodY = header.bottom() + HEADER_CONTENT_GAP + FRAME_PADDING;
        int usbWidth = (cardWidth - METHOD_GAP) / 2;
        Bounds usbMethod = new Bounds(cardX, methodY, usbWidth, METHOD_H);
        Bounds wifiMethod = new Bounds(usbMethod.right() + METHOD_GAP, methodY,
                cardWidth - usbWidth - METHOD_GAP, METHOD_H);

        WifiHandler wifi = ConnectionManager.getWifi();
        int wifiHeight = switch (wifi.getState()) {
            case STOPPED   -> wifiStartError == null ? 0
                    : 14 + font.split(wifiStartError, Math.max(1, cardWidth - 24)).size() * font.lineHeight;
            case LISTENING -> {
                int remembered = BoardTrust.store().all().size();
                int lines = remembered == 0 ? 0 : font.split(
                        Component.translatable("gui.serialcraft.welcome.wifi_remembered", remembered),
                        Math.max(1, cardWidth - WIFI_REMEMBERED_PADDING * 2)).size();
                yield lines == 0 ? WIFI_INFO_H_LISTENING
                        : WIFI_REMEMBERED_Y + lines * font.lineHeight + WIFI_REMEMBERED_PADDING;
            }
            case CONNECTED -> WIFI_INFO_H_CONNECTED;
        };
        int wifiY = usbMethod.bottom() + (wifiHeight > 0 ? 8 : 0);
        Bounds wifiInfo = new Bounds(cardX, wifiY, cardWidth, wifiHeight);

        int panelBottom = (wifiHeight > 0 ? wifiInfo.bottom() : usbMethod.bottom()) + FRAME_PADDING;
        Bounds connectionPanel = new Bounds(cardX - FRAME_PADDING, methodY - FRAME_PADDING,
                cardWidth + FRAME_PADDING * 2, panelBottom - methodY + FRAME_PADDING);
        int helpY = connectionPanel.bottom() + 6;
        Bounds helpLink = new Bounds(cardX, helpY, cardWidth, HELPER_LINK_H);
        int helpPanelHeight = showHelper ? helperPanelHeight(cardWidth) : 0;
        Bounds helpPanel = new Bounds(cardX, helpLink.bottom() + 6, cardWidth, helpPanelHeight);

        int devicesY = showHelper ? helpPanel.bottom() + 12 : helpLink.bottom() + 12;
        List<DeviceRow> rows = new ArrayList<>();
        for (int i = 0; i < devices.size(); i++) {
            Bounds card = new Bounds(cardX, devicesY + i * UiTheme.CARD_ROW_HEIGHT,
                    cardWidth, UiTheme.CARD_HEIGHT);
            Bounds button = new Bounds(card.right() - 8 - 104,
                    card.y() + (card.height() - 24) / 2, 104, 24);
            rows.add(new DeviceRow(card, button));
        }
        int emptyTextWidth = Math.max(1, cardWidth - 48);
        int emptyHeight = Math.max(44, 20 + font.lineHeight * (
                font.split(Component.translatable("gui.serialcraft.welcome.no_usb"), emptyTextWidth).size()
                + font.split(Component.translatable("gui.serialcraft.welcome.no_usb_hint"), emptyTextWidth).size()));
        Bounds emptyDevices = new Bounds(cardX, devicesY, cardWidth, emptyHeight);
        int contentBottom = (rows.isEmpty() ? emptyDevices.bottom() : rows.getLast().card().bottom()) + 6;
        int totalHeight = contentBottom + 6;
        // Centra el conjunto visible; en pantallas bajas conserva el margen superior y el scroll.
        int offset = Math.max(0, (height - (contentBottom - logo.y())) / 2 - logo.y());
        Bounds centeredHeader = new Bounds(0, 0, width, header.height() + offset);
        List<DeviceRow> centeredRows = rows.stream()
                .map(row -> new DeviceRow(row.card().moveDown(offset), row.connectButton().moveDown(offset)))
                .toList();

        return new Layout(centeredHeader, logo.moveDown(offset), subtitleY + offset,
                connectionPanel.moveDown(offset), usbMethod.moveDown(offset), wifiMethod.moveDown(offset),
                wifiInfo.moveDown(offset), helpLink.moveDown(offset), helpPanel.moveDown(offset),
                centeredRows, emptyDevices.moveDown(offset), centeredHeader.bottom() + 4, totalHeight + offset);
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
        currentLayout = layout(width, height);
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
        int trackX = l.scrollbarX();
        int trackY = l.viewportTop();
        int trackH = Math.max(10, screenHeight - trackY - 4);
        return scroll.mouseClicked(event.x(), event.y(), event.button(), trackX, trackY, 4, trackH);
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
                usbStatus, UiTheme.WELCOME_MUTED,
                UiTheme.WELCOME_CARD, UiTheme.WELCOME_LINE, false,
                () -> panel.refreshWelcome()), usb.y());

        WifiHandler wifi = ConnectionManager.getWifi();
        Component wifiStatus = switch (wifi.getState()) {
            case STOPPED   -> Component.translatable("gui.serialcraft.welcome.method_wifi_start");
            case LISTENING -> Component.translatable("gui.serialcraft.welcome.method_wifi_listening");
            case CONNECTED -> Component.translatable("gui.serialcraft.welcome.method_wifi_connected", wifi.getRemoteIp());
        };
        boolean running = wifi.isServerRunning();
        int wifiBg = running ? UiTheme.WELCOME_ACTIVE : UiTheme.WELCOME_CARD;
        int wifiBorder = running ? UiTheme.WELCOME_TEAL : UiTheme.WELCOME_LINE;

        addScrollWidget(panelUi, new MethodCard(wifiCard.x(), wifiCard.y(), wifiCard.width(),
                wifiCard.height(), SpriteIcon.WIFI,
                Component.translatable("gui.serialcraft.welcome.method_wifi"),
                wifiStatus, UiTheme.WELCOME_MUTED, wifiBg, wifiBorder, running,
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
            int btnW = WIFI_COPY_WIDTH;
            int btnH = 18;
            int btnX = x + cardWidth - btnW - 8;

            addScrollWidget(panelUi, new IconTextButton(
                    btnX, y + 22, btnW, btnH, null,
                    Component.translatable("gui.serialcraft.welcome.copy"),
                    btn -> copyWithFeedback(btn, getEffectiveHostIp()),
                    UiTheme.WELCOME_FIELD, UiTheme.WELCOME_CYAN, UiTheme.WELCOME_TEXT).withGlassStyle(), y + 22);

            addScrollWidget(panelUi, new IconTextButton(
                    btnX, y + 44, btnW, btnH, null,
                    Component.translatable("gui.serialcraft.welcome.copy"),
                    btn -> copyWithFeedback(btn, String.valueOf(WifiHandler.DEFAULT_PORT)),
                    UiTheme.WELCOME_FIELD, UiTheme.WELCOME_CYAN, UiTheme.WELCOME_TEXT).withGlassStyle(), y + 44);

            addScrollWidget(panelUi, new IconTextButton(
                    btnX, y + 66, btnW, btnH, null,
                    Component.translatable("gui.serialcraft.welcome.copy"),
                    btn -> copyWithFeedback(btn, wifi.getPairingToken()),
                    UiTheme.WELCOME_ACTIVE, UiTheme.WELCOME_TEAL, UiTheme.WELCOME_TEXT).withGlassStyle(), y + 66);

        } else if (wifi.getState() == WifiHandler.State.CONNECTED) {
            addScrollWidget(panelUi, new IconTextButton(
                    x + cardWidth - 112, y + (h - 20) / 2, 104, 20, SpriteIcon.DISCONNECT,
                    Component.translatable("gui.serialcraft.welcome.wifi_disconnect"),
                    btn -> toggleWifiServer(),
                    0x903F2530, UiTheme.WELCOME_ERROR).withGlassStyle(), y + (h - 20) / 2);
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
            UiDraw.glassSurface(gui, x, y, cardWidth, h, UiTheme.WELCOME_CARD, UiTheme.WELCOME_ERROR, false);
            UiDraw.wrappedText(gui, font, wifiStartError, x + 12, y + 7, cardWidth - 24, UiTheme.WELCOME_ERROR);
        } else if (wifi.getState() == WifiHandler.State.LISTENING) {
            UiDraw.glassSurface(gui, x, y, cardWidth, h, UiTheme.WELCOME_CARD, UiTheme.WELCOME_LINE, false);
            gui.fill(x + 8, y + 18, x + cardWidth - 8, y + 19, 0x506CA9C6);

            gui.fill(x + 8, y + 6, x + 13, y + 11, UiTheme.WELCOME_GREEN);
            gui.text(font, Component.translatable("gui.serialcraft.welcome.wifi_server_title"),
                    x + 20, y + 5, UiTheme.WELCOME_CYAN, false);

            renderWifiRow(gui, font, x, y + 22, cardWidth, "gui.serialcraft.welcome.wifi_host", getEffectiveHostIp(), false);
            renderWifiRow(gui, font, x, y + 44, cardWidth, "gui.serialcraft.welcome.wifi_port", String.valueOf(WifiHandler.DEFAULT_PORT), false);
            renderWifiRow(gui, font, x, y + 66, cardWidth, "gui.serialcraft.welcome.wifi_token", wifi.getPairingToken(), true);

            int remembered = BoardTrust.store().all().size();
            if (remembered > 0) {
                UiDraw.wrappedText(gui, font, Component.translatable("gui.serialcraft.welcome.wifi_remembered", remembered),
                        x + WIFI_REMEMBERED_PADDING, y + WIFI_REMEMBERED_Y,
                        cardWidth - WIFI_REMEMBERED_PADDING * 2, UiTheme.WELCOME_MUTED);
            }

        } else if (wifi.getState() == WifiHandler.State.CONNECTED) {
            UiDraw.glassSurface(gui, x, y, cardWidth, h, UiTheme.WELCOME_CARD, UiTheme.WELCOME_GREEN, false);
            gui.text(font, Component.translatable("gui.serialcraft.status.connected"),
                    x + 10, y + 10, UiTheme.WELCOME_GREEN, false);
            gui.text(font, font.plainSubstrByWidth(wifi.getRemoteIp(), cardWidth - 132),
                    x + 10, y + 26, UiTheme.WELCOME_TEXT, false);
        }
    }

    private void renderWifiRow(GuiGraphicsExtractor gui, Font font, int x, int y, int width,
                               String labelKey, String value, boolean token) {
        int valueX = x + 60;
        int valueWidth = width - 60 - WIFI_COPY_WIDTH - 16;
        UiDraw.glassSurface(gui, x + 8, y, 46, 18, UiTheme.WELCOME_BADGE, 0x406CA9C6, false);
        gui.text(font, Component.translatable(labelKey), x + 14, y + 5, UiTheme.WELCOME_MUTED, false);
        UiDraw.glassSurface(gui, valueX, y, valueWidth, 18,
                token ? UiTheme.WELCOME_TOKEN : UiTheme.WELCOME_FIELD,
                token ? UiTheme.WELCOME_GREEN : UiTheme.WELCOME_LINE, false);
        gui.text(font, font.plainSubstrByWidth(value, Math.max(1, valueWidth - 12)), valueX + 6, y + 5,
                token ? UiTheme.WELCOME_GREEN : UiTheme.WELCOME_TEXT, false);
    }

    // ── AYUDA DE CONEXION ─────────────────────────────────────────────────────

    private void buildHelperLink(PanelUI panelUi, int x, int cardWidth, int y) {
        Component text = Component.translatable(showHelper
                ? "gui.serialcraft.welcome.helper_link_close"
                : "gui.serialcraft.welcome.helper_link_open");
        int linkWidth = Math.min(cardWidth, Minecraft.getInstance().font.width(text) + 36);

        addScrollWidget(panelUi, new IconTextButton(
                x + (cardWidth - linkWidth) / 2, y, linkWidth, HELPER_LINK_H, SpriteIcon.QUEST,
                text, btn -> { showHelper = !showHelper; if (panel != null) panel.refreshWelcome(); },
                0x00000000, 0x00000000, UiTheme.WELCOME_CYAN).withGlassStyle(), y);
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
                    active ? UiTheme.WELCOME_ACTIVE : UiTheme.WELCOME_FIELD,
                    active ? UiTheme.WELCOME_TEAL : UiTheme.WELCOME_LINE).withGlassStyle(), y + 6);
        }

        List<String> snippet = helperSnippetLines(helperPlatform);
        int copyY = y + helperCopyOffset(cardWidth);

        IconTextButton copyBtn = new IconTextButton(
                    x + cardWidth - 96, copyY, 96, 16, SpriteIcon.CODE,
                    Component.translatable("gui.serialcraft.welcome.copy"),
                    btn -> copyWithFeedback(btn, String.join("\n", snippet)),
                    UiTheme.WELCOME_FIELD, UiTheme.WELCOME_CYAN).withGlassStyle();
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
        UiDraw.glassSurface(gui, x, y, cardWidth, height, UiTheme.WELCOME_CARD, UiTheme.WELCOME_LINE, false);

        int textX = x + 10;
        int introY = y + 26;
        UiDraw.wrappedText(gui, font, helperIntro(), textX, introY, cardWidth - 20, UiTheme.WELCOME_MUTED);

        List<String> snippet = helperSnippetLines(helperPlatform);
        int codeBoxY = y + helperCodeOffset(cardWidth);
        int codeBoxH = snippet.size() * 11 + 8;
        UiDraw.glassSurface(gui, x + 8, codeBoxY, cardWidth - 16, codeBoxH,
                UiTheme.WELCOME_FIELD, UiTheme.WELCOME_LINE, false);

        boolean live = ConnectionManager.getWifi().isServerRunning();
        int lineY = codeBoxY + 5;
        for (String line : snippet) {
            gui.text(font, font.plainSubstrByWidth(line, cardWidth - 24),
                    x + 12, lineY, live ? UiTheme.WELCOME_GREEN : UiTheme.WELCOME_MUTED, false);
            lineY += 11;
        }

        if (!live) {
            UiDraw.wrappedText(gui, font, Component.translatable("gui.serialcraft.welcome.helper_needs_wifi"),
                    textX, codeBoxY + codeBoxH + 6, cardWidth - 20, UiTheme.WELCOME_ERROR);
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
                    UiTheme.WELCOME_CONNECT, UiTheme.WELCOME_GREEN, UiTheme.WELCOME_TEXT).withGlassStyle(), button.y());
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

        // Velo ligero: el mundo desenfocado también queda visible detrás del logo.
        gui.fill(0, 0, width, height, UiTheme.BG_WELCOME);

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

        Bounds frame = l.connectionPanel();
        UiDraw.glassSurface(gui, frame.x(), frame.y() - scrollY, frame.width(), frame.height(),
                UiTheme.WELCOME_PANEL, UiTheme.WELCOME_LINE, false);
        Bounds wifi = l.wifiInfo();
        renderWifiInfoPanel(gui, font, wifi.x(), wifi.width(), wifi.y() - scrollY, wifi.height());
        Component helpLabel = Component.translatable(showHelper
                ? "gui.serialcraft.welcome.helper_link_close" : "gui.serialcraft.welcome.helper_link_open");
        int helpWidth = Math.min(l.cardWidth(), font.width(helpLabel) + 36);
        int helpEdge = (l.cardWidth() - helpWidth) / 2 - 8;
        if (helpEdge > 0) {
            int dividerY = l.helpLink().y() - scrollY + HELPER_LINK_H / 2;
            gui.fill(l.cardX(), dividerY, l.cardX() + helpEdge, dividerY + 1, 0x406CA9C6);
            gui.fill(l.cardX() + l.cardWidth() - helpEdge, dividerY,
                    l.cardX() + l.cardWidth(), dividerY + 1, 0x406CA9C6);
        }
        if (showHelper) {
            Bounds help = l.helpPanel();
            renderHelperPanel(gui, font, help.x(), help.width(), help.y() - scrollY, help.height());
        }
        renderDeviceCards(gui, font, l, scrollY, mouseX,
                mouseY >= viewportTop && mouseY < viewportBottom ? mouseY : -1);

        gui.disableScissor();

        if (scroll.hasScroll()) {
            int trackX = l.scrollbarX();
            gui.fill(trackX, viewportTop, trackX + 4, viewportBottom, 0x30253A4A);
            int thumbY = scroll.getThumbY(viewportTop, viewportHeight);
            gui.fill(trackX, thumbY, trackX + 4, thumbY + scroll.getThumbHeight(viewportHeight), UiTheme.WELCOME_LINE);
        }
    }

    private void renderDeviceCards(GuiGraphicsExtractor gui, Font font, Layout layout, int scrollY,
                                    int mouseX, int mouseY) {
        Bounds first = devices.isEmpty() ? layout.emptyDevices() : layout.deviceRows().getFirst().card();
        int lastBottom = devices.isEmpty() ? first.bottom() : layout.deviceRows().getLast().card().bottom();
        UiDraw.glassSurface(gui, first.x() - FRAME_PADDING, first.y() - scrollY - 6,
                first.width() + FRAME_PADDING * 2, lastBottom - first.y() + 12,
                UiTheme.WELCOME_PANEL, UiTheme.WELCOME_LINE, false);
        if (devices.isEmpty()) {
            Bounds empty = layout.emptyDevices();
            int x = empty.x();
            int y = empty.y() - scrollY;
            UiDraw.glassSurface(gui, x, y, empty.width(), empty.height(),
                    UiTheme.WELCOME_CARD, UiTheme.WELCOME_LINE, false);
            int iconY = y + (empty.height() - 16) / 2;
            UiDraw.glassSurface(gui, x + 12, iconY, 16, 16, 0, UiTheme.WELCOME_CYAN, false);
            gui.text(font, "!", x + 18, iconY + 4, UiTheme.WELCOME_CYAN, false);
            Component title = Component.translatable("gui.serialcraft.welcome.no_usb");
            UiDraw.wrappedText(gui, font, title, x + 38, y + 8, empty.width() - 48, UiTheme.WELCOME_TEXT);
            int hintY = y + 12 + font.split(title, Math.max(1, empty.width() - 48)).size() * font.lineHeight;
            UiDraw.wrappedText(gui, font, Component.translatable("gui.serialcraft.welcome.no_usb_hint"),
                    x + 38, hintY, empty.width() - 48, UiTheme.WELCOME_MUTED);
            return;
        }

        for (int i = 0; i < devices.size(); i++) {
            PanelUI.DeviceInfo device = devices.get(i);
            Bounds card = layout.deviceRows().get(i).card();
            int x = card.x();
            int y = card.y() - scrollY;
            int cardWidth = card.width();
            boolean hovered = mouseX >= x && mouseX < card.right() && mouseY >= y && mouseY < y + card.height();
            UiDraw.glassSurface(gui, x, y, cardWidth, card.height(), UiTheme.WELCOME_CARD,
                    hovered ? UiTheme.WELCOME_CYAN : UiTheme.WELCOME_LINE, hovered);

            boolean wifi = device.isWifi();
            UiDraw.glassSurface(gui, x + 10, y + 13, 32, 22,
                    UiTheme.WELCOME_BADGE, 0, false);
            gui.text(font, device.type(), x + 14, y + 20,
                    wifi ? UiTheme.WELCOME_TEAL : UiTheme.WELCOME_CYAN, false);

            int textX = x + 54;
            int textWidth = Math.max(1, layout.deviceRows().get(i).connectButton().x() - textX - 8);
            UiDraw.clippedText(gui, font, Component.literal(device.name()), textX, y + 12, textWidth,
                    UiTheme.WELCOME_TEXT, mouseX, mouseY);
            UiDraw.clippedText(gui, font, Component.literal(device.address()), textX, y + 27, textWidth,
                    UiTheme.WELCOME_MUTED, mouseX, mouseY);
        }
    }

}
