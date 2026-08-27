package com.serialcraft.client.ui.pages;

import com.serialcraft.client.events.EventsConfig;
import com.serialcraft.client.events.GameEvent;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.client.ui.widget.EventToggle;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pagina "Eventos": que datos del juego se envian a la placa, y el log en
 * vivo de lo que realmente viaja por el cable.
 *
 * La lista de casillas es puramente una vista de EventsConfig: esta pagina no
 * guarda ningun estado propio de que esta activado, asi que cambiar de
 * pestana y volver, o reabrir el panel, siempre refleja lo que hay en disco.
 * El muestreo real ocurre en GameEventsTracker, en el hilo de tick del
 * cliente; esta clase solo dibuja y escribe la configuracion.
 */
public class EventsPage implements Page {

    private static final int LIST_TOP   = 46;
    private static final int HEADER_H   = 13;
    private static final int ROW_H      = 14;
    private static final int ROW_HEIGHT_WIDGET = ROW_H - 2;
    private static final int INTERVAL_ROW_H = 18;
    private static final int SECTION_GAP    = 10;

    private static final int LINE_HEIGHT   = 11;

    private record CategoryHeader(GameEvent.Category category, int y) {}

    private final List<CategoryHeader> categoryHeaders = new ArrayList<>();
    private int logTop = LIST_TOP;

    @Override
    public void init(PanelUI panel, int screenWidth, int screenHeight) {
        categoryHeaders.clear();

        int x     = UiTheme.contentX(screenWidth);
        int width = screenWidth - x - UiTheme.CONTENT_MARGIN;

        EventsConfig cfg = EventsConfig.get();
        int y = LIST_TOP;

        for (GameEvent.Category category : GameEvent.Category.values()) {
            List<GameEvent> events = eventsOf(category);
            if (events.isEmpty()) continue;

            categoryHeaders.add(new CategoryHeader(category, y));
            y += HEADER_H;

            for (GameEvent event : events) {
                panel.addWidget(new EventToggle(x, y, width, ROW_HEIGHT_WIDGET, event,
                        cfg.isEnabled(event), Component.translatable(event.labelKey()),
                        (ev, checked) -> EventsConfig.get().setEnabled(ev, checked)));
                y += ROW_H;
            }
        }

        y += SECTION_GAP;
        panel.addWidget(SolidButton.primary(x, y, width, INTERVAL_ROW_H, intervalLabel(cfg), btn -> {
            EventsConfig.get().cycleInterval();
            btn.setMessage(intervalLabel(EventsConfig.get()));
        }));

        this.logTop = y + INTERVAL_ROW_H + SECTION_GAP;
    }

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font,
                       int screenWidth, int screenHeight) {
        int x     = UiTheme.contentX(screenWidth);
        int width = screenWidth - x - UiTheme.CONTENT_MARGIN;

        UiDraw.pageTitle(gui, font, x,
                Component.translatable("gui.serialcraft.events.title"), UiTheme.ACCENT_EVENTS,
                Component.translatable("gui.serialcraft.events.subtitle"));

        for (CategoryHeader header : categoryHeaders) {
            gui.text(font, Component.translatable(categoryLabelKey(header.category())),
                    x, header.y() + 3, UiTheme.TEXT_MUTED, false);
        }

        renderLog(gui, font, x, width, screenHeight);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LOG
    // ══════════════════════════════════════════════════════════════════════

    private void renderLog(GuiGraphicsExtractor gui, Font font, int x, int width, int screenHeight) {
        gui.text(font, Component.translatable("gui.serialcraft.events.log_title"),
                x, logTop, UiTheme.TEXT_SECONDARY, false);
        int consoleTop = logTop + 12;

        int available    = Math.max(40, screenHeight - consoleTop - 20);
        int visibleLines = Math.max(1, available / LINE_HEIGHT);

        List<String> entries = ConnectionManager.recentHistory(visibleLines);

        if (entries.isEmpty()) {
            gui.text(font, Component.translatable("gui.serialcraft.events.empty"),
                    x, consoleTop + 10, UiTheme.TEXT_SECONDARY, false);
            return;
        }

        int height = Math.min(available, entries.size() * LINE_HEIGHT + 12);
        gui.fill(x, consoleTop, x + width, consoleTop + height, UiTheme.BG_CONSOLE);

        int y = consoleTop + 6;
        for (String entry : entries) {
            int color = entry.startsWith("TX:") ? UiTheme.OK
                      : entry.startsWith("RX:") ? UiTheme.INFO
                      : UiTheme.ERROR;
            gui.text(font, font.plainSubstrByWidth(entry, width - 12),
                    x + 6, y, color, false);
            y += LINE_HEIGHT;
            if (y > consoleTop + height - LINE_HEIGHT) break;
        }
    }

    // ══════════════════════════════════════════════════════════════════════

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
