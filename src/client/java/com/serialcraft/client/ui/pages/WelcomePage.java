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
    private static final int DEFAULT_CARD_WIDTH = 340;

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
    private int screenWidth;
    private int screenHeight;
    private String hostIp = "";
    private boolean showHelper = false;
    private int helperPlatform = PLATFORM_ESP32;

    private IconTextButton pendingCopyButton;
    private Component pendingCopyOriginalLabel;
    private long copyFeedbackUntilMs;

    private record Layout(int cardWidth, int cardX, int logoHeight, int subtitleY, int methodY,
                          int wifiInfoY, int wifiInfoH,
                          int helperLinkY, int helperPanelY, int helperPanelH,
                          int deviceListY, int totalHeight) {}

    private Layout layout(int screenWidth) {
        int cardWidth = Math.min(DEFAULT_CARD_WIDTH, screenWidth - 24);
        int cardX = (screenWidth - cardWidth) / 2;

        int logoWidth = Math.min(LOGO_WIDTH, screenWidth - 40);
        int logoHeight = (logoWidth * LOGO_SRC_H) / LOGO_SRC_W;
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
        int deviceCount = Math.max(1, devices.size());
        int totalHeight = deviceListY + (deviceCount * UiTheme.CARD_ROW_HEIGHT) + 20;

        return new Layout(cardWidth, cardX, logoHeight, subtitleY, methodY, wifiInfoY, wifiInfoH,
                helperLinkY, helperPanelY, helperPanelH, deviceListY, totalHeight);
    }

    // ── INIT ──────────────────────────────────────────────────────────────────

    @Override
    public void init(PanelUI panelUi, int width, int height) {
        this.panel        = panelUi;
        this.screenWidth  = width;
        this.screenHeight = height;
        this.scrollableWidgets.clear();

        if (ConnectionManager.getWifi().isServerRunning() && (hostIp == null || hostIp.isEmpty())) {
            hostIp = NetUtils.findLocalIpv4();
        }

        scanUsbPorts();
        Layout l = layout(width);

        buildMethodCards(panelUi, l.cardX, l.cardWidth, l.methodY);
        if (l.wifiInfoH > 0) buildWifiInfoWidgets(panelUi, l.cardX, l.cardWidth, l.wifiInfoY, l.wifiInfoH);
        buildHelperLink(panelUi, l.cardX, l.cardWidth, l.helperLinkY);
        if (showHelper) buildHelperPanel(panelUi, l.cardX, l.cardWidth, l.helperPanelY);
        buildDeviceButtons(panelUi, l.cardX, l.cardWidth, l.deviceListY);
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
        Layout l = layout(screenWidth);
        int trackX = l.cardX + l.cardWidth + 4;
        int trackY = 40;
        int trackH = screenHeight - 50;
        return scroll.mouseClicked(event.x(), event.y(), event.button(), trackX, trackY, 8, trackH);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return scroll.mouseReleased(event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        int trackY = 40;
        int trackH = screenHeight - 50;
        return scroll.mouseDragged(event.y(), trackY, trackH);
    }

    // ── TARJETAS DE METODO ────────────────────────────────────────────────────

    private void buildMethodCards(PanelUI panelUi, int x, int cardWidth, int y) {
        int cardW = (cardWidth - METHOD_GAP) / 2;

        long usbCount = devices.stream().filter(d -> "USB".equals(d.type())).count();
        Component usbStatus = usbCount == 0
                ? Component.translatable("gui.serialcraft.welcome.method_usb_empty")
                : Component.translatable("gui.serialcraft.welcome.method_usb_count", usbCount);

        addScrollWidget(panelUi, new MethodCard(x, y, cardW, METHOD_H, SpriteIcon.USB,
                Component.translatable("gui.serialcraft.welcome.method_usb"),
                usbStatus, UiTheme.TEXT_ON_DARK,
                UiTheme.TAB_INACTIVE_BG, UiTheme.TAB_INACTIVE_BORDER,
                () -> { scanUsbPorts(); panel.refreshWelcome(); }), y);

        WifiHandler wifi = ConnectionManager.getWifi();
        Component wifiStatus = switch (wifi.getState()) {
            case STOPPED   -> Component.translatable("gui.serialcraft.welcome.method_wifi_stopped");
            case LISTENING -> Component.translatable("gui.serialcraft.welcome.method_wifi_listening");
            case CONNECTED -> Component.translatable("gui.serialcraft.welcome.method_wifi_connected", wifi.getRemoteIp());
        };
        int wifiBg     = (wifi.getState() == WifiHandler.State.LISTENING) ? 0xFF00695C : UiTheme.TAB_INACTIVE_BG;
        int wifiBorder = (wifi.getState() == WifiHandler.State.LISTENING) ? 0xFF00897B : UiTheme.TAB_INACTIVE_BORDER;

        addScrollWidget(panelUi, new MethodCard(x + cardW + METHOD_GAP, y, cardW, METHOD_H, SpriteIcon.WIFI,
                Component.translatable("gui.serialcraft.welcome.method_wifi"),
                wifiStatus, UiTheme.TEXT_ON_DARK, wifiBg, wifiBorder,
                this::toggleWifiServer), y);
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
                    0xFF37474F, 0xFF455A64, UiTheme.TEXT_INVERSE), y + 22);

            addScrollWidget(panelUi, new IconTextButton(
                    btnX, y + 44, btnW, btnH, null,
                    Component.translatable("gui.serialcraft.welcome.copy"),
                    btn -> copyWithFeedback(btn, String.valueOf(WifiHandler.DEFAULT_PORT)),
                    0xFF37474F, 0xFF455A64, UiTheme.TEXT_INVERSE), y + 44);

            addScrollWidget(panelUi, new IconTextButton(
                    btnX, y + 66, btnW, btnH, null,
                    Component.translatable("gui.serialcraft.welcome.copy"),
                    btn -> copyWithFeedback(btn, wifi.getPairingToken()),
                    0xFF00695C, 0xFF00897B, UiTheme.TEXT_INVERSE), y + 66);

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

        if (wifi.getState() == WifiHandler.State.LISTENING) {
            UiDraw.card(gui, x, y, cardWidth, h);

            gui.fill(x, y, x + cardWidth, y + 17, 0xFFE1F5FE);
            gui.fill(x, y + 17, x + cardWidth, y + 18, 0xFFB3E5FC);

            gui.fill(x + 8, y + 5, x + 14, y + 11, UiTheme.OK);
            gui.text(font, Component.translatable("gui.serialcraft.welcome.wifi_server_title"),
                    x + 18, y + 4, UiTheme.INFO_DARK, false);

            int valBoxX = x + 58;
            int valBoxW = cardWidth - 58 - 66;

            UiDraw.badge(gui, font, x + 8, y + 23,
                    Component.translatable("gui.serialcraft.welcome.wifi_host"),
                    0xFFECEFF1, UiTheme.TEXT_SECONDARY);
            gui.fill(valBoxX, y + 22, valBoxX + valBoxW, y + 38, 0xFFF5F5F5);
            gui.outline(valBoxX, y + 22, valBoxW, 16, UiTheme.LINE_SOFT);
            gui.text(font, getEffectiveHostIp(), valBoxX + 6, y + 26, UiTheme.TEXT_PRIMARY, false);

            UiDraw.badge(gui, font, x + 8, y + 45,
                    Component.translatable("gui.serialcraft.welcome.wifi_port"),
                    0xFFECEFF1, UiTheme.TEXT_SECONDARY);
            gui.fill(valBoxX, y + 44, valBoxX + valBoxW, y + 60, 0xFFF5F5F5);
            gui.outline(valBoxX, y + 44, valBoxW, 16, UiTheme.LINE_SOFT);
            gui.text(font, String.valueOf(WifiHandler.DEFAULT_PORT), valBoxX + 6, y + 48, UiTheme.TEXT_PRIMARY, false);

            UiDraw.badge(gui, font, x + 8, y + 67,
                    Component.translatable("gui.serialcraft.welcome.wifi_token"),
                    0xFFE0F2F1, UiTheme.ACCENT_PRIMARY_DARK);
            gui.fill(valBoxX, y + 66, valBoxX + valBoxW, y + 82, 0xFFE8F5E9);
            gui.outline(valBoxX, y + 66, valBoxW, 16, 0xFFA5D6A7);
            gui.text(font, wifi.getPairingToken(), valBoxX + 6, y + 70, UiTheme.OK_DARK, false);

            int remembered = BoardTrust.store().all().size();
            if (remembered > 0) {
                gui.text(font, Component.translatable("gui.serialcraft.welcome.wifi_remembered", remembered),
                        x + 8, y + 85, UiTheme.TEXT_SECONDARY, false);
            }

        } else if (wifi.getState() == WifiHandler.State.CONNECTED) {
            UiDraw.card(gui, x, y, cardWidth, h);
            gui.fill(x, y, x + 4, y + h, UiTheme.OK);

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
        int linkWidth = Minecraft.getInstance().font.width(text) + 24;

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
        int codeBoxY = y + 26 + 14;
        int codeBoxH = snippet.size() * 11 + 8;

        IconTextButton copyBtn = new IconTextButton(
                    x + cardWidth - 96, codeBoxY + codeBoxH + 6, 96, 16, SpriteIcon.CODE,
                    Component.translatable("gui.serialcraft.welcome.helper_copy"),
                    btn -> copyWithFeedback(btn, String.join("\n", snippet)),
                    UiTheme.TAB_INACTIVE_BG, UiTheme.TAB_INACTIVE_BORDER);
        addScrollWidget(panelUi, copyBtn, codeBoxY + codeBoxH + 6);
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

    private void renderHelperPanel(GuiGraphicsExtractor gui, Font font, int x, int cardWidth, int y) {
        UiDraw.card(gui, x, y, cardWidth, HELPER_PANEL_H);
        gui.outline(x, y, cardWidth, HELPER_PANEL_H, 0xFF455A64);

        int textX = x + 10;
        int introY = y + 26;
        gui.text(font, Component.translatable("gui.serialcraft.welcome.helper_intro"),
                textX, introY, UiTheme.TEXT_SECONDARY, false);

        List<String> snippet = helperSnippetLines(helperPlatform);
        int codeBoxY = introY + 12;
        int codeBoxH = snippet.size() * 11 + 8;
        gui.fill(x + 8, codeBoxY, x + cardWidth - 8, codeBoxY + codeBoxH, UiTheme.BG_CONSOLE);
        gui.outline(x + 8, codeBoxY, cardWidth - 16, codeBoxH, 0xFF37474F);

        boolean live = ConnectionManager.getWifi().isServerRunning();
        int lineY = codeBoxY + 5;
        for (String line : snippet) {
            gui.text(font, font.plainSubstrByWidth(line, cardWidth - 24),
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

    private void buildDeviceButtons(PanelUI panelUi, int x, int cardWidth, int y) {
        for (PanelUI.DeviceInfo device : devices) {
            final PanelUI.DeviceInfo target = device;
            addScrollWidget(panelUi, new IconTextButton(
                    x + cardWidth - 92, y + 14, 86, 20, SpriteIcon.CONNECT,
                    Component.translatable("gui.serialcraft.welcome.connect"),
                    btn -> panelUi.connectDevice(target),
                    0xFF2E7D32, 0xFF388E3C, UiTheme.TEXT_INVERSE), y + 14);
            y += UiTheme.CARD_ROW_HEIGHT;
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
        Layout l = layout(width);
        int logoWidth = Math.min(LOGO_WIDTH, width - 40);
        int logoX = (width - logoWidth) / 2;

        int headerH = l.subtitleY + 12;

        // Barra superior estática
        gui.fill(0, 0, width, headerH, UiTheme.BG_NAV);
        gui.fill(0, headerH, width, headerH + 2, 0x20000000);

        gui.blit(RenderPipelines.GUI_TEXTURED, LOGO_TEXTURE,
                logoX, LOGO_Y, 0, 0, logoWidth, l.logoHeight,
                LOGO_SRC_W, LOGO_SRC_H, LOGO_SRC_W, LOGO_SRC_H);

        Component subtitle = Component.translatable("gui.serialcraft.welcome.subtitle");
        gui.text(font, subtitle, (width - font.width(subtitle)) / 2, l.subtitleY, 0xFFE0F7FA, false);

        int viewportTop = headerH + 4;
        int viewportBottom = height - 4;
        int viewportHeight = Math.max(10, viewportBottom - viewportTop);

        int totalContent = l.totalHeight - viewportTop;
        scroll.update(viewportHeight, totalContent);
        int scrollY = (int) scroll.getScrollAmount();

        // Actualizar widgets según el scroll
        for (ScrollableWidget sw : scrollableWidgets) {
            int currentY = sw.baseY() - scrollY;
            sw.widget().setY(currentY);
            boolean inView = (currentY + sw.widget().getHeight() >= viewportTop && currentY <= viewportBottom);
            sw.widget().visible = inView;
            sw.widget().active  = inView;
        }

        // Renderizado recortado dentro del viewport
        gui.enableScissor(0, viewportTop, width, viewportBottom);

        renderWifiInfoPanel(gui, font, l.cardX, l.cardWidth, l.wifiInfoY - scrollY, l.wifiInfoH);
        if (showHelper) renderHelperPanel(gui, font, l.cardX, l.cardWidth, l.helperPanelY - scrollY);
        renderDeviceCards(gui, font, l.cardX, l.cardWidth, l.deviceListY - scrollY);

        gui.disableScissor();

        // Barra de desplazamiento
        if (scroll.hasScroll()) {
            scroll.renderScrollbar(gui, l.cardX + l.cardWidth + 4, viewportTop, 6, viewportHeight);
        }
    }

    private void renderDeviceCards(GuiGraphicsExtractor gui, Font font, int x, int cardWidth, int y) {
        if (devices.isEmpty()) {
            UiDraw.card(gui, x, y, cardWidth, 44);
            gui.text(font, Component.translatable("gui.serialcraft.welcome.no_usb"),
                    x + 12, y + 10, UiTheme.TEXT_SECONDARY, false);
            gui.text(font, Component.translatable("gui.serialcraft.welcome.no_usb_hint"),
                    x + 12, y + 24, UiTheme.TEXT_MUTED, false);
            return;
        }

        for (PanelUI.DeviceInfo device : devices) {
            UiDraw.card(gui, x, y, cardWidth, UiTheme.CARD_HEIGHT);

            boolean wifi = device.isWifi();
            UiDraw.badge(gui, font, x + 10, y + 14, device.type(),
                    wifi ? UiTheme.INFO_BG   : UiTheme.NEUTRAL_BG,
                    wifi ? UiTheme.INFO_DARK : UiTheme.NEUTRAL_TX);

            int nameW = Math.max(40, cardWidth - 150);
            gui.text(font, font.plainSubstrByWidth(device.name(), nameW),
                    x + 50, y + 13, UiTheme.TEXT_PRIMARY, false);
            gui.text(font, device.address(), x + 50, y + 27, UiTheme.TEXT_SECONDARY, false);

            y += UiTheme.CARD_ROW_HEIGHT;
        }
    }
}
