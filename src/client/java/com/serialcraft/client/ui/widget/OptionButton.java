package com.serialcraft.client.ui.widget;

import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** A wrapped selection with a persistent mark, border, narration and semantic color. */
public final class OptionButton extends SolidButton {
    private final Component label;
    private final boolean selected;
    private final int semanticColor;
    private final boolean compact;

    public OptionButton(int x, int y, int width, int height, Component label, boolean selected,
                        int semanticColor, OnPress onPress) {
        this(x, y, width, height, label, selected, semanticColor, onPress, false);
    }

    private OptionButton(int x, int y, int width, int height, Component label, boolean selected,
                         int semanticColor, OnPress onPress, boolean compact) {
        super(x, y, width, height, selected ? Component.translatable("gui.serialcraft.io.selected", label) : label,
                onPress, Variant.SOFT);
        this.label = compact && selected ? label.copy().withStyle(ChatFormatting.BOLD) : label;
        this.selected = selected;
        this.semanticColor = semanticColor;
        this.compact = compact;
    }

    /** Single-line option sharing the same selection, keyboard handling and narration. */
    public static OptionButton compact(int x, int y, int width, int height, Component label, boolean selected,
                                       int semanticColor, OnPress onPress) {
        return new OptionButton(x, y, width, height, label, selected, semanticColor, onPress, true);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        if (compact) {
            renderCompact(gui, mouseX, mouseY);
            return;
        }
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

    private void renderCompact(GuiGraphicsExtractor gui, int mouseX, int mouseY) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hot = active && isHoveredOrFocused();
        int background = selected ? (active ? semanticColor : UiTheme.LINE_SOFT)
                : hot ? UiTheme.INFO_BG : UiTheme.BG_CARD;
        int border = hot ? UiTheme.TEXT_PRIMARY : active && selected ? semanticColor
                : active ? UiTheme.LINE_STRONG : UiTheme.LINE;
        int text = !active ? UiTheme.TEXT_SECONDARY : selected ? UiTheme.TEXT_INVERSE : UiTheme.TEXT_PRIMARY;
        gui.fill(x, y, x + w, y + h, background);
        gui.outline(x, y, w, h, border);
        if (active && isFocused()) gui.outline(x + 1, y + 1, w - 2, h - 2, UiTheme.TEXT_INVERSE);
        var font = Minecraft.getInstance().font;
        int textWidth = Math.min(font.width(label), Math.max(1, w - 12));
        UiDraw.clippedText(gui, font, label, x + (w - textWidth) / 2, y + (h - font.lineHeight) / 2 + 1,
                textWidth, text, mouseX, mouseY);
    }
}
