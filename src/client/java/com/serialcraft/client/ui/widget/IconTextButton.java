package com.serialcraft.client.ui.widget;

import com.serialcraft.client.ui.SpriteIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

public class IconTextButton extends AbstractWidget {

    @FunctionalInterface
    public interface OnPress {
        void onPress(IconTextButton button);
    }

    private final SpriteIcon icon;
    private final int backgroundColor;
    private final int borderColor;
    private final OnPress onPress;

    private static final int ICON_DRAW_SIZE = 16;

    public IconTextButton(
            int x,
            int y,
            int width,
            int height,
            SpriteIcon icon,
            Component text,
            OnPress onPress,
            int backgroundColor,
            int borderColor
    ) {
        super(x, y, width, height, text);
        this.icon = icon;
        this.onPress = onPress;
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
    }

    @Override
    public void onClick(@NotNull MouseButtonEvent event, boolean focused) {
        if (this.onPress != null) {
            this.onPress.onPress(this);
        }
    }

    @Override
    protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        renderBackground(gui);
        renderIcon(gui);
        renderText(gui);
    }

    private void renderBackground(GuiGraphics gui) {
        int x = getX();
        int y = getY();

        gui.fill(x, y, x + width, y + height, backgroundColor);

        gui.fill(x, y, x + width, y + 2, borderColor);
        gui.fill(x, y + height - 2, x + width, y + height, borderColor);
        gui.fill(x, y, x + 2, y + height, borderColor);
        gui.fill(x + width - 2, y, x + width, y + height, borderColor);
    }

    private void renderIcon(GuiGraphics gui) {
        int iconX = getX() + 6;
        int iconY = getY() + (height - ICON_DRAW_SIZE) / 2;

        gui.blit(
                RenderPipelines.GUI_TEXTURED,
                SpriteIcon.ICONS_TEXTURE,
                iconX,
                iconY,
                icon.u(),
                icon.v(),
                ICON_DRAW_SIZE,
                ICON_DRAW_SIZE,
                64,
                64,
                296,
                222
        );
    }

    private void renderText(GuiGraphics gui) {
        int textX = getX() + 6 + ICON_DRAW_SIZE + 6;
        var font = Minecraft.getInstance().font;

        int textY = getY()
                + (height - font.lineHeight) / 2
                + 1; // ajuste visual vanilla


        gui.drawString(
                Minecraft.getInstance().font,
                getMessage(),
                textX,
                textY,
                0xFFFFFFFF,
                false
        );
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, getMessage());
    }
}
