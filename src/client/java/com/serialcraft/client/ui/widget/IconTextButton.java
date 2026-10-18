package com.serialcraft.client.ui.widget;

import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
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
    public static final int HELP_WIDTH = 28;
    public static final int HELP_HEIGHT = 22;

    private final @Nullable SpriteIcon icon;
    private final OnPress onPress;

    private int backgroundColor;
    private int borderColor;
    private int textColor;
    private boolean glassStyle;

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

    /** Opt-in Welcome styling; dashboard buttons keep their existing appearance. */
    public IconTextButton withGlassStyle() {
        glassStyle = true;
        return this;
    }

    @Override
    public void onClick(@NotNull MouseButtonEvent event, boolean focused) {
        if (this.onPress != null) this.onPress.onPress(this);
    }

    /** Compact help action keeps a full label for keyboard narration and its tooltip. */
    public static IconTextButton help(int x, int y, Component title, OnPress onPress) {
        Component label = Component.translatable("gui.serialcraft.io.help.open", title);
        var button = new IconTextButton(x, y, HELP_WIDTH, HELP_HEIGHT, SpriteIcon.QUEST, label, onPress,
                UiTheme.ACCENT_PRIMARY, UiTheme.ACCENT_PRIMARY_DARK);
        button.setTooltip(Tooltip.create(label));
        return button;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (active && visible && isFocused() && (event.key() == GLFW.GLFW_KEY_ENTER
                || event.key() == GLFW.GLFW_KEY_KP_ENTER || event.key() == GLFW.GLFW_KEY_SPACE)) {
            if (onPress != null) onPress.onPress(this);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        int x = getX();
        int y = getY();

        if (glassStyle) {
            UiDraw.glassSurface(gui, x, y, width, height,
                    active ? backgroundColor : UiTheme.WELCOME_FIELD,
                    isHoveredOrFocused() ? UiTheme.WELCOME_TEXT : borderColor, isHoveredOrFocused());
        } else {
            int background = (backgroundColor >>> 24) == 255 ? UiTheme.glassTint(backgroundColor) : backgroundColor;
            UiDraw.glassSurface(gui, x, y, width, height, active ? background : UiTheme.BG_FIELD,
                    isHoveredOrFocused() ? UiTheme.TEXT_PRIMARY : borderColor, active && isHoveredOrFocused());
        }
        if (isFocused()) {
            int inset = glassStyle ? 2 : 1;
            gui.outline(x + inset, y + inset, width - inset * 2, height - inset * 2,
                    glassStyle ? UiTheme.WELCOME_CYAN : UiTheme.TEXT_PRIMARY);
        }

        Font font = Minecraft.getInstance().font;
        int textColorNow = active ? textColor : glassStyle ? UiTheme.WELCOME_MUTED : UiTheme.TEXT_SECONDARY;
        String full = getMessage().getString();
        int iconSpace = icon != null ? SpriteIcon.DRAW_SIZE + PADDING : 0;
        int available = width - PADDING * 2 - iconSpace;
        // Si ni con puntos suspensivos cabe un trozo legible, queda solo el icono centrado.
        boolean iconOnly = icon != null && available < font.width("W…");
        boolean clipped = font.width(full) > available;

        int textX = x + PADDING;
        if (glassStyle && !iconOnly) {
            int contentWidth = iconSpace + Math.min(font.width(full), Math.max(0, available));
            textX = x + Math.max(PADDING, (width - contentWidth) / 2);
        }
        if (icon != null) {
            int iconX = iconOnly ? x + (width - SpriteIcon.DRAW_SIZE) / 2 : textX;
            int iconY = y + (height - SpriteIcon.DRAW_SIZE) / 2;
            gui.blit(RenderPipelines.GUI_TEXTURED, SpriteIcon.TEXTURE,
                    iconX, iconY,
                    icon.u(), icon.v(),
                    SpriteIcon.DRAW_SIZE, SpriteIcon.DRAW_SIZE,
                    SpriteIcon.SPRITE_SIZE, SpriteIcon.SPRITE_SIZE,
                    SpriteIcon.SHEET_WIDTH, SpriteIcon.SHEET_HEIGHT);
            textX += iconSpace;
        }
        if (iconOnly || available <= 0) return;

        int textY = y + (height - font.lineHeight) / 2 + 1;
        // Una traduccion larga se acorta con puntos suspensivos en lugar de
        // cortarse en seco o desbordar el widget vecino.
        if (clipped) {
            int ellipsis = font.width("…");
            String label = font.plainSubstrByWidth(full, Math.max(0, available - ellipsis)) + "…";
            gui.text(font, label, textX, textY, textColorNow, false);
        } else {
            gui.text(font, full, textX, textY, textColorNow, false);
        }
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narration) {
        narration.add(NarratedElementType.TITLE, getMessage());
    }
}
