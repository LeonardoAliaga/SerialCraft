package com.serialcraft.client.ui.widget;

import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A wrapped selection with a persistent mark, border, narration and semantic color. */
public final class OptionButton extends SolidButton {
    private final Component label;
    private final boolean selected;
    private final int semanticColor;

    public OptionButton(int x, int y, int width, int height, Component label, boolean selected,
                        int semanticColor, OnPress onPress) {
        super(x, y, width, height, selected ? Component.translatable("gui.serialcraft.io.selected", label) : label,
                onPress, Variant.SOFT);
        this.label = label;
        this.selected = selected;
        this.semanticColor = semanticColor;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        gui.fill(x, y, x + w, y + h, selected ? UiTheme.INFO_BG : UiTheme.BG_CARD);
        int border = isHoveredOrFocused() && active ? UiTheme.TEXT_PRIMARY : selected ? semanticColor : UiTheme.LINE_STRONG;
        gui.outline(x, y, w, h, border);
        if (selected || isFocused()) gui.outline(x + 1, y + 1, w - 2, h - 2, border);
        gui.outline(x + 6, y + (h - 8) / 2, 8, 8, active ? semanticColor : UiTheme.TEXT_SECONDARY);
        if (selected) gui.fill(x + 8, y + (h - 4) / 2, x + 12, y + (h + 4) / 2, semanticColor);
        var font = Minecraft.getInstance().font;
        var lines = font.split(label, Math.max(8, w - 24));
        int textY = y + (h - lines.size() * font.lineHeight) / 2;
        for (var line : lines) {
            gui.text(font, line, x + 20, textY, active ? UiTheme.TEXT_PRIMARY : UiTheme.TEXT_SECONDARY, false);
            textY += font.lineHeight;
        }
    }
}
