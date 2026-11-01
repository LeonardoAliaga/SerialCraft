package com.serialcraft.client.ui.pages;

import com.serialcraft.client.ui.ScrollState;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.client.ui.io.IoEditorLayout.Rect;
import com.serialcraft.client.ui.widget.UiViewport;
import com.serialcraft.client.ui.widget.ContextHelpDialog;
import com.serialcraft.config.OutputSettings;
import com.serialcraft.config.OutputSettings.ConnectAction;
import com.serialcraft.config.OutputSettings.DisconnectAction;
import com.serialcraft.config.SerialConfig;
import com.serialcraft.screen.PanelUI;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local mod settings, available before connecting hardware. Drafts survive tab switches/resizes. */
public final class SettingsPage implements Page {
    private record Text(net.minecraft.util.FormattedCharSequence value, int x, int y, int color) {}
    private final ScrollState scroll = new ScrollState();
    private final List<Text> texts = new ArrayList<>();
    private final List<Rect> cards = new ArrayList<>();
    private PanelUI panel;
    private Font font;
    private Rect view;
    private UiViewport viewport;
    private SolidButton save;
    private ConnectAction connect;
    private DisconnectAction disconnect;
    private String connectValue, disconnectValue;
    private String status = "";
    private ContextHelpDialog dialog;
    private int x, width;

    private static Component tr(String key) { return Component.translatable("gui.serialcraft.settings." + key); }

    @Override public void init(PanelUI panel, int screenWidth, int screenHeight) {
        this.panel = panel;
        font = Minecraft.getInstance().font;
        if (connect == null) resetDraft();
        texts.clear(); cards.clear();
        x = UiTheme.contentX(screenWidth);
        width = screenWidth - x - UiTheme.contentMargin(screenWidth) - 12;
        int top = UiTheme.contentTop(screenHeight);
        view = new Rect(x, top, width + 10, Math.max(24, screenHeight - top - 12));
        viewport = new UiViewport(scroll, view);
        int y = paragraph(tr("scope"), x + 12, top + 8, width - 24, UiTheme.TEXT_SECONDARY) + 12;
        y = section(true, y);
        y = section(false, y + 12);
        y = paragraph(tr("restore_hint"), x + 12, y + 12, width - 24, UiTheme.TEXT_SECONDARY) + 12;
        y = paragraph(tr("limits"), x + 12, y, width - 24, UiTheme.TEXT_SECONDARY) + 14;
        int half = (width - 8) / 2;
        save = SolidButton.success(x, y, half, 24, tr("save"), button -> save());
        widget(save, y);
        widget(SolidButton.soft(x + half + 8, y, width - half - 8, 24, tr("defaults"), button -> {
            connect = ConnectAction.ZERO; disconnect = DisconnectAction.ZERO;
            connectValue = disconnectValue = "0"; status = ""; panel.refresh();
        }), y);
        y += 32;
        if (!valid()) y = paragraph(tr("invalid"), x + 12, y, width - 24, UiTheme.ERROR) + 6;
        if (!status.isEmpty()) y = paragraph(tr(status), x + 12, y, width - 24, UiTheme.TEXT_PRIMARY) + 6;
        widget(SolidButton.soft(x, y, width, 24, tr("back"), button -> panel.closeSettings()), y);
        viewport.size(y + 36 - top);
        refreshSave();
        if (dialog != null) {
            panel.clearUiWidgets();
            dialog.build(panel, screenWidth, screenHeight, font);
        }
    }

    private int section(boolean connecting, int y) {
        int start = y;
        y = paragraph(tr(connecting ? "connect.title" : "disconnect.title"), x + 12, y + 12,
                width - 24, UiTheme.TEXT_PRIMARY) + 6;
        y = paragraph(tr(connecting ? "connect.description" : "disconnect.description"), x + 12, y,
                width - 24, UiTheme.TEXT_SECONDARY) + 10;
        int count = connecting ? ConnectAction.values().length : DisconnectAction.values().length;
        for (int i = 0; i < count; i++) {
            final int index = i;
            String name = connecting ? ConnectAction.values()[i].name() : DisconnectAction.values()[i].name();
            boolean selected = connecting ? connect.ordinal() == i : disconnect.ordinal() == i;
            String key = (connecting ? "connect." : "disconnect.") + name.toLowerCase(Locale.ROOT);
            widget(SolidButton.of(x + 12, y, width - 24, 25, tr(key), button -> {
                if (connecting) connect = ConnectAction.values()[index];
                else disconnect = DisconnectAction.values()[index];
                status = ""; panel.refresh();
            }, selected ? SolidButton.Variant.PRIMARY : SolidButton.Variant.SOFT), y);
            y += 31;
        }
        if (connecting ? connect == ConnectAction.CUSTOM : disconnect == DisconnectAction.CUSTOM) {
            y = paragraph(tr("custom_value"), x + 12, y + 2, width - 24, UiTheme.TEXT_PRIMARY) + 5;
            EditBox field = new EditBox(font, x + 12, y, width - 24, 22, tr("custom_value"));
            field.setMaxLength(3);
            field.setFilter(value -> value.matches("[0-9]{0,3}"));
            field.setValue(connecting ? connectValue : disconnectValue);
            field.setResponder(value -> {
                if (connecting) connectValue = value; else disconnectValue = value;
                field.setTextColor(numberValid(value) ? UiTheme.TEXT_PRIMARY : UiTheme.ERROR);
                field.setTooltip(net.minecraft.client.gui.components.Tooltip.create(tr(numberValid(value) ? "custom_value" : "invalid")));
                status = ""; refreshSave();
            });
            field.setTextColor(numberValid(field.getValue()) ? UiTheme.TEXT_PRIMARY : UiTheme.ERROR);
            widget(field, y); y += 30;
        }
        cards.add(new Rect(x, start, width, y - start + 8));
        return y + 8;
    }

    private void widget(AbstractWidget widget, int y) { viewport.add(widget, y - view.y()); panel.addInputWidget(widget); }
    private int paragraph(Component text, int x, int y, int width, int color) {
        for (var line : font.split(text, width)) {
            texts.add(new Text(line, x, y, color));
            y += font.lineHeight + 3;
        }
        return y;
    }
    private static boolean numberValid(String value) {
        try { int number = Integer.parseInt(value); return number >= 0 && number <= 255; }
        catch (NumberFormatException e) { return false; }
    }
    private boolean valid() {
        return (connect != ConnectAction.CUSTOM || numberValid(connectValue))
                && (disconnect != DisconnectAction.CUSTOM || numberValid(disconnectValue));
    }
    private OutputSettings draft() {
        return new OutputSettings(connect, numberValid(connectValue) ? Integer.parseInt(connectValue) : 0,
                disconnect, numberValid(disconnectValue) ? Integer.parseInt(disconnectValue) : 0);
    }
    private void refreshSave() { if (save != null) save.active = valid() && !draft().equals(SerialConfig.get().outputSettings); }
    private void resetDraft() {
        var settings = SerialConfig.get().outputSettings;
        connect = settings.onConnect(); disconnect = settings.onDisconnect();
        connectValue = Integer.toString(settings.connectValue()); disconnectValue = Integer.toString(settings.disconnectValue());
    }
    private void save() {
        if (!valid()) return;
        var previous = SerialConfig.get().outputSettings;
        SerialConfig.get().outputSettings = draft();
        if (SerialConfig.save()) status = "saved";
        else { SerialConfig.get().outputSettings = previous; status = "save_failed"; }
        panel.refresh();
    }

    public void requestLeave(Runnable action) {
        var previous = SerialConfig.get().outputSettings;
        boolean dirty = connect != null && (!valid() || !draft().equals(previous));
        if (!dirty) { action.run(); return; }
        dialog = new ContextHelpDialog(tr("discard.title"), tr("discard.body"),
                () -> { dialog = null; panel.refresh(); },
                () -> { dialog = null; resetDraft(); status = ""; action.run(); });
        panel.refresh();
    }

    @Override public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, Font font, int width, int height) {
        UiDraw.pageHeader(gui, font, width, height, tr("title"), 0xFF4995B6,
                tr("subtitle"), tr("description"), mouseX, mouseY);
        int offset = (int) scroll.getScrollAmount();
        gui.enableScissor(view.x(), view.y(), view.right() - 8, view.bottom());
        for (Rect card : cards) UiDraw.surface(gui, card.x(), card.y() - offset, card.width(), card.height(), 0xFF4995B6, 0, 0, false);
        for (Text text : texts) gui.text(font, text.value(), text.x(), text.y() - offset, text.color(), false);
        gui.disableScissor();
        viewport.render(gui, mouseX, mouseY, 0);
    }
    @Override public void renderOverlay(GuiGraphicsExtractor gui, int mx, int my, Font font, int width, int height) {
        if (dialog != null) dialog.render(gui, font, width, height, mx, my);
    }
    @Override public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
        if (dialog != null) return dialog.scrolled(vertical);
        if (!view.contains(mx, my)) return false;
        boolean moved = scroll.mouseScrolled(vertical); viewport.position(); return moved;
    }
    @Override public boolean mouseClicked(MouseButtonEvent event, boolean focused) {
        if (dialog != null) return dialog.clicked(event);
        boolean handled = scroll.mouseClicked(event.x(), event.y(), event.button(), view.right() - 6, view.y(), 6, view.height());
        viewport.position(); return handled;
    }
    @Override public boolean mouseReleased(MouseButtonEvent event) { return dialog != null ? dialog.released(event.button()) : scroll.mouseReleased(event.button()); }
    @Override public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (dialog != null) return dialog.dragged(event.y());
        boolean handled = scroll.mouseDragged(event.y(), view.y(), view.height()); viewport.position(); return handled;
    }
    @Override public boolean keyPressed(KeyEvent event) {
        if (dialog != null) return dialog.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_TAB) viewport.prepareKeyboardFocus();
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { panel.closeSettings(); return true; }
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP || event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll.setScrollAmount(scroll.getScrollAmount() + (event.key() == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * view.height() * .8);
            viewport.position(); return true;
        }
        return false;
    }
    @Override public void afterKey() {
        if (dialog != null) dialog.restoreFocus(panel); else viewport.revealFocus(panel.getFocused());
    }
}
