package com.serialcraft.client.ui.widget;

import com.serialcraft.client.ui.SpriteIcon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;


public class IconTextButton extends AbstractWidget {

    private final SpriteIcon icon;
    private final int backgroundColor;
    private final int borderColor;

    public IconTextButton(
            int x,
            int y,
            int width,
            int height,
            SpriteIcon icon,
            Component text,
            int backgroundColor,
            int borderColor
    ) {
        super(x, y, width, height, text);
        this.icon = icon;
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
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

        // fondo
        gui.fill(
                x,
                y,
                x + width,
                y + height,
                backgroundColor
        );

        // borde 3px
        gui.fill(x, y, x + width, y + 2, borderColor);                 // top
        gui.fill(x, y + height - 2, x + width, y + height, borderColor); // bottom
        gui.fill(x, y, x + 2, y + height, borderColor);                // left
        gui.fill(x + width - 2, y, x + width, y + height, borderColor);  // right
    }
    private static final int ICON_DRAW_SIZE = 16;

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
                64,64,
                296,
                222
        );
    }
    private void renderText(GuiGraphics gui) {
        int textX = getX() + 6 + ICON_DRAW_SIZE + 6;
        int textY = getY() + (height - Minecraft.getInstance().font.lineHeight) / 2;


        gui.drawString(
                net.minecraft.client.Minecraft.getInstance().font,
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