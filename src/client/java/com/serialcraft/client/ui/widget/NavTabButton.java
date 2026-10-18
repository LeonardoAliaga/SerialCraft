package com.serialcraft.client.ui.widget;

import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

/** Dark glass navigation with a persistent selected state and a colored icon. */
public final class NavTabButton extends SolidButton {
    private final SpriteIcon icon;
    private final int accent;
    private final boolean selected;

    public NavTabButton(int x, int y, int width, int height, SpriteIcon icon, Component label,
                        int accent, boolean selected, OnPress onPress) {
        super(x, y, width, height, label, onPress, Variant.SOFT);
        this.icon = icon;
        this.accent = accent;
        this.selected = selected;
        setTooltip(Tooltip.create(label));
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hot = isHoveredOrFocused();
        UiDraw.glassSurface(gui, x, y, w, h,
                selected ? (accent & 0x00FFFFFF) | 0xB0000000 : UiTheme.TAB_INACTIVE_BG,
                selected || hot ? accent : UiTheme.TAB_INACTIVE_BORDER, selected || hot);
        var font = Minecraft.getInstance().font;
        int iconSize = Math.min(20, h - 6);
        boolean iconOnly = w < 72;
        int iconX = iconOnly ? x + (w - iconSize) / 2 : x + 4;
        int iconY = y + (h - iconSize) / 2;
        UiDraw.glassSurface(gui, iconX, iconY, iconSize, iconSize,
                selected ? 0x20FFFFFF : (accent & 0x00FFFFFF) | 0xCC000000, 0, false);
        UiDraw.icon(gui, icon, iconX + (iconSize - 16) / 2, iconY + (iconSize - 16) / 2, 16);
        if (!iconOnly) {
            int textX = iconX + iconSize + 8;
            UiDraw.clippedText(gui, font, getMessage(), textX, y + (h - font.lineHeight) / 2 + 1,
                    Math.max(1, x + w - textX - 6), UiTheme.TEXT_INVERSE, mouseX, mouseY);
        }
        if (isFocused()) gui.outline(x + 2, y + 2, w - 4, h - 4, UiTheme.TEXT_INVERSE);
    }
}
