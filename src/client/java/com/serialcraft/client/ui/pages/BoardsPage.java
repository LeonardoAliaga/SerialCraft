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
    private static final int CARD_HEIGHT = 78;
    private static final int CARD_ROW = CARD_HEIGHT + 8;
    private static final int CARD_MAX_WIDTH = 480;
    private static final int CARD_MARGIN = 8;
    private static final int ACTION_GAP = 4;
    private static final int TOGGLE_HEIGHT = 20;
    private static final int EDIT_HEIGHT = 22;
    private static final int TOGGLE_Y = (CARD_HEIGHT - TOGGLE_HEIGHT - ACTION_GAP - EDIT_HEIGHT) / 2;
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
    private int cardWidth() { return Math.max(80, Math.min(CARD_MAX_WIDTH, width - cardX() - UiTheme.contentMargin(width) - 10)); }
    private int viewportHeight() { return Math.max(1, height - UiTheme.contentMargin(width) - CARD_TOP); }
    private void buildList() {
        scroll.update(viewportHeight(), boards.size() * CARD_ROW);
        int x = cardX(), cardWidth = cardWidth();
        var refresh = new IconTextButton(x + cardWidth - 88, 43, 88, 22, SpriteIcon.LIST,
                Component.translatable("gui.serialcraft.io.list.refresh"), b -> { requestList(); panel.refresh(); },
                UiTheme.ACCENT_PRIMARY, UiTheme.ACCENT_PRIMARY_DARK);
        refresh.active = !awaitingResponse; panel.addInputWidget(refresh);
        listHeaderButton = refresh;
        for (int i = 0; i < boards.size(); i++) {
            BoardInfo board = boards.get(i);
            int buttonWidth = Math.min(112, (cardWidth - 26) / 2);
            int cardY = CARD_TOP + i * CARD_ROW;
            int buttonX = x + cardWidth - buttonWidth - 8;
            var toggle = new IconTextButton(buttonX, cardY + TOGGLE_Y, buttonWidth, TOGGLE_HEIGHT,
                    board.enabled() ? SpriteIcon.CONNECT : SpriteIcon.DISCONNECT,
                    Component.translatable(board.enabled() ? "gui.serialcraft.io.enabled" : "gui.serialcraft.io.disabled"),
                    b -> toggle(board), board.enabled() ? UiTheme.OK_DARK : UiTheme.NEUTRAL_TX,
                    board.enabled() ? UiTheme.OK_DARK : UiTheme.LINE_STRONG);
            toggle.setTooltip(Tooltip.create(Component.translatable(board.enabled()
                    ? "gui.serialcraft.io.list.disable" : "gui.serialcraft.io.list.enable")));
            var edit = new OutlineButton(buttonX, cardY + EDIT_Y, buttonWidth, EDIT_HEIGHT,
                    Component.translatable("gui.serialcraft.boards.edit"), b -> openEditor(board, true));
            edit.setTooltip(Tooltip.create(Component.translatable("gui.serialcraft.io.list.edit", board.id())));
            panel.addInputWidget(toggle); panel.addInputWidget(edit);
            widgets.add(new CardWidgets(toggle, edit, cardY));
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
            card.toggle().setY(y + TOGGLE_Y); card.edit().setY(y + EDIT_Y);
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
        int x = cardX(), cardWidth = cardWidth();
        UiDraw.pageTitle(gui, font, UiTheme.contentX(width),
                Component.translatable("gui.serialcraft.boards.title"), UiTheme.ACCENT_BOARDS,
                Component.translatable("gui.serialcraft.boards.subtitle"));
        gui.text(font, Component.translatable(boards.size() == 1 ? "gui.serialcraft.io.list.count_one" : "gui.serialcraft.io.list.count_many", boards.size()),
                x, 50, UiTheme.TEXT_SECONDARY, false);
        listHeaderButton.extractRenderState(gui, mouseX, mouseY, 0);
        if (boards.isEmpty()) {
            Component message = Component.translatable(!listMessage.isEmpty() ? listMessage : awaitingResponse
                    ? "gui.serialcraft.boards.loading" : "gui.serialcraft.boards.empty_hint");
            UiDraw.wrappedText(gui, font, message, x, CARD_TOP + 8, cardWidth, listMessage.isEmpty() ? UiTheme.TEXT_SECONDARY : UiTheme.ERROR_DARK);
            return;
        }
        positionWidgets();
        gui.enableScissor(x - 2, CARD_TOP, x + cardWidth + 2, CARD_TOP + viewportHeight());
        int offset = (int) scroll.getScrollAmount();
        for (int i = 0; i < boards.size(); i++) {
            int y = CARD_TOP + i * CARD_ROW - offset;
            if (y + CARD_HEIGHT < CARD_TOP || y > CARD_TOP + viewportHeight()) continue;
            BoardInfo board = boards.get(i);
            CardWidgets card = widgets.get(i);
            int accent = board.enabled() ? board.mode().isOutput() ? UiTheme.INFO : UiTheme.OK : UiTheme.LINE_STRONG;
            int tint = board.enabled() ? board.mode().isOutput() ? UiTheme.INFO_BG : UiTheme.OK_BG : UiTheme.NEUTRAL_BG;
            int directionColor = board.enabled() ? board.mode().isOutput() ? UiTheme.INFO_DARK : UiTheme.OK_DARK : UiTheme.NEUTRAL_TX;
            UiDraw.card(gui, x, y, cardWidth, CARD_HEIGHT);
            gui.fill(x + 3, y, x + cardWidth, y + 26, tint);
            gui.fill(x, y, x + 3, y + CARD_HEIGHT, accent);
            cardText(gui, font, Component.literal(board.id()).withStyle(ChatFormatting.BOLD), x + 12, y + 9,
                    card.toggle().getX() - x - 20, mouseX, mouseY, UiTheme.TEXT_PRIMARY);
            cardText(gui, font, Component.translatable("gui.serialcraft.io.list.channel", board.data()),
                    x + 12, y + 27, card.toggle().getX() - x - 20, mouseX, mouseY, UiTheme.TEXT_PRIMARY);
            int footerWidth = card.edit().getX() - x - 20;
            Component direction = Component.translatable("gui.serialcraft.mode." + board.mode().getSerializedName());
            int badgeWidth = Math.min(footerWidth, font.width(direction) + 8);
            var directionLines = font.split(direction, Math.max(1, badgeWidth - 8));
            int lineCount = Math.min(2, directionLines.size());
            int badgeY = lineCount > 1 ? 40 : 44;
            int badgeHeight = lineCount * font.lineHeight + 6;
            gui.fill(x + 12, y + badgeY, x + 12 + badgeWidth, y + badgeY + badgeHeight, tint);
            cardText(gui, font, direction, x + 16, y + badgeY + 3, badgeWidth - 8, mouseX, mouseY, directionColor);
            for (int line = 1; line < lineCount; line++)
                gui.text(font, directionLines.get(line), x + 16, y + badgeY + 3 + line * font.lineHeight, directionColor, false);
            cardText(gui, font, Component.translatable("gui.serialcraft.boards.pos", board.pos().getX(), board.pos().getY(), board.pos().getZ()),
                    x + 12, y + (lineCount > 1 ? badgeY + badgeHeight + 3 : 62), footerWidth, mouseX, mouseY, UiTheme.TEXT_SECONDARY);
            card.toggle().extractRenderState(gui, mouseX, mouseY, 0);
            card.edit().extractRenderState(gui, mouseX, mouseY, 0);
        }
        gui.disableScissor();
        scroll.renderScrollbar(gui, x + cardWidth + 3, CARD_TOP, 6, viewportHeight());
    }
    private void cardText(GuiGraphicsExtractor gui, Font font, Component text, int x, int y, int width, int mouseX, int mouseY, int color) {
        var lines = font.split(text, Math.max(1, width));
        if (!lines.isEmpty()) gui.text(font, lines.getFirst(), x, y, color, false);
        if (font.width(text) > width && mouseY >= CARD_TOP && mouseY < CARD_TOP + viewportHeight()
                && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 12)
            gui.setTooltipForNextFrame(text, mouseX, mouseY);
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
        boolean handled = scroll.mouseClicked(event.x(), event.y(), event.button(), cardX() + cardWidth() + 2, CARD_TOP, 8, viewportHeight());
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
