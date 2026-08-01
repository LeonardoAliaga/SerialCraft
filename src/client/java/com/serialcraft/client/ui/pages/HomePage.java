package com.serialcraft.client.ui.pages;

import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.connection.WifiHandler;
import com.serialcraft.screen.PanelUI;
import com.serialcraft.util.NetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Pagina de inicio: estado de la conexion y terminal bidireccional.
 */
public class HomePage implements Page {

    private static final int CARD_WIDTH   = 320;
    private static final int CARD_HEIGHT  = 140;
    private static final int CARD_TOP     = 60;
    private static final int BUTTON_GAP   = 12;
    private static final int CONSOLE_H    = 93;
    private static final int CONSOLE_LINES = 8;

    private @Nullable PanelUI.DeviceInfo device;
    private @Nullable EditBox commandBox;
    private long connectedAtMillis;

    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void init(PanelUI panel, int screenWidth, int screenHeight) {
        this.device            = PanelUI.getSelectedDevice();
        this.connectedAtMillis = System.currentTimeMillis();

        int x = UiTheme.contentX(screenWidth) + 10;
        int buttonY = CARD_TOP + CARD_HEIGHT + BUTTON_GAP;

        panel.addWidget(new IconTextButton(
                x, buttonY, 156, 24, SpriteIcon.DISCONNECT,
                Component.translatable("gui.serialcraft.home.disconnect"),
                btn -> panel.disconnectDevice(),
                UiTheme.ACCENT_HOME, UiTheme.ACCENT_HOME_BORDER, UiTheme.TEXT_INVERSE
        ));

        if (device == null) return;

        // ── Terminal ──────────────────────────────────────────────────────
        int terminalY = buttonY + 35 + 110;

        commandBox = new EditBox(Minecraft.getInstance().font,
                x, terminalY, CARD_WIDTH - 85, 20,
                Component.translatable("gui.serialcraft.home.command"));
        commandBox.setMaxLength(64);   // igual al limite del payload
        commandBox.setTextColor(UiTheme.TEXT_INVERSE);
        panel.addWidget(commandBox);

        panel.addWidget(SolidButton.success(
                x + CARD_WIDTH - 80, terminalY, 80, 20,
                Component.translatable("gui.serialcraft.home.send"),
                btn -> submitCommand()
        ));
    }

    private void submitCommand() {
        if (commandBox == null) return;
        String text = commandBox.getValue().trim();
        if (text.isEmpty()) return;
        ConnectionManager.sendMessageToBoard(text);
        commandBox.setValue("");
    }

    // ══════════════════════════════════════════════════════════════════════

    @Override
    public void render(GuiGraphics gui, int mouseX, int mouseY, Font font,
                       int screenWidth, int screenHeight) {
        int x = UiTheme.contentX(screenWidth) + 10;

        UiDraw.pageTitle(gui, font, x,
                Component.translatable("gui.serialcraft.home.title"), UiTheme.ACCENT_HOME,
                Component.translatable("gui.serialcraft.home.subtitle"));

        if (device == null) {
            gui.drawString(font, Component.translatable("gui.serialcraft.home.no_device"),
                    x, CARD_TOP, UiTheme.TEXT_SECONDARY, false);
            return;
        }

        renderStatusCard(gui, font, x);
        renderConsole(gui, font, x);
    }

    private void renderStatusCard(GuiGraphics gui, Font font, int x) {
        int y = CARD_TOP;
        boolean wifi = device.isWifi();

        UiDraw.card(gui, x, y, CARD_WIDTH, CARD_HEIGHT);

        // Cabecera
        gui.fill(x, y, x + CARD_WIDTH, y + 30, wifi ? 0xFFE3F2FD : 0xFFF1F8E9);
        gui.fill(x + 12, y + 12, x + 18, y + 18, UiTheme.OK);

        int cursor = UiDraw.badge(gui, font, x + 24, y + 8,
                Component.translatable("gui.serialcraft.status.connected"),
                UiTheme.OK_BG, UiTheme.OK_DARK);

        cursor = UiDraw.badge(gui, font, cursor + 6, y + 8, device.platform(),
                wifi ? UiTheme.INFO_BG : UiTheme.WARN_BG,
                wifi ? UiTheme.INFO_DARK : UiTheme.WARN_DARK);

        UiDraw.badge(gui, font, cursor + 6, y + 8, device.type(),
                wifi ? UiTheme.INFO_BG : UiTheme.NEUTRAL_BG,
                wifi ? UiTheme.INFO_DARK : UiTheme.NEUTRAL_TX);

        gui.drawString(font, device.name(), x + 12, y + 36, UiTheme.TEXT_PRIMARY, false);
        gui.fill(x + 10, y + 50, x + CARD_WIDTH - 10, y + 51, UiTheme.LINE);

        int rowY = y + 58;
        if (wifi) renderWifiRows(gui, font, x + 12, rowY);
        else      renderUsbRows(gui, font, x + 12, rowY);

        gui.fill(x + 10, y + 112, x + CARD_WIDTH - 10, y + 113, UiTheme.LINE);

        long seconds = (System.currentTimeMillis() - connectedAtMillis) / 1000L;
        gui.drawString(font,
                Component.translatable("gui.serialcraft.home.uptime", formatDuration(seconds)),
                x + 12, y + 118, UiTheme.TEXT_MUTED, false);
    }

    private void renderWifiRows(GuiGraphics gui, Font font, int x, int y) {
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

    private void renderUsbRows(GuiGraphics gui, Font font, int x, int y) {
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

    private void renderConsole(GuiGraphics gui, Font font, int x) {
        int y = CARD_TOP + CARD_HEIGHT + BUTTON_GAP + 35;

        gui.drawString(font, Component.translatable("gui.serialcraft.home.terminal"),
                x, y, UiTheme.TEXT_PRIMARY, false);
        gui.fill(x, y + 12, x + CARD_WIDTH, y + 12 + CONSOLE_H, UiTheme.BG_CONSOLE);

        int lineY = y + 18;
        List<String> lines = ConnectionManager.recentHistory(CONSOLE_LINES);
        for (String line : lines) {
            int color = line.startsWith("TX:") ? UiTheme.OK
                      : line.startsWith("RX:") ? UiTheme.INFO
                      : UiTheme.ERROR;
            // Recorte al ancho de la consola: sin esto una linea larga de la
            // placa se dibujaba fuera del recuadro negro.
            gui.drawString(font, font.plainSubstrByWidth(line, CARD_WIDTH - 12),
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
