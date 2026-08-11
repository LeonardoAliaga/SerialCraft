package com.serialcraft.client.ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/** Boton plano con variantes semanticas. */
public class SolidButton extends AbstractWidget {

    @FunctionalInterface
    public interface OnPress {
        void onPress(SolidButton button);
    }

    public enum Variant {
        PRIMARY(UiTheme.ACCENT_PRIMARY, UiTheme.TEXT_INVERSE),
        SUCCESS(UiTheme.OK_DARK,        UiTheme.TEXT_INVERSE),
        DANGER (UiTheme.ERROR_DARK,     UiTheme.TEXT_INVERSE),
        NEUTRAL(0xFFB0B0B0,             0xFF1F1F1F),
        SOFT   (0xFFE6E6DF,             0xFF333333);

        public final int baseColor;
        public final int textColor;

        Variant(int baseColor, int textColor) {
            this.baseColor = baseColor;
            this.textColor = textColor;
        }
    }

    private static final int BORDER_DARKEN   = 24;
    private static final int HOVER_LIGHTEN   = 18;
    private static final float DISABLED_ALPHA = 0.70f;
    private static final float DISABLED_DESAT = 0.55f;

    private Variant variant;
    private final OnPress onPress;

    public SolidButton(int x, int y, int width, int height,
                       Component message, OnPress onPress, Variant variant) {
        super(x, y, width, height, message);
        this.onPress = onPress;
        this.variant = (variant == null) ? Variant.NEUTRAL : variant;
    }

    @Override
    public void onClick(@NotNull MouseButtonEvent event, boolean focused) {
        if (this.onPress != null) this.onPress.onPress(this);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }

    @Override
    public void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float partialTick) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        int base = variant.baseColor;
        int background, border;

        if (!this.active) {
            int muted   = desaturate(base, DISABLED_DESAT);
            background  = applyAlpha(muted, DISABLED_ALPHA);
            border      = applyAlpha(darken(muted, 30), DISABLED_ALPHA);
        } else if (isHoveredOrFocused()) {
            background  = lighten(base, HOVER_LIGHTEN);
            border      = darken(background, BORDER_DARKEN);
        } else {
            background  = base;
            border      = darken(base, BORDER_DARKEN);
        }

        gui.fill(x, y, x + w, y + h, background);
        gui.outline(x, y, w, h, border);

        Font font = Minecraft.getInstance().font;
        int textColor = this.active ? variant.textColor
                                    : applyAlpha(variant.textColor, DISABLED_ALPHA);

        // Recorte al ancho disponible: protege contra traducciones largas.
        String label = font.plainSubstrByWidth(getMessage().getString(), w - 6);
        int textX = x + (w - font.width(label)) / 2;
        int textY = y + (h - font.lineHeight) / 2 + 1;

        gui.text(font, label, textX, textY, textColor, false);
    }

    public Variant getVariant() { return variant; }

    public void setVariant(Variant variant) {
        this.variant = (variant == null) ? Variant.NEUTRAL : variant;
    }

    // ── Constructores de conveniencia ─────────────────────────────────────

    public static SolidButton of(int x, int y, int w, int h, Component msg, OnPress cb, Variant v) {
        return new SolidButton(x, y, w, h, msg, cb, v);
    }
    public static SolidButton primary(int x, int y, int w, int h, Component msg, OnPress cb) {
        return of(x, y, w, h, msg, cb, Variant.PRIMARY);
    }
    public static SolidButton success(int x, int y, int w, int h, Component msg, OnPress cb) {
        return of(x, y, w, h, msg, cb, Variant.SUCCESS);
    }
    public static SolidButton danger(int x, int y, int w, int h, Component msg, OnPress cb) {
        return of(x, y, w, h, msg, cb, Variant.DANGER);
    }
    public static SolidButton soft(int x, int y, int w, int h, Component msg, OnPress cb) {
        return of(x, y, w, h, msg, cb, Variant.SOFT);
    }

    // ── Manipulacion de color ─────────────────────────────────────────────

    private static int applyAlpha(int argb, float factor) {
        int a = Mth.clamp(Math.round(((argb >>> 24) & 0xFF) * factor), 0, 255);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    private static int lighten(int argb, int amount) { return shift(argb,  amount); }
    private static int darken (int argb, int amount) { return shift(argb, -amount); }

    private static int shift(int argb, int delta) {
        int a = (argb >>> 24) & 0xFF;
        int r = Mth.clamp(((argb >>> 16) & 0xFF) + delta, 0, 255);
        int g = Mth.clamp(((argb >>>  8) & 0xFF) + delta, 0, 255);
        int b = Mth.clamp(( argb         & 0xFF) + delta, 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static int desaturate(int argb, float amount) {
        int a = (argb >>> 24) & 0xFF;
        int r = (argb >>> 16) & 0xFF;
        int g = (argb >>>  8) & 0xFF;
        int b =  argb         & 0xFF;
        float gray = r * 0.299f + g * 0.587f + b * 0.114f;
        r = Mth.clamp(Math.round(r + (gray - r) * amount), 0, 255);
        g = Mth.clamp(Math.round(g + (gray - g) * amount), 0, 255);
        b = Mth.clamp(Math.round(b + (gray - b) * amount), 0, 255);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }
}
