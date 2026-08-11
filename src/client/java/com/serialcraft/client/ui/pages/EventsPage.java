package com.serialcraft.client.ui.pages;

import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.connection.ConnectionManager;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Pagina de eventos.
 */
public class EventsPage implements Page {

    private static final int LOG_TOP        = 55;
    private static final int VISIBLE_LINES  = 24;
    private static final int LINE_HEIGHT    = 11;

    @Override
    public void init(PanelUI panel, int screenWidth, int screenHeight) {
        // Sin widgets: la pagina es de solo lectura por ahora.
    }

    @Override
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font,
                       int screenWidth, int screenHeight) {
        int x     = UiTheme.contentX(screenWidth);
        int width = screenWidth - x - UiTheme.CONTENT_MARGIN;

        UiDraw.pageTitle(gui, font, x,
                Component.translatable("gui.serialcraft.events.title"), UiTheme.ACCENT_EVENTS,
                Component.translatable("gui.serialcraft.events.subtitle"));

        List<String> entries = ConnectionManager.recentHistory(VISIBLE_LINES);

        if (entries.isEmpty()) {
            gui.text(font, Component.translatable("gui.serialcraft.events.empty"),
                    x, LOG_TOP + 10, UiTheme.TEXT_SECONDARY, false);
            return;
        }

        int height = Math.min(screenHeight - LOG_TOP - 20, entries.size() * LINE_HEIGHT + 12);
        gui.fill(x, LOG_TOP, x + width, LOG_TOP + height, UiTheme.BG_CONSOLE);

        int y = LOG_TOP + 6;
        for (String entry : entries) {
            int color = entry.startsWith("TX:") ? UiTheme.OK
                      : entry.startsWith("RX:") ? UiTheme.INFO
                      : UiTheme.ERROR;
            gui.text(font, font.plainSubstrByWidth(entry, width - 12),
                    x + 6, y, color, false);
            y += LINE_HEIGHT;
            if (y > LOG_TOP + height - LINE_HEIGHT) break;
        }
    }
}
