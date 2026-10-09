package com.serialcraft.client.ui.widget;

import com.serialcraft.client.ui.ScrollState;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.io.IoEditorLayout.Rect;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/** Local modal. Only its controls are registered while open, so background input is blocked. */
public final class ContextHelpDialog {
    private final Component title;
    private final Component body;
    private final Runnable close;
    private final Runnable confirm;
    private final ScrollState scroll = new ScrollState();
    private final List<SolidButton> buttons = new ArrayList<>();
    private Rect bounds;
    private Rect textBounds;

    public ContextHelpDialog(Component title, Component body, Runnable close) { this(title, body, close, null); }
    public ContextHelpDialog(Component title, Component body, Runnable close, Runnable confirm) {
        this.title = title; this.body = body; this.close = close; this.confirm = confirm;
    }

    public static Rect bounds(int width, int height, int desiredHeight) {
        int w = Math.min(420, width - 24), h = Math.min(desiredHeight, height - 24);
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }
    public void build(PanelUI panel, int width, int height, Font font) {
        int textWidth = Math.min(420, width - 24) - 28;
        int titleHeight = UiDraw.sectionHeaderHeight(font, title, textWidth);
        bounds = bounds(width, height, Math.max(150, font.split(body, textWidth).size() * font.lineHeight + titleHeight + 64));
        textBounds = new Rect(bounds.x() + 12, bounds.y() + 16 + titleHeight, bounds.width() - 24,
                Math.max(1, bounds.height() - titleHeight - 56));
        scroll.update(textBounds.height(), font.split(body, textBounds.width() - 8).size() * font.lineHeight);
        buttons.clear();
        int buttonWidth = confirm == null ? bounds.width() - 24 : (bounds.width() - 30) / 2;
        var closeButton = new SolidButton(bounds.x() + 12, bounds.bottom() - 30, buttonWidth, 22,
                Component.translatable(confirm == null ? "gui.serialcraft.io.help.close" : "gui.serialcraft.io.discard.keep"),
                b -> close.run(), SolidButton.Variant.SOFT) {
            @Override protected void updateWidgetNarration(NarrationElementOutput output) {
                super.updateWidgetNarration(output);
                output.add(NarratedElementType.HINT, title.copy().append("\n").append(body));
            }
        };
        buttons.add(closeButton);
        if (confirm != null) buttons.add(SolidButton.danger(bounds.x() + 18 + buttonWidth, bounds.bottom() - 30,
                buttonWidth, 22, Component.translatable("gui.serialcraft.io.discard.confirm"), b -> confirm.run()));
        buttons.forEach(panel::addInputWidget);
        panel.setFocused(closeButton);
    }
    public void render(GuiGraphicsExtractor gui, Font font, int width, int height, int mouseX, int mouseY) {
        gui.nextStratum();
        gui.fill(0, 0, width, height, UiTheme.OVERLAY);
        gui.fill(bounds.x(), bounds.y(), bounds.right(), bounds.bottom(), UiTheme.BG_PANEL);
        gui.outline(bounds.x(), bounds.y(), bounds.width(), bounds.height(), UiTheme.LINE_STRONG);
        UiDraw.sectionHeader(gui, font, title, bounds.x() + 12, bounds.y() + 12,
                bounds.width() - 28, UiTheme.ACCENT_PRIMARY);
        gui.enableScissor(textBounds.x(), textBounds.y(), textBounds.right(), textBounds.bottom());
        int y = textBounds.y() - (int) scroll.getScrollAmount();
        for (var line : font.split(body, textBounds.width() - 8)) {
            gui.text(font, line, textBounds.x(), y, UiTheme.TEXT_PRIMARY, false);
            y += font.lineHeight;
        }
        gui.disableScissor();
        scroll.renderScrollbar(gui, textBounds.right() - 6, textBounds.y(), 6, textBounds.height());
        buttons.forEach(b -> b.extractRenderState(gui, mouseX, mouseY, 0));
    }
    public boolean clicked(MouseButtonEvent event) {
        if (buttons.stream().anyMatch(b -> b.isMouseOver(event.x(), event.y()))) return false;
        scroll.mouseClicked(event.x(), event.y(), event.button(), textBounds.right() - 6, textBounds.y(), 6, textBounds.height());
        return true;
    }
    public boolean scrolled(double amount) { scroll.mouseScrolled(amount); return true; }
    public boolean dragged(double y) { scroll.mouseDragged(y, textBounds.y(), textBounds.height()); return true; }
    public boolean released(int button) { scroll.mouseReleased(button); return false; }
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { close.run(); return true; }
        if (event.key() == GLFW.GLFW_KEY_PAGE_DOWN || event.key() == GLFW.GLFW_KEY_PAGE_UP) {
            scroll.setScrollAmount(scroll.getScrollAmount() + (event.key() == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * textBounds.height() * .8);
            return true;
        }
        return false;
    }
    public void restoreFocus(PanelUI panel) {
        if (!panel.children().contains(panel.getFocused())) panel.setFocused(buttons.getFirst());
    }
}
