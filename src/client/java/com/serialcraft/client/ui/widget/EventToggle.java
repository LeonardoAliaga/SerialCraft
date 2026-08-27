package com.serialcraft.client.ui.widget;

import com.serialcraft.client.events.GameEvent;
import com.serialcraft.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

import java.util.function.BiConsumer;

/**
 * Fila de la pestana Eventos: casilla, nombre del dato y la clave con la que
 * viaja por el cable, alineada a la derecha.
 *
 * Toda la fila reacciona al clic, no solo el cuadrado de 10x10: en una lista
 * de once filas apretadas exigir precision de pixel sobre la casilla es lo
 * primero que se maldice en una pantalla pequena o con un ratón impreciso.
 */
public class EventToggle extends AbstractWidget {

    private static final int BOX_SIZE = 10;

    private final GameEvent event;
    private final BiConsumer<GameEvent, Boolean> onToggle;
    private boolean checked;

    public EventToggle(int x, int y, int width, int height, GameEvent event,
                       boolean checked, Component label,
                       BiConsumer<GameEvent, Boolean> onToggle) {
        super(x, y, width, height, label);
        this.event    = event;
        this.checked  = checked;
        this.onToggle = onToggle;
    }

    @Override
    public void onClick(@NotNull MouseButtonEvent evt, boolean focused) {
        checked = !checked;
        onToggle.accept(event, checked);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();

        if (isHoveredOrFocused()) {
            gui.fill(x, y, x + width, y + height, 0x14000000);
        }

        int boxY = y + (height - BOX_SIZE) / 2;
        gui.fill(x, boxY, x + BOX_SIZE, boxY + BOX_SIZE,
                checked ? UiTheme.ACCENT_EVENTS : UiTheme.BG_CARD);
        gui.outline(x, boxY, BOX_SIZE, BOX_SIZE,
                checked ? UiTheme.ACCENT_EVENTS_BORDER : UiTheme.LINE_STRONG);
        if (checked) {
            gui.fill(x + 3, boxY + 3, x + BOX_SIZE - 3, boxY + BOX_SIZE - 3, UiTheme.TEXT_INVERSE);
        }

        Font font = Minecraft.getInstance().font;
        String key = event.wireKey();
        int keyWidth = font.width(key);
        int textY = y + (height - font.lineHeight) / 2 + 1;

        int textX = x + BOX_SIZE + 8;
        int available = (x + width - keyWidth - 6) - textX;
        String label = font.plainSubstrByWidth(getMessage().getString(), Math.max(0, available));

        gui.text(font, label, textX, textY, checked ? UiTheme.TEXT_PRIMARY : UiTheme.TEXT_SECONDARY, false);
        gui.text(font, key, x + width - keyWidth, textY,
                checked ? UiTheme.TEXT_SECONDARY : UiTheme.TEXT_MUTED, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, getMessage());
    }

    public boolean isChecked() { return checked; }
}
