package com.serialcraft.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class SolidButton extends AbstractWidget {

    // Interface funcional propia porque ya no usamos la de Button
    public interface OnPress {
        void onPress(SolidButton button);
    }

    public enum Variant {
        PRIMARY(0xFF00838F, 0xFFFFFFFF),
        SUCCESS(0xFF2E7D32, 0xFFFFFFFF),
        DANGER(0xFFC62828, 0xFFFFFFFF),
        NEUTRAL(0xFFB0B0B0, 0xFF1F1F1F),
        SOFT(0xFFE6E6DF, 0xFF333333);

        public final int baseColor;
        public final int textColor;

        Variant(int baseColor, int textColor) {
            this.baseColor = baseColor;
            this.textColor = textColor;
        }
    }

    private Variant variant;
    protected final OnPress onPress;

    public SolidButton(int x, int y, int width, int height, Component message, OnPress onPress, Variant variant) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        this.variant = (variant == null) ? Variant.NEUTRAL : variant;
    }

    // --- Lógica de Click (Requerida en AbstractWidget) ---
    public void onClick(double mouseX, double mouseY) {
        this.onPress.onPress(this);
    }

    // --- Accesibilidad / Narrador (Requerido en AbstractWidget) ---
    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    // --- Renderizado ---
    @Override
    public void renderWidget(GuiGraphics gui, int mouseX, int mouseY, float partialTick) {
        int x = this.getX();
        int y = this.getY();
        int w = this.getWidth();
        int h = this.getHeight();

        boolean hovered = this.isHoveredOrFocused();

        int base = this.variant.baseColor;
        int bg = base;
        int border = darken(base, 24);

        if (!this.active) {
            bg = applyAlpha(desaturate(base, 0.55f), 0.70f);
            border = applyAlpha(darken(desaturate(base, 0.55f), 30), 0.70f);
        } else if (hovered) {
            bg = lighten(base, 18);
            border = darken(bg, 24);
        }

        // Fondo + borde
        gui.fill(x, y, x + w, y + h, bg);
        drawBorder(gui, x, y, w, h, border);

        // Texto centrado
        Font font = Minecraft.getInstance().font;
        Component msg = this.getMessage();
        int textColor = this.active ? this.variant.textColor : applyAlpha(this.variant.textColor, 0.70f);

        // Cálculo de posición centrado
        int textX = x + (w - font.width(msg)) / 2;
        int textY = y + (h - 8) / 2;

        gui.drawString(font, msg, textX, textY, textColor, false);
    }

    // --- Getters & Setters ---

    public Variant getVariant() {
        return this.variant;
    }

    public void setVariant(Variant variant) {
        this.variant = (variant == null) ? Variant.NEUTRAL : variant;
    }

    // --- Métodos estáticos de utilidad (Builders) ---

    public static SolidButton of(int x, int y, int width, int height, Component message, OnPress onPress, Variant variant) {
        return new SolidButton(x, y, width, height, message, onPress, variant);
    }

    public static SolidButton primary(int x, int y, int width, int height, Component message, OnPress onPress) {
        return of(x, y, width, height, message, onPress, Variant.PRIMARY);
    }

    public static SolidButton success(int x, int y, int width, int height, Component message, OnPress onPress) {
        return of(x, y, width, height, message, onPress, Variant.SUCCESS);
    }

    public static SolidButton danger(int x, int y, int width, int height, Component message, OnPress onPress) {
        return of(x, y, width, height, message, onPress, Variant.DANGER);
    }

    public static SolidButton neutral(int x, int y, int width, int height, Component message, OnPress onPress) {
        return of(x, y, width, height, message, onPress, Variant.NEUTRAL);
    }

    public static SolidButton soft(int x, int y, int width, int height, Component message, OnPress onPress) {
        return of(x, y, width, height, message, onPress, Variant.SOFT);
    }

    // --- Utiles de Color ---

    private static void drawBorder(GuiGraphics gui, int x, int y, int width, int height, int color) {
        gui.fill(x, y, x + width, y + 1, color);              // Top
        gui.fill(x, y + height - 1, x + width, y + height, color); // Bottom
        gui.fill(x, y, x + 1, y + height, color);              // Left
        gui.fill(x + width - 1, y, x + width, y + height, color);  // Right
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Mth.clamp(Math.round(((argb >>> 24) & 0xFF) * alpha), 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int lighten(int argb, int amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        r = Mth.clamp(r + amount, 0, 255);
        g = Mth.clamp(g + amount, 0, 255);
        b = Mth.clamp(b + amount, 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int darken(int argb, int amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;
        r = Mth.clamp(r - amount, 0, 255);
        g = Mth.clamp(g - amount, 0, 255);
        b = Mth.clamp(b - amount, 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int desaturate(int argb, float amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>> 8) & 0xFF;
        int b = argb & 0xFF;

        float gray = (r * 0.299f + g * 0.587f + b * 0.114f);
        r = Mth.clamp(Math.round(r + (gray - r) * amount), 0, 255);
        g = Mth.clamp(Math.round(g + (gray - g) * amount), 0, 255);
        b = Mth.clamp(Math.round(b + (gray - b) * amount), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}