package com.serialcraft.client.ui.widget;

import com.serialcraft.client.ui.ScrollState;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.SpriteIcon;
import net.minecraft.ChatFormatting;
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
        int textWidth = Math.max(32, Math.min(420, width - 24) - 44);
        int titleHeight = font.split(title, Math.max(1, textWidth - 32)).size() * font.lineHeight;
        int headerHeight = titleHeight + 26;
        bounds = bounds(width, height, Math.max(148, font.split(body, textWidth).size() * font.lineHeight + headerHeight + 62));
        textBounds = new Rect(bounds.x() + 18, bounds.y() + headerHeight + 12, bounds.width() - 36,
                Math.max(1, bounds.height() - headerHeight - 58));
        scroll.update(textBounds.height(), font.split(body, textBounds.width() - 8).size() * font.lineHeight);
        buttons.clear();
        int buttonWidth = confirm == null ? Math.min(140, bounds.width() - 36) : (bounds.width() - 42) / 2;
        int closeX = confirm == null ? bounds.x() + (bounds.width() - buttonWidth) / 2 : bounds.x() + 18;
        var closeButton = new SolidButton(closeX, bounds.bottom() - 34, buttonWidth, 22,
                Component.translatable(confirm == null ? "gui.serialcraft.io.help.close" : "gui.serialcraft.io.discard.keep"),
                b -> close.run(), confirm == null ? SolidButton.Variant.PRIMARY : SolidButton.Variant.SOFT) {
            @Override protected void updateWidgetNarration(NarrationElementOutput output) {
                super.updateWidgetNarration(output);
                output.add(NarratedElementType.HINT, title.copy().append("\n").append(body));
            }
        };
        buttons.add(closeButton);
        if (confirm != null) buttons.add(SolidButton.danger(bounds.x() + 24 + buttonWidth, bounds.bottom() - 34,
                buttonWidth, 22, Component.translatable("gui.serialcraft.io.discard.confirm"), b -> confirm.run()));
        buttons.forEach(panel::addInputWidget);
        panel.setFocused(closeButton);
    }
    public void render(GuiGraphicsExtractor gui, Font font, int width, int height, int mouseX, int mouseY) {
        gui.nextStratum();
        gui.fill(0, 0, width, height, 0x70000000);
        UiDraw.pixelRounded(gui, bounds.x() + 3, bounds.y() + 5, bounds.width(), bounds.height(), UiTheme.SHADOW);
        UiDraw.pixelRounded(gui, bounds.x(), bounds.y(), bounds.width(), bounds.height(), UiTheme.LINE_STRONG);
        UiDraw.pixelRounded(gui, bounds.x() + 1, bounds.y() + 1, bounds.width() - 2, bounds.height() - 2, UiTheme.BG_CARD);
        int headerHeight = textBounds.y() - bounds.y() - 12;
        gui.fill(bounds.x() + 4, bounds.y() + 4, bounds.right() - 4,
                bounds.y() + headerHeight, UiTheme.INFO_BG);
        gui.fill(bounds.x() + 4, bounds.y() + 4, bounds.x() + 7, bounds.y() + headerHeight, UiTheme.ACCENT_PRIMARY);
        UiDraw.pixelRounded(gui, bounds.x() + 14, bounds.y() + 12, 20, 20, UiTheme.ACCENT_PRIMARY);
        UiDraw.icon(gui, SpriteIcon.QUEST, bounds.x() + 16, bounds.y() + 14, 16);
        UiDraw.wrappedText(gui, font, title.copy().withStyle(ChatFormatting.BOLD), bounds.x() + 42,
                bounds.y() + 15, bounds.width() - 60, UiTheme.TEXT_PRIMARY);
        gui.fill(bounds.x() + 18, bounds.bottom() - 44, bounds.right() - 18, bounds.bottom() - 43, UiTheme.LINE);
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
        if (!bounds.contains(event.x(), event.y())) {
            close.run(); // Outside click dismisses help or safely cancels confirmation.
            return true;
        }
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
