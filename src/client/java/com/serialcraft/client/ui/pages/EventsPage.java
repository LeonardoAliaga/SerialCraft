package com.serialcraft.client.ui.pages;

import com.serialcraft.client.events.EventsConfig;
import com.serialcraft.client.events.GameEvent;
import com.serialcraft.client.ui.ScrollState;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.client.ui.widget.EventToggle;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pagina "Eventos": seleccion de datos del juego que se envian a la placa
 * y log en vivo del cable.
 *
 * Incluye barra de desplazamiento (scroll) adaptativa para pantallas compactas.
 */
public class EventsPage implements Page {

    private static final int LIST_TOP   = 46;
    private static final int HEADER_H   = 13;
    private static final int ROW_H      = 14;
    private static final int ROW_HEIGHT_WIDGET = ROW_H - 2;
    private static final int INTERVAL_ROW_H = 18;
    private static final int SECTION_GAP    = 10;
    private static final int LINE_HEIGHT   = 11;

    private record CategoryHeader(GameEvent.Category category, int baseY) {}
    private record ToggleItem(EventToggle toggle, int baseY) {}

    private final List<CategoryHeader> categoryHeaders = new ArrayList<>();
    private final List<ToggleItem> toggleItems = new ArrayList<>();
    private final ScrollState scroll = new ScrollState();

    private @Nullable SolidButton intervalButton;
    private int baseIntervalY;
    private int baseLogTop = LIST_TOP;

    private int screenWidth;
    private int screenHeight;

    @Override
    public void init(PanelUI panel, int screenWidth, int screenHeight) {
        this.screenWidth  = screenWidth;
        this.screenHeight = screenHeight;

        categoryHeaders.clear();
        toggleItems.clear();

        int x = UiTheme.contentX(screenWidth);
        int width = Math.max(160, UiTheme.contentWidth(screenWidth) - (scroll.hasScroll() ? 10 : 0));

        EventsConfig cfg = EventsConfig.get();
        int y = LIST_TOP;

        for (GameEvent.Category category : GameEvent.Category.values()) {
            List<GameEvent> events = eventsOf(category);
            if (events.isEmpty()) continue;

            categoryHeaders.add(new CategoryHeader(category, y));
            y += HEADER_H;

            for (GameEvent event : events) {
                EventToggle toggle = new EventToggle(x, y, width, ROW_HEIGHT_WIDGET, event,
                        cfg.isEnabled(event), Component.translatable(event.labelKey()),
                        (ev, checked) -> EventsConfig.get().setEnabled(ev, checked));
                panel.addWidget(toggle);
                toggleItems.add(new ToggleItem(toggle, y));
                y += ROW_H;
            }
        }

        y += SECTION_GAP;
        this.baseIntervalY = y;
        intervalButton = SolidButton.primary(x, y, width, INTERVAL_ROW_H, intervalLabel(cfg), btn -> {
            EventsConfig.get().cycleInterval();
            btn.setMessage(intervalLabel(EventsConfig.get()));
        });
        panel.addWidget(intervalButton);

        this.baseLogTop = y + INTERVAL_ROW_H + SECTION_GAP;
    }

    // ── EVENTOS DE RATÓN Y DESPLAZAMIENTO ─────────────────────────────────────

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        return scroll.mouseScrolled(verticalAmount);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        int x = UiTheme.contentX(screenWidth);
        int width = Math.max(160, UiTheme.contentWidth(screenWidth) - (scroll.hasScroll() ? 10 : 0));
        int viewportTop = 40;
        int viewportBottom = screenHeight - UiTheme.contentMargin(screenWidth);
        int viewportHeight = viewportBottom - viewportTop;
        return scroll.mouseClicked(event.x(), event.y(), event.button(),
                x + width + 2, viewportTop, 8, viewportHeight);
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
        int x     = UiTheme.contentX(screenWidth);
        int width = Math.max(160, UiTheme.contentWidth(screenWidth) - (scroll.hasScroll() ? 10 : 0));

        UiDraw.pageTitle(gui, font, x,
                Component.translatable("gui.serialcraft.events.title"), UiTheme.ACCENT_EVENTS,
                Component.translatable("gui.serialcraft.events.subtitle"));

        int viewportTop = 40;
        int viewportBottom = screenHeight - UiTheme.contentMargin(screenWidth);
        int viewportHeight = Math.max(10, viewportBottom - viewportTop);

        int totalContent = baseLogTop + 100 - viewportTop;
        scroll.update(viewportHeight, totalContent);
        int scrollY = (int) scroll.getScrollAmount();

        // Actualizar posiciones de widgets
        for (ToggleItem item : toggleItems) {
            int currentY = item.baseY() - scrollY;
            item.toggle().setY(currentY);
            boolean inView = (currentY + item.toggle().getHeight() >= viewportTop && currentY <= viewportBottom);
            item.toggle().visible = inView;
            item.toggle().active  = inView;
        }

        if (intervalButton != null) {
            int currentY = baseIntervalY - scrollY;
            intervalButton.setY(currentY);
            boolean inView = (currentY + intervalButton.getHeight() >= viewportTop && currentY <= viewportBottom);
            intervalButton.visible = inView;
            intervalButton.active  = inView;
        }

        gui.enableScissor(x - 2, viewportTop, screenWidth, viewportBottom);

        for (CategoryHeader header : categoryHeaders) {
            int currentY = header.baseY() - scrollY;
            if (currentY >= viewportTop - 10 && currentY <= viewportBottom) {
                gui.text(font, Component.translatable(categoryLabelKey(header.category())),
                        x, currentY + 3, UiTheme.TEXT_MUTED, false);
            }
        }

        renderLog(gui, font, x, width, baseLogTop - scrollY);

        gui.disableScissor();

        if (scroll.hasScroll()) {
            scroll.renderScrollbar(gui, x + width + 3, viewportTop, 6, viewportHeight);
        }
    }

    private void renderLog(GuiGraphicsExtractor gui, Font font, int x, int width, int logY) {
        gui.text(font, Component.translatable("gui.serialcraft.events.log_title"),
                x, logY, UiTheme.TEXT_SECONDARY, false);
        int consoleTop = logY + 12;

        int linesCount = 8;
        List<String> entries = ConnectionManager.recentHistory(linesCount);

        if (entries.isEmpty()) {
            gui.text(font, Component.translatable("gui.serialcraft.events.empty"),
                    x, consoleTop + 10, UiTheme.TEXT_SECONDARY, false);
            return;
        }

        int height = entries.size() * LINE_HEIGHT + 12;
        gui.fill(x, consoleTop, x + width, consoleTop + height, UiTheme.BG_CONSOLE);

        int y = consoleTop + 6;
        for (String entry : entries) {
            int color = entry.startsWith("TX:") ? UiTheme.OK
                      : entry.startsWith("RX:") ? UiTheme.INFO
                      : entry.startsWith("TM:") ? UiTheme.WARN
                      : UiTheme.ERROR;
            gui.text(font, font.plainSubstrByWidth(entry, width - 12),
                    x + 6, y, color, false);
            y += LINE_HEIGHT;
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
