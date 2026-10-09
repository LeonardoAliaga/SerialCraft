package com.serialcraft.client.ui.widget;

import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/** Secondary action with a light surface and the application's accent outline. */
public final class OutlineButton extends SolidButton {
    public OutlineButton(int x, int y, int width, int height, Component label, OnPress onPress) {
        super(x, y, width, height, label, onPress, Variant.SOFT);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        gui.fill(x, y, x + w, y + h, active && isHoveredOrFocused() ? UiTheme.INFO_BG : UiTheme.BG_CARD);
        gui.outline(x, y, w, h, !active ? UiTheme.LINE_STRONG : isFocused() ? UiTheme.TEXT_PRIMARY : UiTheme.ACCENT_PRIMARY);
        if (active && isFocused()) gui.outline(x + 1, y + 1, w - 2, h - 2, UiTheme.TEXT_PRIMARY);
        var font = Minecraft.getInstance().font;
        String text = font.plainSubstrByWidth(getMessage().getString(), Math.max(1, w - 12));
        gui.text(font, text, x + (w - font.width(text)) / 2, y + (h - font.lineHeight) / 2 + 1,
                active ? UiTheme.ACCENT_PRIMARY_DARK : UiTheme.TEXT_SECONDARY, false);
    }
}
