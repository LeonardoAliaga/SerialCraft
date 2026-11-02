package com.serialcraft.client.ui.widget;

import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.UiTheme;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Compact page navigation: text and an underline, separate from selectable form options. */
public final class SectionTabButton extends SolidButton {
    private final Component label;
    private final boolean selected;
    private final int accent;

    public SectionTabButton(int x, int y, int width, int height, Component label, boolean selected,
                            int accent, OnPress onPress) {
        super(x, y, width, height, selected ? Component.translatable("gui.serialcraft.io.selected", label) : label,
                onPress, Variant.SOFT);
        this.label = selected ? label.copy().withStyle(ChatFormatting.BOLD) : label;
        this.selected = selected;
        this.accent = accent;
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        if (selected || isHoveredOrFocused())
            gui.fill(x, y, x + w, y + h, selected ? UiTheme.BG_CARD : UiTheme.NEUTRAL_BG);
        if (selected)
            gui.fill(x + 5, y + h - 3, x + w - 5, y + h, active ? accent : UiTheme.TEXT_SECONDARY);
        else if (isHoveredOrFocused() && active)
            gui.fill(x + 5, y + h - 1, x + w - 5, y + h, UiTheme.LINE_STRONG);
        if (isFocused() && active)
            gui.outline(x + 1, y + 1, w - 2, h - 5, UiTheme.TEXT_PRIMARY);

        var font = Minecraft.getInstance().font;
        var text = font.split(label, Math.max(1, w - 16)).getFirst();
        gui.text(font, text, x + (w - font.width(text)) / 2, y + (h - font.lineHeight - 3) / 2,
                active && selected ? UiTheme.TEXT_PRIMARY : UiTheme.TEXT_SECONDARY, false);
    }
}
