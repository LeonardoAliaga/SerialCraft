package com.serialcraft.client.ui.widget;

import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Boton con icono opcional a la izquierda y texto. */
public class IconTextButton extends AbstractWidget {

    @FunctionalInterface
    public interface OnPress {
        void onPress(IconTextButton button);
    }

    private static final int PADDING = 6;

    private final @Nullable SpriteIcon icon;
    private final OnPress onPress;

    private int backgroundColor;
    private int borderColor;
    private int textColor;

    public IconTextButton(int x, int y, int width, int height,
                          @Nullable SpriteIcon icon, Component text, OnPress onPress,
                          int backgroundColor, int borderColor, int textColor) {
        super(x, y, width, height, text);
        this.icon            = icon;
        this.onPress         = onPress;
        this.backgroundColor = backgroundColor;
        this.borderColor     = borderColor;
        this.textColor       = textColor;
    }

    public IconTextButton(int x, int y, int width, int height,
                          @Nullable SpriteIcon icon, Component text, OnPress onPress,
                          int backgroundColor, int borderColor) {
        this(x, y, width, height, icon, text, onPress,
             backgroundColor, borderColor, UiTheme.TEXT_INVERSE);
    }

    /** Permite reutilizar el widget al cambiar de estado sin recrearlo. */
    public void setColors(int background, int border, int text) {
        this.backgroundColor = background;
        this.borderColor     = border;
        this.textColor       = text;
    }

    @Override
    public void onClick(@NotNull MouseButtonEvent event, boolean focused) {
        if (this.onPress != null) this.onPress.onPress(this);
    }

    @Override
    protected void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();

        gui.fill(x, y, x + width, y + height, backgroundColor);
        UiDraw.border(gui, x, y, width, height, borderColor);

        int textX = x + PADDING;
        if (icon != null) {
            int iconY = y + (height - SpriteIcon.DRAW_SIZE) / 2;
            gui.blit(RenderPipelines.GUI_TEXTURED, SpriteIcon.TEXTURE,
                    textX, iconY,
                    icon.u(), icon.v(),
                    SpriteIcon.DRAW_SIZE, SpriteIcon.DRAW_SIZE,
                    SpriteIcon.SPRITE_SIZE, SpriteIcon.SPRITE_SIZE,
                    SpriteIcon.SHEET_WIDTH, SpriteIcon.SHEET_HEIGHT);
            textX += SpriteIcon.DRAW_SIZE + PADDING;
        }

        Font font = Minecraft.getInstance().font;
        int textY = y + (height - font.lineHeight) / 2 + 1;

        // Recorte defensivo: si la traduccion es mas larga que el boton, se
        // acorta con puntos suspensivos en vez de desbordarse sobre el widget
        // vecino. El original dibujaba el texto completo pasara lo que pasara,
        // que es como se rompen las interfaces al traducirlas.
        int available = x + width - PADDING - textX;
        String label = font.plainSubstrByWidth(getMessage().getString(), available);
        gui.drawString(font, label, textX, textY, textColor, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, getMessage());
    }
}
