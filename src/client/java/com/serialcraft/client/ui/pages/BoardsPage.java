package com.serialcraft.client.ui.pages;

import com.serialcraft.block.entity.HardwareIOBlockEntity;
import com.serialcraft.client.ui.ScrollState;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.client.ui.io.IoEditor;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.client.ui.widget.OutlineButton;
import com.serialcraft.network.BoardInfo;
import com.serialcraft.network.BoardListRequestPayload;
import com.serialcraft.network.ConfigResultPayload;
import com.serialcraft.network.RemoteTogglePayload;
import com.serialcraft.screen.PanelUI;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Module inventory and network responses. Editor presentation lives in IoEditor. */
public class BoardsPage implements Page {
    private static final int CARD_TOP = 72;
    private static final int CARD_HEIGHT = 102;
    private static final int CARD_ROW = CARD_HEIGHT + 16;
    private static final int CARD_MAX_WIDTH = 396;
    private static final int CARD_MIN_TWO_COLUMNS = 340;
    private static final int CARD_COLUMN_GAP = 26;
    private static final int CARD_MARGIN = 8;
    private static final int SCROLLBAR_GAP = 16;
    private static final int ACTION_GAP = 4;
    private static final int TOGGLE_HEIGHT = 20;
    private static final int EDIT_HEIGHT = 22;
    private static final int TOGGLE_Y = 39;
    private static final int EDIT_Y = TOGGLE_Y + TOGGLE_HEIGHT + ACTION_GAP;
    private record CardWidgets(IconTextButton toggle, OutlineButton edit, int baseY) {}
    private final List<BoardInfo> boards = new ArrayList<>();
    private final List<CardWidgets> widgets = new ArrayList<>();
    private final ScrollState scroll = new ScrollState();
    private PanelUI panel;
    private int width, height;
    private boolean awaitingResponse, listLoaded;
    private long listRequestedAt;
    private String listMessage = "", dimension = "";
    private List<BoardInfo> incoming;
    private BlockPos directEdit;
    private IoEditor editor;

    @Override
    public void init(PanelUI panel, int width, int height) {
        this.panel = panel; this.width = width; this.height = height;
        widgets.clear();
        updateDimension();
        if (directEdit != null) {
            BlockPos target = directEdit; directEdit = null;
            var level = Minecraft.getInstance().level;
            if (level != null && level.getBlockEntity(target) instanceof HardwareIOBlockEntity io) openEditor(io.toBoardInfo(), false);
            else {
                BoardInfo board = boards.stream().filter(b -> b.pos().equals(target)).findFirst().orElse(null);
                if (board != null) openEditor(board, false);
                else listMessage = "gui.serialcraft.editor.unavailable";
            }
        }
        if (editor != null) { editor.build(panel, width, height); return; }
        if (!listLoaded && !awaitingResponse) requestList();
        buildList();
    }
    public void requestDirectEdit(BlockPos pos) { directEdit = pos; }
    private void openEditor(BoardInfo target, boolean refresh) {
        editor = new IoEditor(target, dimension, this::closeEditor);
        if (refresh) panel.refresh();
    }
    private void closeEditor() { editor = null; listLoaded = false; awaitingResponse = false; panel.refresh(); }
    public void requestClosePanel(Runnable action) { if (editor == null) action.run(); else editor.requestClose(action); }

    @Override
    public void tick() {
        if (updateDimension()) { panel.refresh(); return; }
        if (editor != null) editor.tick();
        if (awaitingResponse && System.nanoTime() - listRequestedAt > 5_000_000_000L) {
            awaitingResponse = false; listLoaded = true;
            listMessage = "gui.serialcraft.boards.timeout";
            if (editor == null) panel.refresh();
        }
        if (incoming == null) return;
        List<BoardInfo> received = incoming; incoming = null;
        awaitingResponse = false; listLoaded = true; listMessage = "";
        boards.clear(); boards.addAll(received);
        if (editor == null) panel.refresh();
    }
    private boolean updateDimension() {
        var level = Minecraft.getInstance().level;
        String current = level == null ? "" : level.dimension().identifier().toString();
        if (dimension.equals(current)) return false;
        boolean initialized = !dimension.isEmpty();
        dimension = current; boards.clear(); incoming = null; awaitingResponse = listLoaded = false; editor = null;
        if (initialized) directEdit = null;
        return initialized;
    }
    public void acceptBoardList(List<BoardInfo> list) { incoming = List.copyOf(list); }
    public void acceptConfigResult(ConfigResultPayload result) { if (editor != null) editor.accept(result); }
    @Override public void onClose() { awaitingResponse = false; incoming = null; }

    private void requestList() {
        if (awaitingResponse) return;
        if (!ClientPlayNetworking.canSend(BoardListRequestPayload.TYPE)) {
            listLoaded = true; listMessage = "gui.serialcraft.editor.incompatible"; return;
        }
        awaitingResponse = true; listRequestedAt = System.nanoTime(); listMessage = "";
        ClientPlayNetworking.send(BoardListRequestPayload.INSTANCE);
    }
    private int cardX() { return UiTheme.contentX(width) + CARD_MARGIN; }
    private int viewportHeight() { return Math.max(1, height - UiTheme.contentMargin(width) - CARD_TOP); }

    private int availableWidth() {
        return Math.max(80, width - cardX() - UiTheme.contentMargin(width) - SCROLLBAR_GAP - 6);
    }
    private int columns() {
        return availableWidth() >= 2 * CARD_MIN_TWO_COLUMNS + CARD_COLUMN_GAP ? 2 : 1;
    }
    private int cardWidth() {
        return Math.min(CARD_MAX_WIDTH, Math.max(80,
                (availableWidth() - (columns() - 1) * CARD_COLUMN_GAP) / columns()));
    }
    private int listWidth() {
        return columns() * cardWidth() + (columns() - 1) * CARD_COLUMN_GAP;
    }
    private int cardX(int index) {
        return cardX() + (index % columns()) * (cardWidth() + CARD_COLUMN_GAP);
    }
    private int cardY(int index) {
        return CARD_TOP + (index / columns()) * CARD_ROW;
    }
    private int scrollbarX() { return cardX() + listWidth() + SCROLLBAR_GAP; }

    private void buildList() {
        int rows = (boards.size() + columns() - 1) / columns();
        scroll.update(viewportHeight(), Math.max(0, rows * CARD_ROW - (CARD_ROW - CARD_HEIGHT)));
        int cardWidth = cardWidth();

        var refresh = new IconTextButton(cardX() + listWidth() - 88, 43, 88, 22, SpriteIcon.LIST,
                Component.translatable("gui.serialcraft.io.list.refresh"),
                b -> { requestList(); panel.refresh(); },
                UiTheme.ACCENT_PRIMARY, UiTheme.ACCENT_PRIMARY_DARK);
        refresh.active = !awaitingResponse;
        panel.addInputWidget(refresh);
        listHeaderButton = refresh;

        for (int i = 0; i < boards.size(); i++) {
            BoardInfo board = boards.get(i);
            int buttonWidth = Math.min(112, Math.max(60, cardWidth / 3));
            int x = cardX(i);
            int y = cardY(i);
            int buttonX = x + cardWidth - buttonWidth - 18;

            var toggle = new IconTextButton(buttonX, y + TOGGLE_Y, buttonWidth, TOGGLE_HEIGHT,
                    board.enabled() ? SpriteIcon.CONNECT : SpriteIcon.DISCONNECT,
                    Component.translatable(board.enabled() ? "gui.serialcraft.io.enabled" : "gui.serialcraft.io.disabled"),
                    b -> toggle(board), board.enabled() ? UiTheme.OK_DARK : UiTheme.NEUTRAL_TX,
                    board.enabled() ? UiTheme.OK_DARK : UiTheme.LINE_STRONG);
            toggle.setTooltip(Tooltip.create(Component.translatable(board.enabled()
                    ? "gui.serialcraft.io.list.disable" : "gui.serialcraft.io.list.enable")));

            var edit = new OutlineButton(buttonX, y + EDIT_Y, buttonWidth, EDIT_HEIGHT,
                    Component.translatable("gui.serialcraft.boards.edit"), b -> openEditor(board, true));
            edit.setTooltip(Tooltip.create(Component.translatable("gui.serialcraft.io.list.edit", board.id())));
            panel.addInputWidget(toggle);
            panel.addInputWidget(edit);
            widgets.add(new CardWidgets(toggle, edit, y));
        }
        positionWidgets();
    }
    private IconTextButton listHeaderButton;
    private void toggle(BoardInfo board) {
        if (awaitingResponse || !ClientPlayNetworking.canSend(RemoteTogglePayload.TYPE)) return;
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        ClientPlayNetworking.send(new RemoteTogglePayload(board.pos(), dimension));
        // The server already returns its authoritative list after a successful toggle.
        awaitingResponse = true; listRequestedAt = System.nanoTime(); positionWidgets();
        listHeaderButton.active = false;
    }
    private void positionWidgets() {
        int offset = (int) scroll.getScrollAmount();
        for (CardWidgets card : widgets) {
            int y = card.baseY() - offset;
            card.toggle().setY(y + TOGGLE_Y);
            card.edit().setY(y + EDIT_Y);
            card.toggle().visible = buttonInsideViewport(card.toggle());
            card.edit().visible = buttonInsideViewport(card.edit());
            card.toggle().active = card.toggle().visible && !awaitingResponse;
            card.edit().active = card.edit().visible;
        }
    }
    private boolean buttonInsideViewport(AbstractWidget button) {
        return button.getY() >= CARD_TOP && button.getBottom() <= CARD_TOP + viewportHeight();
    }

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font, int width, int height) {
        if (editor != null) { editor.render(gui, font, mouseX, mouseY); return; }

        int x = cardX();
        int cardWidth = cardWidth();
        UiDraw.pageTitle(gui, font, UiTheme.contentX(width),
                Component.translatable("gui.serialcraft.boards.title"), UiTheme.ACCENT_BOARDS,
                Component.translatable("gui.serialcraft.boards.subtitle"));
        gui.text(font, Component.translatable(boards.size() == 1
                        ? "gui.serialcraft.io.list.count_one" : "gui.serialcraft.io.list.count_many", boards.size()),
                x, 50, UiTheme.TEXT_SECONDARY, false);
        listHeaderButton.extractRenderState(gui, mouseX, mouseY, 0);

        if (boards.isEmpty()) {
            Component message = Component.translatable(!listMessage.isEmpty() ? listMessage : awaitingResponse
                    ? "gui.serialcraft.boards.loading" : "gui.serialcraft.boards.empty_hint");
            UiDraw.wrappedText(gui, font, message, x, CARD_TOP + 8, listWidth(),
                    listMessage.isEmpty() ? UiTheme.TEXT_SECONDARY : UiTheme.ERROR_DARK);
            return;
        }

        positionWidgets();
        gui.enableScissor(x - 2, CARD_TOP, x + listWidth() + 2, CARD_TOP + viewportHeight());
        int offset = (int) scroll.getScrollAmount();
        for (int i = 0; i < boards.size(); i++) {
            int cardX = cardX(i);
            int cardY = cardY(i) - offset;
            if (cardY + CARD_HEIGHT < CARD_TOP || cardY > CARD_TOP + viewportHeight()) continue;

            BoardInfo board = boards.get(i);
            CardWidgets controls = widgets.get(i);
            boolean enabled = board.enabled();
            boolean output = board.mode().isOutput();
            boolean connected = board.snapshot().connected();

            int accent = enabled ? (output ? UiTheme.INFO : UiTheme.OK) : UiTheme.LINE_STRONG;
            int tint = enabled ? (output ? UiTheme.INFO_BG : UiTheme.OK_BG) : UiTheme.NEUTRAL_BG;
            int directionColor = enabled ? (output ? UiTheme.INFO_DARK : UiTheme.OK_DARK) : UiTheme.NEUTRAL_TX;
            int infoWidth = Math.max(1, controls.toggle().getX() - cardX - 20);

            UiDraw.card(gui, cardX, cardY, cardWidth, CARD_HEIGHT);
            boolean hovered = mouseY >= CARD_TOP && mouseY < CARD_TOP + viewportHeight()
                    && mouseX >= cardX && mouseX < cardX + cardWidth && mouseY >= cardY && mouseY < cardY + CARD_HEIGHT;
            gui.outline(cardX, cardY, cardWidth, CARD_HEIGHT, hovered ? UiTheme.LINE_STRONG : UiTheme.LINE);
            gui.fill(cardX + 3, cardY, cardX + cardWidth, cardY + 26, tint);
            gui.fill(cardX, cardY, cardX + 3, cardY + CARD_HEIGHT, accent);

            // Estado de la sesión y tipo de señal: ambos vienen de BoardInfo.
            int statusColor = connected ? UiTheme.OK : UiTheme.LINE_STRONG;
            gui.fill(cardX + 12, cardY + 8, cardX + 18, cardY + 14, statusColor);
            Component status = Component.translatable(connected
                    ? "gui.serialcraft.status.connected" : "gui.serialcraft.status.disconnected");
            Component signal = Component.translatable(
                    "gui.serialcraft.signal." + board.signalType().getSerializedName());
            int signalWidth = Math.min(font.width(signal) + 8, (cardWidth - 40) / 2);
            int statusX = cardX + 22;
            int statusWidth = cardWidth - signalWidth - 46;
            int hoverX = mouseY >= CARD_TOP && mouseY < CARD_TOP + viewportHeight() ? mouseX : -1;
            UiDraw.badge(gui, font, statusX, cardY + 5, statusWidth, status,
                    connected ? UiTheme.OK_BG : UiTheme.NEUTRAL_BG,
                    connected ? UiTheme.OK_DARK : UiTheme.NEUTRAL_TX, hoverX, mouseY);
            UiDraw.badge(gui, font, cardX + cardWidth - 12 - signalWidth, cardY + 5, signalWidth,
                    signal, UiTheme.NEUTRAL_BG, UiTheme.TEXT_PRIMARY, hoverX, mouseY);

            cardText(gui, font, Component.literal(board.id()).withStyle(ChatFormatting.BOLD),
                    cardX + 12, cardY + 27, infoWidth, mouseX, mouseY, UiTheme.TEXT_PRIMARY);
            gui.fill(cardX + 12, cardY + 44,
                    Math.max(cardX + 13, controls.toggle().getX() - 12), cardY + 45, UiTheme.LINE);
            cardText(gui, font, Component.translatable("gui.serialcraft.io.list.channel", board.data()),
                    cardX + 12, cardY + 48, infoWidth, mouseX, mouseY, UiTheme.TEXT_PRIMARY);

            Component direction = Component.translatable("gui.serialcraft.mode." + board.mode().getSerializedName());
            int directionWidth = Math.min(infoWidth, font.width(direction) + 8);
            if (directionWidth > 8) {
                gui.fill(cardX + 12, cardY + 65,
                        cardX + 12 + directionWidth, cardY + 82, tint);
                cardText(gui, font, direction, cardX + 16, cardY + 69, directionWidth - 8,
                        mouseX, mouseY, directionColor);
            }
            cardText(gui, font, Component.translatable("gui.serialcraft.boards.pos",
                            board.pos().getX(), board.pos().getY(), board.pos().getZ()),
                    cardX + 12, cardY + 84, infoWidth, mouseX, mouseY, UiTheme.TEXT_SECONDARY);

            controls.toggle().extractRenderState(gui, mouseX, mouseY, 0);
            controls.edit().extractRenderState(gui, mouseX, mouseY, 0);
        }
        gui.disableScissor();
        scroll.renderScrollbar(gui, scrollbarX(), CARD_TOP, 6, viewportHeight());
    }
    private void cardText(GuiGraphicsExtractor gui, Font font, Component text, int x, int y, int width, int mouseX, int mouseY, int color) {
        UiDraw.clippedText(gui, font, text, x, y, Math.max(1, width), color,
                mouseY >= CARD_TOP && mouseY < CARD_TOP + viewportHeight() ? mouseX : -1, mouseY);
    }
    @Override public void renderOverlay(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font, int width, int height) {
        if (editor != null) editor.renderOverlay(gui, font, width, height, mouseX, mouseY);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double vertical) {
        if (editor != null) return editor.mouseScrolled(x, y, vertical);
        boolean handled = scroll.mouseScrolled(vertical); positionWidgets(); return handled;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        if (editor != null) return editor.mouseClicked(event);
        boolean handled = scroll.mouseClicked(event.x(), event.y(), event.button(), scrollbarX(), CARD_TOP, 6, viewportHeight());
        positionWidgets(); return handled;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) {
        return editor != null ? editor.mouseReleased(event) : scroll.mouseReleased(event.button());
    }
    @Override public boolean mouseDragged(MouseButtonEvent event, double x, double y) {
        if (editor != null) return editor.mouseDragged(event);
        boolean handled = scroll.mouseDragged(event.y(), CARD_TOP, viewportHeight()); positionWidgets(); return handled;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (editor != null) return editor.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_TAB) for (CardWidgets card : widgets) {
            card.toggle().visible = card.edit().visible = true;
            card.toggle().active = !awaitingResponse; card.edit().active = true;
        }
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP || event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll.setScrollAmount(scroll.getScrollAmount() + (event.key() == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * viewportHeight() * .8);
            positionWidgets(); return true;
        }
        return false;
    }
    @Override public void afterKey() {
        if (editor != null) { editor.afterKey(); return; }
        for (CardWidgets card : widgets) if (panel.getFocused() == card.edit() || panel.getFocused() == card.toggle()) {
            var focused = panel.getFocused() == card.edit() ? card.edit() : card.toggle();
            if (focused.getY() < CARD_TOP) scroll.setScrollAmount(scroll.getScrollAmount() + focused.getY() - CARD_TOP);
            else if (focused.getBottom() > CARD_TOP + viewportHeight())
                scroll.setScrollAmount(scroll.getScrollAmount() + focused.getBottom() - CARD_TOP - viewportHeight());
        }
        positionWidgets();
    }
}
