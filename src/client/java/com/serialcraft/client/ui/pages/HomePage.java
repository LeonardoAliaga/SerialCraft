package com.serialcraft.client.ui.pages;

import com.serialcraft.client.ui.ScrollState;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.connection.WifiHandler;
import com.serialcraft.identity.BoardIdentity;
import com.serialcraft.screen.PanelUI;
import com.serialcraft.util.NetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pagina "Inicio": estado del dispositivo conectado, acciones de sesion y consola serial.
 *
 * Totalmente adaptada a resoluciones reducidas mediante layout responsivo y scroll vertical.
 */
public class HomePage implements Page {

    private static final int BASE_CARD_TOP = 48;
    private static final int CARD_HEIGHT   = 136;
    private static final int BUTTON_GAP    = 8;
    private static final int CONSOLE_LINES = 10;
    private static final int CONSOLE_H     = 108;

    private static long connectedAtMillis = 0L;

    private @Nullable PanelUI.DeviceInfo device;
    private @Nullable EditBox     commandBox;
    private @Nullable SolidButton trustButton;
    private @Nullable SolidButton disconnectButton;
    private @Nullable SolidButton sendButton;

    private int baseButtonsY;
    private int baseTerminalY;

    private final ScrollState scroll = new ScrollState();
    private int screenWidth;
    private int screenHeight;

    @Override
    public void init(PanelUI panel, int screenWidth, int screenHeight) {
        this.screenWidth  = screenWidth;
        this.screenHeight = screenHeight;
        this.device       = PanelUI.getSelectedDevice();

        if (device == null) return;
        if (connectedAtMillis == 0L) connectedAtMillis = System.currentTimeMillis();

        int x = UiTheme.contentX(screenWidth);
        int cardWidth = Math.max(160, UiTheme.contentWidth(screenWidth) - (scroll.hasScroll() ? 10 : 0));

        // ── Botones bajo la tarjeta ───────────────────────────────────────────
        this.baseButtonsY = BASE_CARD_TOP + CARD_HEIGHT + BUTTON_GAP;
        int halfW = (cardWidth - 8) / 2;

        disconnectButton = SolidButton.danger(
                x, baseButtonsY, halfW, 22,
                Component.translatable("gui.serialcraft.home.disconnect"),
                btn -> panel.disconnectDevice()
        );
        panel.addWidget(disconnectButton);

        trustButton = SolidButton.soft(
                x + halfW + 8, baseButtonsY, halfW, 22,
                Component.translatable("gui.serialcraft.home.remember"),
                btn -> toggleRemember()
        );
        trustButton.visible = device.isWifi();
        panel.addWidget(trustButton);

        // ── Consola y campo de envio ──────────────────────────────────────────
        int terminalTop = baseButtonsY + 28;
        this.baseTerminalY = terminalTop + 14 + CONSOLE_H + 8;

        Font font = Minecraft.getInstance().font;
        int sendBtnW = Math.clamp(cardWidth / 4, 60, 80);
        int cmdBoxW = Math.max(80, cardWidth - sendBtnW - 6);

        commandBox = new EditBox(font, x, baseTerminalY, cmdBoxW, 20,
                Component.translatable("gui.serialcraft.home.command"));
        commandBox.setMaxLength(64);
        commandBox.setTextColor(UiTheme.TEXT_INVERSE);
        panel.addWidget(commandBox);

        sendButton = SolidButton.success(
                x + cmdBoxW + 6, baseTerminalY, sendBtnW, 20,
                Component.translatable("gui.serialcraft.home.send"),
                btn -> submitCommand()
        );
        panel.addWidget(sendButton);
    }

    @Override
    public void tick() {
        if (trustButton == null) return;
        WifiHandler wifi = ConnectionManager.getWifi();
        boolean remembered = wifi.isCurrentRemembered();
        trustButton.visible = wifi.isConnected() && (remembered || wifi.canRemember());
        trustButton.setMessage(Component.translatable(remembered
                ? "gui.serialcraft.home.forget" : "gui.serialcraft.home.remember"));
    }

    private void toggleRemember() {
        WifiHandler wifi = ConnectionManager.getWifi();
        if (wifi.isCurrentRemembered()) wifi.forgetCurrentBoard();
        else                            wifi.rememberCurrentBoard();
    }

    private String boardTitle(PanelUI.DeviceInfo dev) {
        BoardIdentity id = ConnectionManager.activeIdentity();
        if (!id.hasModel()) return dev.name();
        return id.model() + " (" + dev.address() + ")";
    }

    private String boardPlatform(PanelUI.DeviceInfo dev) {
        String label = ConnectionManager.activeIdentity().platformLabel();
        return label.isEmpty() ? dev.platform() : label;
    }

    private void submitCommand() {
        if (commandBox == null) return;
        String text = commandBox.getValue().trim();
        if (text.isEmpty()) return;
        ConnectionManager.sendMessageToBoard(text);
        commandBox.setValue("");
    }

    // ── EVENTOS DE RATON Y DESPLAZAMIENTO ─────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return scroll.mouseScrolled(verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        int x = UiTheme.contentX(screenWidth);
        int cardWidth = Math.max(160, UiTheme.contentWidth(screenWidth) - (scroll.hasScroll() ? 10 : 0));
        int viewportTop = 40;
        int viewportBottom = screenHeight - UiTheme.contentMargin(screenWidth);
        int viewportHeight = viewportBottom - viewportTop;
        return scroll.mouseClicked(event.x(), event.y(), event.button(),
                x + cardWidth + 2, viewportTop, 8, viewportHeight);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return scroll.mouseReleased(event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        int viewportTop = 40;
        int viewportBottom = screenHeight - UiTheme.contentMargin(screenWidth);
        int viewportHeight = viewportBottom - viewportTop;
        return scroll.mouseDragged(event.y(), viewportTop, viewportHeight);
    }

    // ── RENDER ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font,
                       int screenWidth, int screenHeight) {
        int x = UiTheme.contentX(screenWidth);

        UiDraw.pageTitle(gui, font, x,
                Component.translatable("gui.serialcraft.home.title"), UiTheme.ACCENT_HOME,
                Component.translatable("gui.serialcraft.home.subtitle"));

        if (device == null) {
            gui.text(font, Component.translatable("gui.serialcraft.home.no_device"),
                    x, BASE_CARD_TOP, UiTheme.TEXT_SECONDARY, false);
            return;
        }

        int cardWidth = Math.max(160, UiTheme.contentWidth(screenWidth) - (scroll.hasScroll() ? 10 : 0));
        int viewportTop = 40;
        int viewportBottom = screenHeight - UiTheme.contentMargin(screenWidth);
        int viewportHeight = Math.max(10, viewportBottom - viewportTop);

        int totalContentHeight = baseTerminalY + 28 - BASE_CARD_TOP;
        scroll.update(viewportHeight, totalContentHeight);
        int scrollY = (int) scroll.getScrollAmount();

        // Actualizar widgets segun el scroll
        int btnY = baseButtonsY - scrollY;
        if (disconnectButton != null) {
            disconnectButton.setY(btnY);
            boolean inView = (btnY + disconnectButton.getHeight() >= viewportTop && btnY <= viewportBottom);
            disconnectButton.visible = inView;
            disconnectButton.active = inView;
        }
        if (trustButton != null) {
            trustButton.setY(btnY);
            boolean inView = (btnY + trustButton.getHeight() >= viewportTop && btnY <= viewportBottom);
            trustButton.visible = inView && device.isWifi();
            trustButton.active = inView;
        }

        int cmdY = baseTerminalY - scrollY;
        if (commandBox != null) {
            commandBox.setY(cmdY);
            boolean inView = (cmdY + commandBox.getHeight() >= viewportTop && cmdY <= viewportBottom);
            commandBox.visible = inView;
        }
        if (sendButton != null) {
            sendButton.setY(cmdY);
            boolean inView = (cmdY + sendButton.getHeight() >= viewportTop && cmdY <= viewportBottom);
            sendButton.visible = inView;
            sendButton.active = inView;
        }

        // Renderizado recortado dentro del viewport
        gui.enableScissor(x - 2, viewportTop, screenWidth, viewportBottom);

        renderStatusCard(gui, font, x, BASE_CARD_TOP - scrollY, cardWidth);
        renderConsole(gui, font, x, baseButtonsY + 28 - scrollY, cardWidth);

        gui.disableScissor();

        // Barra de desplazamiento
        if (scroll.hasScroll()) {
            scroll.renderScrollbar(gui, x + cardWidth + 3, viewportTop, 6, viewportHeight);
        }
    }

    private void renderStatusCard(GuiGraphicsExtractor gui, Font font, int x, int y, int cardWidth) {
        boolean wifi = device.isWifi();
        UiDraw.card(gui, x, y, cardWidth, CARD_HEIGHT);

        // Cabecera
        gui.fill(x, y, x + cardWidth, y + 30, wifi ? 0xFFE3F2FD : 0xFFF1F8E9);
        gui.fill(x + 10, y + 12, x + 16, y + 18, UiTheme.OK);

        int cursor = UiDraw.badge(gui, font, x + 20, y + 8,
                Component.translatable("gui.serialcraft.status.connected"),
                UiTheme.OK_BG, UiTheme.OK_DARK);

        cursor = UiDraw.badge(gui, font, cursor + 6, y + 8, boardPlatform(device),
                wifi ? UiTheme.INFO_BG : UiTheme.WARN_BG,
                wifi ? UiTheme.INFO_DARK : UiTheme.WARN_DARK);

        UiDraw.badge(gui, font, cursor + 6, y + 8, device.type(),
                wifi ? UiTheme.INFO_BG : UiTheme.NEUTRAL_BG,
                wifi ? UiTheme.INFO_DARK : UiTheme.NEUTRAL_TX);

        gui.text(font, font.plainSubstrByWidth(boardTitle(device), cardWidth - 24),
                x + 10, y + 36, UiTheme.TEXT_PRIMARY, false);
        gui.fill(x + 8, y + 49, x + cardWidth - 8, y + 50, UiTheme.LINE);

        int rowY = y + 56;
        if (wifi) renderWifiRows(gui, font, x + 10, rowY);
        else      renderUsbRows(gui, font, x + 10, rowY);

        gui.fill(x + 8, y + 110, x + cardWidth - 8, y + 111, UiTheme.LINE);

        long seconds = (System.currentTimeMillis() - connectedAtMillis) / 1000L;
        gui.text(font,
                Component.translatable("gui.serialcraft.home.uptime", formatDuration(seconds)),
                x + 10, y + 116, UiTheme.TEXT_MUTED, false);
    }

    private void renderWifiRows(GuiGraphicsExtractor gui, Font font, int x, int y) {
        WifiHandler wifi = ConnectionManager.getWifi();
        String remote = wifi.getRemoteIp();

        UiDraw.labelledRow(gui, font, x, y,
                Component.translatable("gui.serialcraft.home.board_ip"),
                remote.isEmpty() ? "—" : remote, UiTheme.TEXT_PRIMARY);

        UiDraw.labelledRow(gui, font, x, y + 13,
                Component.translatable("gui.serialcraft.home.host_ip"),
                NetUtils.findLocalIpv4() + ":" + WifiHandler.DEFAULT_PORT, UiTheme.INFO_DARK);

        UiDraw.labelledRow(gui, font, x, y + 26,
                Component.translatable("gui.serialcraft.home.token"),
                wifi.getPairingToken().isEmpty() ? "—" : wifi.getPairingToken(),
                UiTheme.WARN_DARK);

        WifiHandler.State state = wifi.getState();
        String key = switch (state) {
            case CONNECTED -> "gui.serialcraft.wifi.state.connected";
            case LISTENING -> "gui.serialcraft.wifi.state.listening";
            case STOPPED   -> "gui.serialcraft.wifi.state.stopped";
        };
        int color = switch (state) {
            case CONNECTED -> UiTheme.OK;
            case LISTENING -> UiTheme.INFO;
            case STOPPED   -> UiTheme.TEXT_MUTED;
        };
        UiDraw.labelledRow(gui, font, x, y + 39,
                Component.translatable("gui.serialcraft.home.server"),
                Component.translatable(key).getString(), color);
    }

    private void renderUsbRows(GuiGraphicsExtractor gui, Font font, int x, int y) {
        UiDraw.labelledRow(gui, font, x, y,
                Component.translatable("gui.serialcraft.home.port"),
                device.address(), UiTheme.TEXT_PRIMARY);

        UiDraw.labelledRow(gui, font, x, y + 13,
                Component.translatable("gui.serialcraft.home.baud"),
                ConnectionManager.getSerial().getBaudRate() + " bps", UiTheme.TEXT_PRIMARY);

        UiDraw.labelledRow(gui, font, x, y + 26,
                Component.translatable("gui.serialcraft.home.protocol"),
                Component.translatable("gui.serialcraft.home.protocol_usb").getString(),
                UiTheme.TEXT_PRIMARY);
    }

    private void renderConsole(GuiGraphicsExtractor gui, Font font, int x, int y, int cardWidth) {
        gui.text(font, Component.translatable("gui.serialcraft.home.terminal"),
                x, y, UiTheme.TEXT_PRIMARY, false);
        gui.fill(x, y + 12, x + cardWidth, y + 12 + CONSOLE_H, UiTheme.BG_CONSOLE);

        int lineY = y + 16;
        List<String> lines = ConnectionManager.recentHistory(CONSOLE_LINES);
        for (String line : lines) {
            int color = line.startsWith("TX:") ? UiTheme.OK
                      : line.startsWith("RX:") ? UiTheme.INFO
                      : UiTheme.ERROR;
            gui.text(font, font.plainSubstrByWidth(line, cardWidth - 12),
                    x + 6, lineY, color, false);
            lineY += 10;
        }
    }

    private static String formatDuration(long seconds) {
        if (seconds < 60)   return seconds + "s";
        if (seconds < 3600) return (seconds / 60) + "m " + (seconds % 60) + "s";
        return (seconds / 3600) + "h " + ((seconds % 3600) / 60) + "m";
    }
}
