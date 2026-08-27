package com.serialcraft.client.ui.widget;

import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * Tarjeta grande de "metodo de conexion": icono, titulo fijo y una linea de
 * estado que cambia sola (numero de placas, o el estado del servidor Wi-Fi).
 *
 * Reemplaza al par de IconTextButton que habia antes en WelcomePage. Un boton
 * de 22px de alto con un icono y una palabra no deja sitio para decir "3
 * placas detectadas" o "escuchando en 192.168.1.4"; el jugador tenia que leer
 * una segunda linea de texto suelta, en otro color, en otro sitio de la
 * pantalla, para saber si lo que acababa de tocar habia funcionado. Aqui el
 * estado vive pegado al boton que lo causo.
 */
public class MethodCard extends AbstractWidget {

    @FunctionalInterface
    public interface OnPress {
        void onPress();
    }

    private final SpriteIcon icon;
    private final Component title;
    private final Component status;
    private final int backgroundColor;
    private final int borderColor;
    private final int statusColor;
    private final OnPress onPress;

    public MethodCard(int x, int y, int width, int height, SpriteIcon icon,
                      Component title, Component status, int statusColor,
                      int backgroundColor, int borderColor, OnPress onPress) {
        super(x, y, width, height, title);
        this.icon            = icon;
        this.title           = title;
        this.status          = status;
        this.statusColor     = statusColor;
        this.backgroundColor = backgroundColor;
        this.borderColor     = borderColor;
        this.onPress         = onPress;
    }

    @Override
    public void onClick(@NotNull MouseButtonEvent event, boolean focused) {
        onPress.onPress();
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();

        gui.fill(x, y, x + width, y + height, backgroundColor);
        gui.outline(x, y, width, height, isHoveredOrFocused() ? UiTheme.TEXT_INVERSE : borderColor);

        int iconSize = SpriteIcon.DRAW_SIZE;
        int iconX = x + 8;
        int iconY = y + (height - iconSize) / 2;
        gui.blit(RenderPipelines.GUI_TEXTURED, SpriteIcon.TEXTURE,
                iconX, iconY, icon.u(), icon.v(),
                iconSize, iconSize, SpriteIcon.SPRITE_SIZE, SpriteIcon.SPRITE_SIZE,
                SpriteIcon.SHEET_WIDTH, SpriteIcon.SHEET_HEIGHT);

        Font font = Minecraft.getInstance().font;
        int textX = iconX + iconSize + 8;
        int available = x + width - 8 - textX;

        int titleY = y + 8;
        gui.text(font, font.plainSubstrByWidth(title.getString(), available),
                textX, titleY, UiTheme.TEXT_INVERSE, false);

        int statusY = titleY + font.lineHeight + 3;
        gui.text(font, font.plainSubstrByWidth(status.getString(), available),
                textX, statusY, statusColor, false);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, title);
        narration.add(NarratedElementType.HINT, status);
    }
}