package com.serialcraft.client.ui.pages;

import com.serialcraft.client.ui.ScrollState;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.client.ui.io.IoEditorLayout.Rect;
import com.serialcraft.client.ui.widget.ContextHelpDialog;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.client.ui.widget.OptionButton;
import com.serialcraft.client.ui.widget.OutlineButton;
import com.serialcraft.client.ui.widget.UiViewport;
import com.serialcraft.config.SerialConfig;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.connection.ConnectionResult;
import com.serialcraft.connection.WifiHandler;
import com.serialcraft.identity.BoardIdentity;
import com.serialcraft.identity.WifiHandshake;
import com.serialcraft.screen.PanelUI;
import com.serialcraft.util.NetUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Native hardware dashboard. Measured sections share one clipped input viewport. */
public class HomePage implements Page {
    private static final int PAD = UiTheme.CARD_PADDING;
    private static final int GAP = UiTheme.SECTION_GAP;
    private static final int HISTORY_LIMIT = 64;
    private static final int LINE_HEIGHT = 11;

    // Presentation snapshot only: the transports remain the owners of connection state.
    private record LinkState(boolean wifi, boolean connected, int epoch, BoardIdentity identity,
                             BoardIdentity.Bridge bridge, int baud, String endpoint, String identifiers,
                             WifiHandler.State server, int serverPort, WifiHandshake.Method authentication, boolean remembered,
                             boolean rememberable, boolean usbRememberable, boolean hasToken) {}
    private record Surface(Rect bounds, int accent) {}
    private record Text(Component value, int x, int y, int width, int color, int background) {}
    private record Property(Component label, Component value, int x, int y, int width, int color) {}
    private record Heading(Component title, int x, int y, int width, int accent) {}
    private final List<Surface> surfaces = new ArrayList<>();
    private final List<Text> texts = new ArrayList<>();
    private final List<Property> properties = new ArrayList<>();
    private final List<Heading> headings = new ArrayList<>();
    private final Map<String, AbstractWidget> focusWidgets = new LinkedHashMap<>();
    private final ScrollState scroll = new ScrollState();
    private final ScrollState historyScroll = new ScrollState();
    private List<String> history = List.of();
    private PanelUI panel;
    private Font font;
    private UiViewport viewport;
    private Rect view, summary, terminal, console, modelBadge;
    private LinkState state;
    private EditBox commandBox;
    private ContextHelpDialog dialog;
    private String focusKey = "", navigationFocus = "", usbPort = "", hostAddress = "";
    private int contentX, contentWidth, summaryFooterY;
    private int pendingBaud;
    private boolean showToken, resultAccepted;
    private Component resultMessage;
    private CompletableFuture<ConnectionResult> reconnect;

    @Override
    public void init(PanelUI panel, int screenWidth, int screenHeight) {
        this.panel = panel;
        font = Minecraft.getInstance().font;
        String commandDraft = commandBox == null ? "" : commandBox.getValue();
        LinkState previous = state;
        state = readState();
        if (previous == null || previous.epoch() != state.epoch() || previous.wifi() != state.wifi()) showToken = false;
        if (previous != null && previous.wifi() != state.wifi()) resultMessage = null;
        if (!state.wifi() && !state.endpoint().isEmpty() && !usbPort.equals(state.endpoint())) {
            usbPort = state.endpoint(); pendingBaud = 0; resultMessage = null;
        }
        if (pendingBaud == 0) pendingBaud = state.baud();
        if (state.wifi()) hostAddress = NetUtils.findLocalIpv4();
        surfaces.clear(); texts.clear(); properties.clear(); headings.clear(); focusWidgets.clear();
        if (dialog != null) panel.clearUiWidgets();

        contentX = UiTheme.contentX(screenWidth);
        contentWidth = Math.max(80, screenWidth - contentX - UiTheme.contentMargin(screenWidth) - 10);
        int top = 40;
        view = new Rect(contentX - 2, top, contentWidth + 12,
                Math.max(24, screenHeight - UiTheme.contentMargin(screenWidth) - top));
        viewport = new UiViewport(scroll, view);
        int zoneY = buildSummary(top + 8) + GAP;
        boolean columns = contentWidth >= 530;
        int leftWidth = columns ? (contentWidth - GAP) * 55 / 100 : contentWidth;
        int leftBottom = buildProperties(contentX, zoneY, leftWidth) + GAP;
        if (state.wifi()) leftBottom = buildWifi(contentX, leftBottom, leftWidth);
        else if (!state.endpoint().isEmpty()) leftBottom = buildUsb(contentX, leftBottom, leftWidth);
        int terminalX = columns ? contentX + leftWidth + GAP : contentX;
        int terminalY = columns ? zoneY : leftBottom + GAP;
        int terminalWidth = columns ? contentWidth - leftWidth - GAP : contentWidth;
        int terminalBottom = buildTerminal(terminalX, terminalY, terminalWidth,
                columns ? Math.clamp(leftBottom - zoneY, 260, 420) : 242, commandDraft);
        viewport.size(Math.max(leftBottom, terminalBottom) - view.y() + 8);
        updateHistory();
        if (dialog != null) dialog.build(panel, screenWidth, screenHeight, font);
        else {
            AbstractWidget focused = focusWidgets.get(focusKey);
            if (focused != null && !focused.active) focused = focusWidgets.get(
                    focusKey.equals("trust") ? "help.wifi" : focusKey.equals("apply") || focusKey.startsWith("baud.")
                            ? "help.baud" : "help.identity");
            if (focused == null && !navigationFocus.isEmpty())
                for (var child : panel.children())
                    if (child instanceof AbstractWidget widget && widget.getMessage().getString().equals(navigationFocus)) {
                        focused = widget; break;
                    }
            if (focused != null && focused.active) {
                viewport.prepareKeyboardFocus(); panel.setFocused(focused); viewport.revealFocus(focused);
            }
        }
    }

    private LinkState readState() {
        var serial = ConnectionManager.getSerial();
        var wifi = ConnectionManager.getWifi();
        var device = PanelUI.getSelectedDevice();
        boolean wireless = wifi.isConnected() || (!serial.isConnected() && device != null && device.isWifi());
        boolean connected = wireless ? wifi.isConnected() : serial.isConnected();
        String endpoint = connected ? wireless ? wifi.getRemoteIp() : serial.getPortName()
                : device == null ? "" : device.address();
        return new LinkState(wireless, connected, ConnectionManager.sessionEpoch(),
                connected ? ConnectionManager.activeIdentity() : BoardIdentity.unknown(),
                !wireless && connected ? serial.getIdentity().bridge() : BoardIdentity.Bridge.NONE,
                wireless ? 0 : serial.getBaudRate(), endpoint, wireless ? "" : serial.getUsbIdentifiers(),
                wifi.getState(), wifi.getServerPort(), wifi.getAuthenticationMethod(), wifi.isCurrentRemembered(), wifi.canRemember(),
                !wireless && serial.canRememberSettings(), wireless && !wifi.getPairingToken().isEmpty());
    }

    private int buildSummary(int top) {
        Component disconnect = tr("disconnect");
        int buttonWidth = Math.min(contentWidth - PAD * 2, font.width(disconnect) + 24);
        boolean beside = contentWidth >= 400;
        int buttonY = top + (beside ? 35 : 80);
        var button = SolidButton.danger(beside ? contentX + contentWidth - PAD - buttonWidth : contentX + PAD,
                buttonY, buttonWidth, 24, disconnect, b -> panel.disconnectDevice());
        button.active = !usbBusy() && (!state.endpoint().isEmpty() || state.wifi());
        button.setTooltip(Tooltip.create(disconnect));
        input("disconnect", button, buttonY);
        summaryFooterY = top + (beside ? 82 : 114);
        summary = new Rect(contentX, top, contentWidth, summaryFooterY - top + 26);
        return summary.bottom();
    }

    private int buildProperties(int x, int top, int width) {
        int left = x + PAD, inner = width - PAD * 2;
        int y = sectionStart(tr("usb.title"), x, top, width, "identity");
        y = paragraph(tr("properties.subtitle"), left, y, inner, UiTheme.TEXT_SECONDARY) + 10;
        y = heading(tr("detected"), left, y, inner, UiTheme.ACCENT_HOME);
        int modelY = y;
        y = property(tr("model"), modelName(), left, y, inner - 36, UiTheme.TEXT_PRIMARY);
        help("model", left + inner - 28, modelY);
        y = Math.max(y, modelY + 28);
        modelBadge = new Rect(left, y, inner, 14);
        y += 22;
        y = property(tr("platform"), family(), left, y, inner, UiTheme.TEXT_PRIMARY);
        if (state.bridge() != BoardIdentity.Bridge.NONE)
            y = property(tr("bridge"), Component.literal(state.bridge().name()), left, y, inner, UiTheme.TEXT_PRIMARY);
        if (!state.identifiers().isEmpty())
            y = property(tr("usb.identifiers"), Component.literal(state.identifiers()), left, y, inner, UiTheme.TEXT_SECONDARY);
        if (state.connected() && !confirmedModel() && state.bridge() != BoardIdentity.Bridge.NONE)
            y = notice(tr("model.warning"), left, y + 2, inner, UiTheme.WARN_BG, UiTheme.WARN_DARK) + 8;
        y = heading(tr("connection_state"), left, y + 4, inner, UiTheme.ACCENT_PRIMARY);
        if (state.wifi()) {
            y = property(tr("board_ip"), state.connected() ? value(state.endpoint()) : unavailable(), left, y, inner, UiTheme.TEXT_PRIMARY);
            y = property(tr("host_ip"), state.serverPort() > 0
                    ? Component.literal(hostAddress + ":" + state.serverPort()) : unavailable(), left, y, inner, UiTheme.INFO_DARK);
            y = property(tr("server"), serverState(), left, y, inner,
                    state.connected() ? UiTheme.OK_DARK : state.server() == WifiHandler.State.LISTENING ? UiTheme.INFO_DARK : UiTheme.TEXT_SECONDARY);
            y = property(tr("protocol"), tr("protocol_wifi"), left, y, inner, UiTheme.TEXT_PRIMARY);
            if (state.hasToken()) {
                y = property(tr("token"), showToken ? value(ConnectionManager.getWifi().getPairingToken()) : tr("token.hidden"),
                        left, y, inner, UiTheme.TEXT_PRIMARY);
                Component label = tr(showToken ? "token.hide" : "token.show");
                var token = new OutlineButton(left, y, Math.min(inner, font.width(label) + 24), 22,
                        label, b -> { showToken = !showToken; focusKey = "token"; panel.refresh(); });
                input("token", token, y); y += 30;
            }
        } else {
            y = property(tr("port"), state.connected() ? value(state.endpoint()) : unavailable(), left, y, inner, UiTheme.TEXT_PRIMARY);
            y = property(tr("baud"), baud(), left, y, inner, UiTheme.TEXT_PRIMARY);
            y = property(tr("protocol"), tr("protocol_usb"), left, y, inner, UiTheme.TEXT_PRIMARY);
        }
        y = property(tr("usb.state"), connectionState(), left, y, inner, statusColor());
        surfaces.add(new Surface(new Rect(x, top, width, y + PAD - top), UiTheme.ACCENT_HOME));
        return y + PAD;
    }

    private int buildUsb(int x, int top, int width) {
        int left = x + PAD, inner = width - PAD * 2;
        int y = sectionStart(tr("usb.settings"), x, top, width, "baud");
        y = property(tr("usb.current_baud"), baud(), left, y, inner, UiTheme.ACCENT_PRIMARY_DARK);
        y = paragraph(tr("usb.baud_selector"), left, y + 2, inner, UiTheme.TEXT_PRIMARY) + 6;
        int columns = Math.clamp((inner + 4) / 76, 1, SerialConfig.USB_BAUD_RATES.size());
        int buttonWidth = (inner - (columns - 1) * 4) / columns;
        int buttonHeight = Math.max(24, textHeight(Component.literal("230400"), buttonWidth - 24) + 8);
        for (int i = 0; i < SerialConfig.USB_BAUD_RATES.size(); i++) {
            int baud = SerialConfig.USB_BAUD_RATES.get(i);
            int buttonY = y + i / columns * (buttonHeight + 4);
            var button = new OptionButton(left + i % columns * (buttonWidth + 4), buttonY, buttonWidth, buttonHeight,
                    Component.literal(Integer.toString(baud)), pendingBaud == baud, UiTheme.ACCENT_PRIMARY, b -> {
                        pendingBaud = baud; resultMessage = null; focusKey = "baud." + baud; panel.refresh();
                    });
            button.active = state.connected() && !usbBusy();
            input("baud." + baud, button, buttonY);
        }
        y += ((SerialConfig.USB_BAUD_RATES.size() + columns - 1) / columns) * (buttonHeight + 4) + 6;
        y = paragraph(tr("usb.baud_hint"), left, y, inner, UiTheme.TEXT_SECONDARY) + 8;
        if (pendingBaud > 0 && (pendingBaud != state.baud() || usbBusy())) {
            y = notice(usbBusy() ? tr("usb.reconnecting") : tr("usb.pending", pendingBaud), left, y, inner,
                    UiTheme.WARN_BG, UiTheme.WARN_DARK) + 8;
        }
        Component applyLabel = tr(usbBusy() ? "usb.reconnecting" : "usb.apply");
        var apply = new OutlineButton(left, y, inner, 24, applyLabel, b -> applyBaud());
        apply.active = state.connected() && !usbBusy() && pendingBaud > 0 && pendingBaud != state.baud();
        apply.setTooltip(Tooltip.create(applyLabel));
        input("apply", apply, y); y += 32;
        y = paragraph(tr(state.usbRememberable() ? "usb.remembered" : "usb.session_only"), left, y, inner, UiTheme.TEXT_SECONDARY) + 8;
        if (resultMessage != null) y = notice(resultMessage, left, y, inner,
                resultAccepted ? UiTheme.OK_BG : UiTheme.ERROR_BG, resultAccepted ? UiTheme.OK_DARK : UiTheme.ERROR_DARK) + 4;
        surfaces.add(new Surface(new Rect(x, top, width, y + PAD - top), UiTheme.ACCENT_PRIMARY));
        return y + PAD;
    }

    private int buildWifi(int x, int top, int width) {
        int left = x + PAD, inner = width - PAD * 2;
        int y = sectionStart(tr("wifi.session"), x, top, width, "wifi");
        y = property(tr("wifi.authentication"), tr("wifi.auth." + state.authentication().name().toLowerCase(Locale.ROOT)),
                left, y, inner, state.connected() ? UiTheme.OK_DARK : UiTheme.TEXT_SECONDARY);
        y = property(tr("wifi.trust"), !state.connected() ? unavailable() : tr(state.remembered() ? "wifi.remembered" : "wifi.temporary"),
                left, y, inner, state.remembered() ? UiTheme.OK_DARK : UiTheme.TEXT_PRIMARY);
        Component label = tr(state.remembered() ? "forget" : "remember");
        var trust = new OutlineButton(left, y, inner, 24, label, b -> toggleRemember());
        trust.active = state.connected() && (state.remembered() || state.rememberable());
        trust.setTooltip(Tooltip.create(tr(state.remembered() ? "wifi.forget_hint" : "wifi.remember_hint")));
        input("trust", trust, y); y += 32;
        y = paragraph(tr(state.remembered() ? "wifi.forget_hint" : state.rememberable()
                ? "wifi.remember_hint" : "wifi.unavailable"), left, y, inner, UiTheme.TEXT_SECONDARY) + 8;
        if (resultMessage != null) y = notice(resultMessage, left, y, inner,
                resultAccepted ? UiTheme.OK_BG : UiTheme.ERROR_BG, resultAccepted ? UiTheme.OK_DARK : UiTheme.ERROR_DARK) + 4;
        surfaces.add(new Surface(new Rect(x, top, width, y + PAD - top), UiTheme.ACCENT_PRIMARY));
        return y + PAD;
    }

    private int buildTerminal(int x, int top, int width, int height, String commandDraft) {
        int titleHeight = textHeight(tr("terminal"), width - PAD * 2 - 28);
        int consoleTop = top + PAD + Math.max(20, titleHeight) + 10;
        terminal = new Rect(x, top, width, Math.max(height, consoleTop - top + 146));
        console = new Rect(x + PAD, consoleTop, width - PAD * 2, terminal.bottom() - consoleTop - 44);
        int commandY = terminal.bottom() - 32;
        int sendWidth = font.width(tr("send")) + 20;
        int commandWidth = width - PAD * 2 - sendWidth - 6;
        commandBox = new EditBox(font, x + PAD, commandY, commandWidth, 22, tr("command"));
        commandBox.setMaxLength(64); commandBox.setTextColor(UiTheme.TEXT_INVERSE);
        commandBox.setValue(commandDraft); commandBox.setHint(tr("command"));
        commandBox.setTooltip(Tooltip.create(tr("terminal.hint")));
        input("command", commandBox, commandY);
        var send = SolidButton.success(x + PAD + commandWidth + 6, commandY, sendWidth, 22, tr("send"), b -> submitCommand());
        send.active = state.connected() && !usbBusy();
        input("send", send, commandY);
        surfaces.add(new Surface(terminal, UiTheme.ACCENT_PRIMARY));
        return terminal.bottom();
    }

    private int sectionStart(Component title, int x, int y, int width, String help) {
        int titleWidth = width - PAD * 2 - 36;
        headings.add(new Heading(title, x + PAD, y + PAD, titleWidth, UiTheme.ACCENT_PRIMARY));
        help(help, x + width - PAD - 28, y + PAD - 3);
        return y + PAD + Math.max(22, UiDraw.sectionHeaderHeight(font, title, titleWidth)) + 8;
    }
    private int heading(Component title, int x, int y, int width, int accent) {
        headings.add(new Heading(title, x, y, width, accent));
        return y + UiDraw.sectionHeaderHeight(font, title, width) + 4;
    }
    private int paragraph(Component text, int x, int y, int width, int color) {
        texts.add(new Text(text, x, y, width, color, 0));
        return y + textHeight(text, width);
    }
    private int notice(Component text, int x, int y, int width, int background, int color) {
        texts.add(new Text(text, x, y, width, color, background));
        return y + UiDraw.noticeHeight(font, text, width);
    }
    private int property(Component label, Component value, int x, int y, int width, int color) {
        properties.add(new Property(label, value, x, y, width, color));
        return y + UiDraw.labelledRowHeight(font, label, width);
    }
    private int textHeight(Component text, int width) { return font.split(text, Math.max(1, width)).size() * font.lineHeight; }
    private void input(String key, AbstractWidget widget, int y) {
        focusWidgets.put(key, widget); viewport.add(widget, y - view.y());
        if (dialog == null) panel.addInputWidget(widget);
    }
    private void help(String topic, int x, int y) {
        var button = IconTextButton.help(x, y, tr("help." + topic + ".title"), b -> {
            focusKey = "help." + topic;
            dialog = new ContextHelpDialog(tr("help." + topic + ".title"), tr("help." + topic + ".body"), () -> {
                dialog = null; panel.refresh();
            });
            panel.refresh();
        });
        input("help." + topic, button, y);
    }

    private boolean usbBusy() { return ConnectionManager.isUsbReconnecting() || reconnect != null; }
    private void applyBaud() {
        if (usbBusy()) return;
        focusKey = "apply";
        if (!ConnectionManager.getSerial().isConnected() || panel.isGeneratingSignal()) {
            resultAccepted = false;
            resultMessage = tr(panel.isGeneratingSignal() ? "usb.busy" : "usb.connection_lost");
        } else {
            resultMessage = null;
            reconnect = ConnectionManager.reconnectUsb(usbPort, pendingBaud);
        }
        panel.refresh();
    }
    private void toggleRemember() {
        var wifi = ConnectionManager.getWifi();
        boolean forgetting = wifi.isCurrentRemembered();
        if (!forgetting && !wifi.canRemember()) return;
        resultAccepted = forgetting ? wifi.forgetCurrentBoard() : wifi.rememberCurrentBoard();
        resultMessage = tr(resultAccepted ? forgetting ? "wifi.forgotten" : "wifi.saved" : "wifi.failed");
        focusKey = "trust"; showToken = false; panel.refresh();
    }
    private void submitCommand() {
        if (commandBox == null || !ConnectionManager.isAnyConnected() || usbBusy()) return;
        String text = commandBox.getValue().trim();
        if (text.isEmpty()) return;
        ConnectionManager.sendMessageToBoard(text);
        commandBox.setValue("");
    }
    @Override public void tick() { update(true); }
    @Override public void backgroundTick() { showToken = false; update(false); }
    private void update(boolean visible) {
        if (state == null) return;
        boolean changed = false;
        if (reconnect != null && reconnect.isDone()) {
            ConnectionResult result;
            try { result = reconnect.join(); }
            catch (CompletionException e) {
                com.serialcraft.SerialCraft.LOGGER.warn("USB reconfiguration failed", e);
                result = new ConnectionResult(false, tr("usb.connection_lost"));
            }
            reconnect = null;
            var serial = ConnectionManager.getSerial();
            resultAccepted = result.connected() && serial.isConnected()
                    && serial.getPortName().equalsIgnoreCase(usbPort) && serial.getBaudRate() == pendingBaud;
            resultMessage = resultAccepted ? tr("usb.applied", serial.getBaudRate())
                    : result.connected() ? tr("usb.connection_lost") : result.message();
            changed = true;
        }
        LinkState current = readState();
        if (current.epoch() != state.epoch()) showToken = false;
        changed |= !current.equals(state);
        if (visible && changed) { captureFocus(); panel.refresh(); }
        if (visible) updateHistory();
    }
    private void updateHistory() {
        history = ConnectionManager.recentHistory(HISTORY_LIMIT);
        boolean atEnd = historyScroll.getScrollAmount() >= historyScroll.getMaxScroll() - 1;
        historyScroll.update(Math.max(1, console.height() - 28), history.size() * LINE_HEIGHT);
        if (atEnd) historyScroll.setScrollAmount(historyScroll.getMaxScroll());
    }

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font, int width, int height) {
        UiDraw.pageTitle(gui, font, contentX, tr("title"), UiTheme.ACCENT_HOME, tr("subtitle"));
        int mx = dialog == null && view.contains(mouseX, mouseY) ? mouseX : -1;
        int my = mx == -1 ? -1 : mouseY;
        gui.enableScissor(view.x(), view.y(), view.right() - 8, view.bottom());
        for (Surface surface : surfaces) {
            Rect r = surface.bounds();
            UiDraw.card(gui, r.x(), drawY(r.y()), r.width(), r.height(), surface.accent(),
                    mx >= r.x() && mx < r.right() && my >= drawY(r.y()) && my < drawY(r.bottom()));
        }
        renderSummary(gui, mx, my);
        for (Heading h : headings) UiDraw.sectionHeader(gui, font, h.title(), h.x(), drawY(h.y()), h.width(), h.accent());
        for (Text t : texts) {
            if (t.background() == 0) UiDraw.wrappedText(gui, font, t.value(), t.x(), drawY(t.y()), t.width(), t.color());
            else UiDraw.notice(gui, font, t.value(), t.x(), drawY(t.y()), t.width(), t.background(), t.color());
        }
        for (Property p : properties)
            UiDraw.labelledRow(gui, font, p.x(), drawY(p.y()), p.width(), p.label(), p.value(), p.color(), mx, my);
        Component confidence = tr("confidence." + state.identity().confidence().name().toLowerCase(Locale.ROOT));
        UiDraw.badge(gui, font, modelBadge.x(), drawY(modelBadge.y()), modelBadge.width(), confidence,
                confirmedModel() ? UiTheme.OK_BG : UiTheme.NEUTRAL_BG,
                confirmedModel() ? UiTheme.OK_DARK : UiTheme.NEUTRAL_TX, mx, my);
        renderTerminal(gui, mx, my);
        gui.disableScissor();
        viewport.render(gui, mx, my, 0);
    }

    private void renderSummary(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        int x = summary.x(), y = drawY(summary.y()), width = summary.width();
        UiDraw.card(gui, x, y, width, summary.height(), statusColor(), false);
        gui.fill(x + 1, y + 4, x + width - 1, y + 25, statusBackground());
        int statusWidth = Math.min(width / 2, font.width(connectionState()) + 8);
        UiDraw.clippedText(gui, font, tr("active_device"), x + PAD, y + 10,
                width - PAD * 2 - statusWidth - 8, statusColor(), mouseX, mouseY);
        UiDraw.badge(gui, font, x + width - PAD - statusWidth, y + 7, statusWidth,
                connectionState(), statusBackground(), statusColor(), mouseX, mouseY);
        gui.fill(x + PAD, y + 34, x + PAD + 36, y + 70, state.connected() ? UiTheme.ACCENT_PRIMARY : UiTheme.TEXT_SECONDARY);
        UiDraw.icon(gui, state.wifi() ? SpriteIcon.WIFI : SpriteIcon.USB, x + PAD + 7, y + 41, 22);
        int nameX = x + PAD + 46;
        int nameWidth = width >= 400 ? focusWidgets.get("disconnect").getX() - nameX - 12 : x + width - PAD - nameX;
        Component name = !state.connected() && state.endpoint().isEmpty() ? tr("no_device") : modelName();
        UiDraw.clippedText(gui, font, name.copy().withStyle(ChatFormatting.BOLD), nameX, y + 36, nameWidth,
                UiTheme.TEXT_PRIMARY, mouseX, mouseY);
        UiDraw.clippedText(gui, font, state.connected() ? family() : tr("device.disconnected"),
                nameX, y + 51, nameWidth, UiTheme.TEXT_SECONDARY, mouseX, mouseY);
        int footerY = drawY(summaryFooterY);
        gui.fill(x + PAD, footerY - 5, x + width - PAD, footerY - 4, UiTheme.LINE);
        gui.fill(x + PAD, footerY + 3, x + PAD + 5, footerY + 8, statusColor());
        Component footer = tr(state.connected() ? "link.active" : "link.inactive").append(" · ").append(transport());
        long seconds = ConnectionManager.getConnectedSeconds();
        if (state.connected() && seconds >= 0) footer = footer.copy().append(" · ").append(duration(seconds));
        UiDraw.clippedText(gui, font, footer, x + PAD + 11, footerY + 1, width - PAD * 2 - 11,
                UiTheme.TEXT_SECONDARY, mouseX, mouseY);
    }

    private void renderTerminal(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        int x = terminal.x(), y = drawY(terminal.y());
        gui.fill(x + PAD, y + PAD, x + PAD + 20, y + PAD + 20, UiTheme.ACCENT_PRIMARY);
        UiDraw.icon(gui, SpriteIcon.TERMINAL, x + PAD + 2, y + PAD + 2, 16);
        UiDraw.wrappedText(gui, font, tr("terminal"), x + PAD + 28, y + PAD,
                terminal.width() - PAD * 2 - 28, UiTheme.TEXT_PRIMARY);
        int consoleY = drawY(console.y());
        gui.fill(console.x(), consoleY, console.right(), consoleY + console.height(), UiTheme.BG_CONSOLE);
        gui.fill(console.x(), consoleY, console.right(), consoleY + 21, UiTheme.BG_CONSOLE_ALT);
        UiDraw.clippedText(gui, font, transport().copy().append(" · ").append(state.connected() ? value(state.endpoint()) : connectionState()),
                console.x() + 8, consoleY + 6, console.width() - 16, UiTheme.TEXT_ON_DARK, mouseX, mouseY);
        gui.enableScissor(console.x() + 4, consoleY + 24, console.right() - 8, consoleY + console.height() - 4);
        int lineY = consoleY + 24 - (int) historyScroll.getScrollAmount();
        if (history.isEmpty()) UiDraw.wrappedText(gui, font, tr("terminal.empty"), console.x() + 8, lineY, console.width() - 20, UiTheme.TEXT_ON_DARK);
        for (String line : history) {
            if (lineY + LINE_HEIGHT >= consoleY + 24 && lineY < consoleY + console.height() - 4) {
                int color = line.startsWith("TX:") ? UiTheme.OK : line.startsWith("RX:") ? UiTheme.CONSOLE_RX
                        : line.startsWith("TM:") ? UiTheme.WARN : line.startsWith("ERR:") ? UiTheme.ERROR : UiTheme.TEXT_ON_DARK;
                UiDraw.clippedText(gui, font, Component.literal(line), console.x() + 8, lineY, console.width() - 20,
                        color, mouseY >= consoleY + 24 && mouseY < consoleY + console.height() - 4 ? mouseX : -1, mouseY);
            }
            lineY += LINE_HEIGHT;
        }
        gui.disableScissor();
        historyScroll.renderScrollbar(gui, console.right() - 6, consoleY + 24, 5, console.height() - 28);
    }

    private int drawY(int y) { return y - (int) scroll.getScrollAmount(); }
    private boolean confirmedModel() { return state.identity().hasModel() && state.identity().confidence().ordinal() >= BoardIdentity.Confidence.MODEL.ordinal(); }
    private Component modelName() { return confirmedModel() ? Component.literal(state.identity().model()) : tr("model.unconfirmed"); }
    private Component family() { return tr("family." + state.identity().family().name().toLowerCase(Locale.ROOT)); }
    private Component transport() { return tr(state.wifi() ? "transport.wifi" : "transport.usb"); }
    private Component baud() { return state.baud() > 0 ? tr("usb.bps", state.baud()) : unavailable(); }
    private Component serverState() {
        return Component.translatable("gui.serialcraft.wifi.state." +
                (state.connected() ? "connected" : state.serverPort() > 0 ? "listening" : "stopped"));
    }
    private Component connectionState() { return usbBusy() && !state.wifi() ? tr("usb.reconnecting") : Component.translatable(state.connected() ? "gui.serialcraft.status.connected" : "gui.serialcraft.status.disconnected"); }
    private int statusColor() { return usbBusy() && !state.wifi() ? UiTheme.WARN_DARK : state.connected() ? UiTheme.OK_DARK : UiTheme.ERROR_DARK; }
    private int statusBackground() { return usbBusy() && !state.wifi() ? UiTheme.WARN_BG : state.connected() ? UiTheme.OK_BG : UiTheme.ERROR_BG; }
    private static Component value(String value) { return value.isEmpty() ? unavailable() : Component.literal(value); }
    private static Component unavailable() { return tr("usb.unavailable"); }
    private static MutableComponent tr(String key, Object... args) { return Component.translatable("gui.serialcraft.home." + key, args); }
    private static Component duration(long seconds) {
        return seconds < 60 ? tr("duration.seconds", seconds) : seconds < 3600
                ? tr("duration.minutes", seconds / 60, seconds % 60) : tr("duration.hours", seconds / 3600, seconds % 3600 / 60);
    }

    @Override public void renderOverlay(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font, int width, int height) {
        if (dialog != null) dialog.render(gui, font, width, height, mouseX, mouseY);
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (dialog != null) return dialog.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_TAB) viewport.prepareKeyboardFocus();
        if (commandBox.isFocused() && (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER)) {
            submitCommand(); return true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP || event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
            boolean historyFocus = commandBox.isFocused() && historyScroll.hasScroll();
            ScrollState target = historyFocus ? historyScroll : scroll;
            int height = historyFocus ? console.height() - 28 : view.height();
            target.setScrollAmount(target.getScrollAmount() + (event.key() == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * height * .8);
            viewport.position(); return true;
        }
        return false;
    }
    private void captureFocus() {
        if (dialog != null) return;
        for (var entry : focusWidgets.entrySet()) if (panel.getFocused() == entry.getValue()) {
            focusKey = entry.getKey(); navigationFocus = ""; return;
        }
        if (panel.getFocused() instanceof AbstractWidget widget) {
            focusKey = ""; navigationFocus = widget.getMessage().getString();
        }
    }
    @Override public void afterKey() {
        if (dialog != null) { dialog.restoreFocus(panel); return; }
        captureFocus(); viewport.revealFocus(panel.getFocused());
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (dialog != null) return dialog.scrolled(vertical);
        if (!view.contains(x, y)) return false;
        if (console.contains(x, y + scroll.getScrollAmount()) && historyScroll.mouseScrolled(vertical)) return true;
        boolean moved = scroll.mouseScrolled(vertical); viewport.position(); return moved;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        if (dialog != null) return dialog.clicked(event);
        if (view.contains(event.x(), event.y()) && historyScroll.mouseClicked(event.x(), event.y(), event.button(),
                console.right() - 6, drawY(console.y()) + 24, 5, console.height() - 28)) return true;
        boolean handled = scroll.mouseClicked(event.x(), event.y(), event.button(), view.right() - 6, view.y(), 6, view.height());
        viewport.position(); return handled;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        if (dialog != null) return dialog.released(event.button());
        return historyScroll.mouseReleased(event.button()) | scroll.mouseReleased(event.button());
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double x, double y) {
        if (dialog != null) return dialog.dragged(event.y());
        if (historyScroll.mouseDragged(event.y(), drawY(console.y()) + 24, console.height() - 28)) return true;
        boolean handled = scroll.mouseDragged(event.y(), view.y(), view.height()); viewport.position(); return handled;
    }
    @Override public void onClose() { showToken = false; dialog = null; }
}
