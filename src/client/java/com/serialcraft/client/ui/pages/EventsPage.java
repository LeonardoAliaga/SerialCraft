package com.serialcraft.client.ui.pages;

import com.serialcraft.client.events.EventsConfig;
import com.serialcraft.client.events.GameEvent;
import com.serialcraft.client.ui.ScrollState;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.client.ui.io.IoEditorLayout.Rect;
import com.serialcraft.client.ui.widget.EventToggle;
import com.serialcraft.client.ui.widget.UiViewport;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.screen.PanelUI;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pagina "Eventos": seleccion de datos del juego que se envian a la placa
 * y log en vivo del cable.
 *
 * Usa el mismo sistema que Inicio: superficies con riel de acento, un unico
 * viewport recortado con su barra en el borde derecho y la consola compartida.
 * En pantallas anchas las categorias se reparten en dos columnas.
 */
public class EventsPage implements Page {

    private static final int PAD        = UiTheme.CARD_PADDING;
    private static final int GAP        = UiTheme.SECTION_GAP;
    private static final int TITLE_H    = 18;
    private static final int ROW_H      = 16;
    private static final int ROW_WIDGET = 14;
    private static final int INTERVAL_H = 22;
    private static final int LINE_HEIGHT = 11;
    private static final int LOG_LINES  = 8;
    private static final int TWO_COLUMNS_FROM = 520;

    private record Category(GameEvent.Category category, Rect bounds) {}

    private final List<Category> categories = new ArrayList<>();
    private final ScrollState scroll = new ScrollState();
    private PanelUI panel;
    private UiViewport viewport;
    private Rect view, log;
    private int contentX, contentWidth, screenWidth;

    @Override
    public void init(PanelUI panel, int screenWidth, int screenHeight) {
        this.panel = panel;
        this.screenWidth = screenWidth;
        categories.clear();

        contentX = UiTheme.contentX(screenWidth);
        contentWidth = Math.max(80, screenWidth - contentX - UiTheme.contentMargin(screenWidth) - 10);
        int top = UiTheme.contentTop(screenHeight);
        view = new Rect(contentX - 2, top, contentWidth + 12,
                Math.max(24, screenHeight - UiTheme.contentMargin(screenWidth) - top));
        viewport = new UiViewport(scroll, view);

        EventsConfig cfg = EventsConfig.get();
        int columns = contentWidth >= TWO_COLUMNS_FROM ? 2 : 1;
        int columnWidth = (contentWidth - (columns - 1) * GAP) / columns;
        int[] columnY = new int[columns];
        java.util.Arrays.fill(columnY, top + 4);

        for (GameEvent.Category category : GameEvent.Category.values()) {
            List<GameEvent> events = eventsOf(category);
            if (events.isEmpty()) continue;

            int column = 0;
            for (int c = 1; c < columns; c++) if (columnY[c] < columnY[column]) column = c;
            int x = contentX + column * (columnWidth + GAP);
            int y = columnY[column];
            int height = PAD + TITLE_H + events.size() * ROW_H + PAD - 2;
            categories.add(new Category(category, new Rect(x, y, columnWidth, height)));

            int rowY = y + PAD + TITLE_H;
            for (GameEvent event : events) {
                EventToggle toggle = new EventToggle(x + PAD + 4, rowY, columnWidth - PAD * 2 - 4, ROW_WIDGET,
                        event, cfg.isEnabled(event), Component.translatable(event.labelKey()),
                        (ev, checked) -> EventsConfig.get().setEnabled(ev, checked));
                viewport.add(toggle, rowY - view.y());
                panel.addInputWidget(toggle);
                rowY += ROW_H;
            }
            columnY[column] = y + height + GAP;
        }

        int y = 0;
        for (int value : columnY) y = Math.max(y, value);

        SolidButton interval = SolidButton.primary(contentX, y, contentWidth, INTERVAL_H, intervalLabel(cfg), btn -> {
            EventsConfig.get().cycleInterval();
            btn.setMessage(intervalLabel(EventsConfig.get()));
        });
        viewport.add(interval, y - view.y());
        panel.addInputWidget(interval);
        y += INTERVAL_H + GAP;

        int logHeight = UiDraw.CONSOLE_HEADER_H + LOG_LINES * LINE_HEIGHT + 12;
        log = new Rect(contentX, y, contentWidth, logHeight);
        viewport.size(log.bottom() - view.y() + 8);
    }

    // ── EVENTOS DE RATON Y TECLADO ────────────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        boolean moved = scroll.mouseScrolled(verticalAmount);
        if (viewport != null) viewport.position();
        return moved;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        boolean handled = scroll.mouseClicked(event.x(), event.y(), event.button(),
                UiTheme.scrollbarX(screenWidth), view.y(), UiTheme.SCROLLBAR_WIDTH, view.height());
        viewport.position();
        return handled;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        return scroll.mouseReleased(event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        boolean handled = scroll.mouseDragged(event.y(), view.y(), view.height());
        viewport.position();
        return handled;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_TAB) viewport.prepareKeyboardFocus();
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP || event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll.setScrollAmount(scroll.getScrollAmount()
                    + (event.key() == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * view.height() * .8);
            viewport.position();
            return true;
        }
        return false;
    }

    @Override
    public void afterKey() {
        if (viewport != null && panel != null) viewport.revealFocus(panel.getFocused());
    }

    // ── RENDER ────────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font,
                       int screenWidth, int screenHeight) {
        UiDraw.pageTitle(gui, font, contentX,
                Component.translatable("gui.serialcraft.events.title"), UiTheme.ACCENT_EVENTS,
                Component.translatable("gui.serialcraft.events.subtitle"));
        UiDraw.pageDescription(gui, font, Component.translatable("gui.serialcraft.events.description"),
                contentX, contentWidth, screenHeight, mouseX, mouseY);

        int mx = view.contains(mouseX, mouseY) ? mouseX : -1;
        int my = mx == -1 ? -1 : mouseY;
        int offset = (int) scroll.getScrollAmount();

        gui.enableScissor(view.x(), view.y(), view.right() - 8, view.bottom());
        for (Category c : categories) {
            Rect r = c.bounds();
            UiDraw.surface(gui, r.x(), r.y() - offset, r.width(), r.height(),
                    UiTheme.ACCENT_EVENTS, 0, 0, false);
            UiDraw.clippedText(gui, font, Component.translatable(categoryLabelKey(c.category()))
                            .copy().withStyle(ChatFormatting.BOLD),
                    r.x() + PAD + 4, r.y() - offset + PAD, r.width() - PAD * 2 - 4,
                    UiTheme.TEXT_PRIMARY, mx, my);
        }
        renderLog(gui, font, mx, my, offset);
        gui.disableScissor();
        viewport.render(gui, mx, my, 0);
    }

    private void renderLog(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY, int offset) {
        int y = log.y() - offset;
        UiDraw.consolePanel(gui, font, log.x(), y, log.width(), log.height(),
                Component.translatable("gui.serialcraft.events.log_title"), mouseX, mouseY);

        List<String> entries = ConnectionManager.recentHistory(LOG_LINES);
        int lineY = y + UiDraw.CONSOLE_HEADER_H + 6;
        if (entries.isEmpty()) {
            gui.text(font, Component.translatable("gui.serialcraft.events.empty"),
                    log.x() + 8, lineY, UiTheme.TEXT_ON_DARK, false);
            return;
        }
        for (String entry : entries) {
            gui.text(font, font.plainSubstrByWidth(entry, log.width() - 16),
                    log.x() + 8, lineY, UiDraw.consoleLineColor(entry), false);
            lineY += LINE_HEIGHT;
        }
    }

    private static List<GameEvent> eventsOf(GameEvent.Category category) {
        List<GameEvent> result = new ArrayList<>();
        for (GameEvent event : GameEvent.values()) {
            if (event.category() == category) result.add(event);
        }
        return result;
    }

    private static String categoryLabelKey(GameEvent.Category category) {
        return "gui.serialcraft.events.category." + category.name().toLowerCase(Locale.ROOT);
    }

    private static Component intervalLabel(EventsConfig cfg) {
        double seconds = cfg.intervalTicks / 20.0;
        String formatted = String.format(Locale.ROOT, "%.2fs", seconds);
        return Component.translatable("gui.serialcraft.events.interval", formatted);
    }
}
