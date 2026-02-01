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
import org.jetbrains.annotations.Nullable; // Importante para marcar que puede ser null

public class IconTextButton extends AbstractWidget {

    @FunctionalInterface
    public interface OnPress {
        void onPress(IconTextButton button);
    }

    // El icono ahora puede ser nulo
    @Nullable
    private final SpriteIcon icon;
    private final int backgroundColor;
    private final int borderColor;
    private final int textColor; // Nuevo campo para el color
    private final OnPress onPress;

    private static final int ICON_DRAW_SIZE = 16;
    private static final int PADDING = 6; // Constante para el margen

    public IconTextButton(
            int x,
            int y,
            int width,
            int height,
            @Nullable SpriteIcon icon, // Acepta null
            Component text,
            OnPress onPress,
            int backgroundColor,
            int borderColor,
            int textColor // Nuevo parámetro
    ) {
        super(x, y, width, height, text);
        this.icon = icon;
        this.onPress = onPress;
        this.backgroundColor = backgroundColor;
        this.borderColor = borderColor;
        this.textColor = textColor;
    }

    // Constructor de sobrecarga opcional por si quieres mantener compatibilidad con el blanco por defecto
    public IconTextButton(int x, int y, int width, int height, @Nullable SpriteIcon icon, Component text, OnPress onPress, int backgroundColor, int borderColor) {
        this(x, y, width, height, icon, text, onPress, backgroundColor, borderColor, 0xFFFFFFFF);
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

        // Solo renderizamos el icono si existe
        if (this.icon != null) {
            renderIcon(gui);
        }

        renderText(gui);
    }

    private void renderBackground(GuiGraphics gui) {
        int x = getX();
        int y = getY();

        gui.fill(x, y, x + width, y + height, backgroundColor);

        // Bordes
        gui.fill(x, y, x + width, y + 1, borderColor);
        gui.fill(x, y + height - 1, x + width, y + height, borderColor);
        gui.fill(x, y, x + 1, y + height, borderColor);
        gui.fill(x + width - 1, y, x + width, y + height, borderColor);
    }

    private void renderIcon(GuiGraphics gui) {
        // Validación extra de seguridad (aunque ya se valida en renderWidget)
        if (icon == null) return;

        int iconX = getX() + PADDING;
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
        // Lógica de posición:
        // Si hay icono: X + Padding + Icono + Padding
        // Si NO hay icono: X + Padding (donde empezaría el icono)
        int textX = getX() + PADDING;

        if (this.icon != null) {
            textX += ICON_DRAW_SIZE + PADDING;
        }

        var font = Minecraft.getInstance().font;

        int textY = getY()
                + (height - font.lineHeight) / 2
                + 1; // ajuste visual vanilla

        gui.drawString(
                Minecraft.getInstance().font,
                getMessage(),
                textX,
                textY,
                this.textColor, // Usamos el color personalizado
                false
        );
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, getMessage());
    }
}