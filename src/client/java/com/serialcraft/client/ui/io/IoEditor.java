package com.serialcraft.client.ui.io;

import com.serialcraft.block.entity.HardwareIOBlockEntity;
import com.serialcraft.board.IoMode;
import com.serialcraft.board.LogicMode;
import com.serialcraft.board.SignalType;
import com.serialcraft.client.ui.ScrollState;
import com.serialcraft.client.ui.SolidButton;
import com.serialcraft.client.ui.SpriteIcon;
import com.serialcraft.client.ui.UiTheme;
import com.serialcraft.client.ui.UiDraw;
import com.serialcraft.client.ui.widget.ContextHelpDialog;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.client.ui.widget.OptionButton;
import com.serialcraft.client.ui.widget.SectionTabButton;
import com.serialcraft.client.ui.widget.UiViewport;
import com.serialcraft.network.BoardInfo;
import com.serialcraft.network.ConfigPayload;
import com.serialcraft.network.ConfigResultPayload;
import com.serialcraft.network.IoSnapshot;
import com.serialcraft.screen.PanelUI;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;

/** Configuration, session diagnostics and local help with the shared page header. */
public final class IoEditor {
    private record Text(int y, Component value, int color) {}
    private record Metric(int x, int y, int width, String label, Function<IoSnapshot, Component> value) {}
    private static int nextRequestId;
    private final IoEditorDraft draft;
    private final Runnable close;
    private final EnumMap<IoEditorDraft.Section, ScrollState> scrolls = new EnumMap<>(IoEditorDraft.Section.class);
    private final List<Text> texts = new ArrayList<>();
    private final List<Metric> metrics = new ArrayList<>();
    private final List<AbstractWidget> fixedWidgets = new ArrayList<>();
    private final Map<String, AbstractWidget> focusWidgets = new LinkedHashMap<>();
    private PanelUI panel;
    private IoEditorLayout layout;
    private UiViewport viewport;
    private Font font;
    private int contentWidth;
    private SolidButton saveButton;
    private ContextHelpDialog dialog;
    private String focusKey = "name";

    public IoEditor(BoardInfo target, String dimension, Runnable close) {
        draft = new IoEditorDraft(target, dimension);
        this.close = close;
        for (var section : IoEditorDraft.Section.values()) scrolls.put(section, new ScrollState());
    }
    public IoEditorDraft draft() { return draft; }
    public boolean modal() { return dialog != null; }

    public void build(PanelUI panel, int width, int height) {
        this.panel = panel;
        font = Minecraft.getInstance().font;
        int titleWidth = (int) Math.ceil((font.width(Component.translatable("gui.serialcraft.boards.title"))
                + font.width(Component.translatable("gui.serialcraft.boards.subtitle")) + 4) * UiTheme.TITLE_SCALE);
        layout = IoEditorLayout.calculate(width, height, UiTheme.contentX(width), titleWidth,
                tabWidth(IoEditorDraft.Section.CONFIGURATION), tabWidth(IoEditorDraft.Section.DIAGNOSTICS));
        viewport = new UiViewport(scroll(), layout.viewport());
        contentWidth = layout.viewport().width() - 12;
        texts.clear(); metrics.clear(); directionalTexts.clear(); fixedWidgets.clear(); focusWidgets.clear();
        if (modal()) panel.clearUiWidgets();

        for (var section : IoEditorDraft.Section.values()) {
            var rect = section == IoEditorDraft.Section.CONFIGURATION ? layout.configurationTab() : layout.diagnosticsTab();
            Component label = tr("section." + section.name().toLowerCase(Locale.ROOT));
            var button = new SectionTabButton(rect.x(), rect.y(), rect.width(), rect.height(), label,
                    draft.section() == section, UiTheme.ACCENT_BOARDS_BORDER, b -> {
                        draft.section(section); focusKey = "tab." + section; panel.refresh();
                    });
            if (font.width(label.copy().withStyle(ChatFormatting.BOLD)) > rect.width() - 16)
                button.setTooltip(Tooltip.create(label));
            fixed("tab." + section, button);
        }
        int totalHeight = switch (draft.section()) {
            case CONFIGURATION -> buildConfiguration();
            case DIAGNOSTICS -> buildDiagnostics();
        };
        viewport.size(totalHeight + 8);
        var save = layout.save();
        saveButton = SolidButton.success(save.x(), save.y(), save.width(), save.height(),
                Component.translatable("gui.serialcraft.editor.save"), b -> save());
        fixed("save", saveButton);
        var cancel = layout.cancel();
        fixed("cancel", SolidButton.soft(cancel.x(), cancel.y(), cancel.width(), cancel.height(),
                Component.translatable("gui.serialcraft.editor.cancel"), b -> requestClose(close)));
        refreshSaveButton();
        if (modal()) dialog.build(panel, width, height, font);
        else {
            var focused = focusWidgets.get(focusKey);
            if (focused != null && focused.active) {
                viewport.prepareKeyboardFocus();
                panel.setFocused(focused);
                viewport.revealFocus(focused);
            }
        }
    }

    private int tabWidth(IoEditorDraft.Section section) {
        return font.width(tr("section." + section.name().toLowerCase(Locale.ROOT)).copy().withStyle(ChatFormatting.BOLD)) + 20;
    }

    private int buildConfiguration() {
        var v = draft.values();
        int y = heading(0, "name", null);
        var name = new EditBox(font, layout.viewport().x(), 0, contentWidth, 20,
                Component.translatable("gui.serialcraft.editor.board_id"));
        name.setMaxLength(BoardInfo.MAX_ID_LENGTH); name.setValue(v.name());
        name.setResponder(value -> { draft.name(value); focusKey = "name"; refreshSaveButton(); });
        input("name", name, y, true);
        y = heading(y + 30, "channel", "channel");
        var channel = new EditBox(font, layout.viewport().x(), 0, contentWidth, 20,
                Component.translatable("gui.serialcraft.editor.command"));
        channel.setMaxLength(BoardInfo.MAX_DATA_LENGTH); channel.setValue(v.channel());
        channel.setResponder(value -> { draft.channel(value); focusKey = "channel"; refreshSaveButton(); });
        input("channel", channel, y, true);
        y += 30;
        input("enabled", SolidButton.of(layout.viewport().x(), 0, contentWidth, 24,
                tr(v.enabled() ? "enabled" : "disabled"), b -> change("enabled", () -> draft.enabled(!draft.values().enabled())),
                v.enabled() ? SolidButton.Variant.SUCCESS : SolidButton.Variant.SOFT), y, true);
        y = heading(y + 34, "direction", "direction");
        boolean twoColumns = contentWidth >= 400;
        int choiceWidth = twoColumns ? (contentWidth - 8) / 2 : contentWidth;
        IoMode[] modes = {IoMode.OUTPUT, IoMode.INPUT};
        int directionBottom = y;
        for (int i = 0; i < modes.length; i++) {
            IoMode mode = modes[i];
            int x = layout.viewport().x() + (twoColumns ? i * (choiceWidth + 8) : 0);
            int top = twoColumns ? y : directionBottom;
            input("mode." + mode, new OptionButton(x, 0, choiceWidth, 30,
                    Component.translatable("gui.serialcraft.mode." + mode.getSerializedName()), v.mode() == mode,
                    UiTheme.ACCENT_PRIMARY, b -> change("mode." + mode, () -> draft.mode(mode))), top, true);
            if (twoColumns) {
                Component description = tr(mode.isOutput() ? "direction.tx" : "direction.rx");
                int height = font.split(description, choiceWidth).size() * font.lineHeight;
                directionalTexts.add(new PositionedText(x, top + 36, choiceWidth, description));
                directionBottom = Math.max(directionBottom, top + 36 + height + 10);
            } else directionBottom = paragraph(top + 36, tr(mode.isOutput() ? "direction.tx" : "direction.rx")) + 10;
        }
        y = heading(directionBottom, "signal", "signal");
        int half = (contentWidth - 8) / 2;
        for (SignalType type : SignalType.values()) {
            int index = type == SignalType.DIGITAL ? 0 : 1;
            input("signal." + type, new OptionButton(layout.viewport().x() + index * (half + 8), 0, half, 28,
                    Component.translatable("gui.serialcraft.signal." + type.getSerializedName()), v.signal() == type,
                    UiTheme.ACCENT_PRIMARY, b -> change("signal." + type, () -> draft.signal(type))), y, true);
        }
        y = paragraph(y + 34, tr(v.signal() == SignalType.DIGITAL ? "signal.digital" : "signal.analog"));
        y = heading(y + 10, "logic", "logic");
        int third = (contentWidth - 12) / 3;
        for (LogicMode logic : LogicMode.values()) {
            input("logic." + logic, new OptionButton(layout.viewport().x() + logic.ordinal() * (third + 6), 0,
                    third, 28, Component.translatable("gui.serialcraft.logic." + logic.getSerializedName()), v.logic() == logic,
                    UiTheme.ACCENT_PRIMARY, b -> change("logic." + logic, () -> draft.logic(logic))), y, true);
        }
        y = paragraph(y + 34, tr("logic." + v.logic().getSerializedName()));
        return paragraph(y + 6, tr(v.mode().isOutput() ? "logic.tx" : "logic.rx"));
    }

    private record PositionedText(int x, int y, int width, Component value) {}
    private final List<PositionedText> directionalTexts = new ArrayList<>();

    private int buildDiagnostics() {
        int y = heading(0, "section.diagnostics", "diagnostics");
        y = paragraph(y, tr("diagnostics.notice")) + 10;
        int columns = contentWidth >= 400 ? 2 : 1;
        int width = (contentWidth - (columns - 1) * 8) / columns;
        String[] labels = {"rx", "tx", "read", "processed", "emitted", "connection", "age"};
        List<Function<IoSnapshot, Component>> values = List.of(
                s -> s.received() < 0 ? tr("no_sample") : wire(s.received()),
                s -> s.lastSent() < 0 ? tr("no_send") : wire(s.lastSent()),
                s -> redstone(s.read()), s -> redstone(s.processed()), s -> redstone(s.emitted()),
                s -> tr(s.connected() ? "link.connected" : "link.disconnected"),
                s -> s.receivedAgeTicks() < 0 ? tr("no_sample") : Component.translatable("gui.serialcraft.io.age.value",
                        String.format(Locale.ROOT, "%.1f", s.receivedAgeTicks() / 20d)));
        for (int i = 0; i < labels.length; i++) {
            metrics.add(new Metric(layout.viewport().x() + (i % columns) * (width + 8),
                    y + (i / columns) * 46, width, labels[i], values.get(i)));
        }
        return y + ((labels.length + columns - 1) / columns) * 46;
    }

    private int heading(int y, String key, String help) {
        texts.add(new Text(y + 6, tr(key), UiTheme.TEXT_PRIMARY));
        if (help != null) {
            var button = new IconTextButton(layout.viewport().x() + contentWidth - 28, 0, 28, 22,
                    SpriteIcon.QUEST, Component.translatable("gui.serialcraft.io.help.open", tr("help." + help + ".title")),
                    b -> openHelp(help), UiTheme.ACCENT_PRIMARY, UiTheme.ACCENT_PRIMARY_DARK);
            button.setTooltip(Tooltip.create(button.getMessage()));
            input("help." + help, button, y, false);
        }
        return y + 26;
    }
    private int paragraph(int y, Component text) { return paragraph(y, text, UiTheme.TEXT_SECONDARY); }
    private int paragraph(int y, Component text, int color) {
        texts.add(new Text(y, text, color));
        return y + font.split(text, contentWidth).size() * font.lineHeight;
    }
    private void input(String key, AbstractWidget widget, int y, boolean modifiesDraft) {
        widget.active = !modifiesDraft || !draft.saving();
        viewport.add(widget, y);
        focusWidgets.put(key, widget);
        if (!modal()) panel.addInputWidget(widget);
    }
    private void fixed(String key, AbstractWidget widget) {
        fixedWidgets.add(widget); focusWidgets.put(key, widget);
        if (!modal()) panel.addInputWidget(widget);
    }
    private void change(String key, Runnable action) { action.run(); focusKey = key; panel.refresh(); }
    private ScrollState scroll() { return scrolls.get(draft.section()); }
    private void refreshSaveButton() {
        if (saveButton == null) return;
        saveButton.active = draft.dirty() && !draft.saving();
        saveButton.setMessage(Component.translatable(draft.saving() ? "gui.serialcraft.editor.saving" : "gui.serialcraft.editor.save"));
    }
    private void save() {
        if (!draft.dirty() || draft.saving()) return;
        if (!ClientPlayNetworking.canSend(ConfigPayload.TYPE)) {
            draft.localError("gui.serialcraft.editor.incompatible"); refreshSaveButton(); return;
        }
        if (!draft.beginSave(++nextRequestId, System.nanoTime())) { refreshSaveButton(); return; }
        ClientPlayNetworking.send(draft.payload());
        focusKey = "save"; panel.refresh();
    }
    public void accept(ConfigResultPayload result) {
        if (draft.accept(result) && panel.getCurrentTab() == PanelUI.Tab.BOARDS) panel.refresh();
    }
    public void tick() {
        if (draft.tick(System.nanoTime())) panel.refresh();
        refreshSaveButton();
    }

    private void openHelp(String topic) {
        focusKey = "help." + topic;
        dialog = new ContextHelpDialog(tr("help." + topic + ".title"), tr("help." + topic + ".body"), this::closeDialog);
        panel.refresh();
    }
    private void closeDialog() { dialog = null; panel.refresh(); }
    public void requestClose(Runnable action) {
        if (modal()) { closeDialog(); return; }
        if (!draft.dirty() && !draft.saving()) { action.run(); return; }
        dialog = new ContextHelpDialog(tr("discard.title"), tr(draft.saving() ? "discard.pending" : "discard.body"),
                this::closeDialog, action);
        panel.refresh();
    }

    public void render(GuiGraphicsExtractor gui, Font font, int mouseX, int mouseY) {
        var box = layout.panel();
        UiDraw.pageTitle(gui, font, box.x(),
                Component.translatable("gui.serialcraft.boards.title"), UiTheme.ACCENT_BOARDS,
                Component.translatable("gui.serialcraft.boards.subtitle"));
        Component identity = Component.literal(draft.values().name()).append(" · ").append(Component.translatable(
                "gui.serialcraft.boards.pos", draft.target().pos().getX(), draft.target().pos().getY(), draft.target().pos().getZ()));
        if (panel.height >= 220) {
            gui.text(font, font.plainSubstrByWidth(identity.getString(), box.width()), box.x(), 44, UiTheme.TEXT_SECONDARY, false);
            if (!modal() && mouseX >= box.x() && mouseX < box.right() && mouseY >= 41 && mouseY < 55)
                gui.setTooltipForNextFrame(identity, mouseX, mouseY);
        }
        var area = layout.viewport();
        gui.enableScissor(area.x(), area.y(), area.right(), area.bottom());
        int offset = area.y() - (int) scroll().getScrollAmount();
        for (Text text : texts) UiDraw.wrappedText(gui, font, text.value(), area.x(), offset + text.y(), contentWidth, text.color());
        for (PositionedText text : directionalTexts) UiDraw.wrappedText(gui, font, text.value(), text.x(), offset + text.y(), text.width(), UiTheme.TEXT_SECONDARY);
        IoSnapshot snapshot = snapshot();
        for (Metric metric : metrics) {
            int y = offset + metric.y();
            gui.fill(metric.x(), y, metric.x() + metric.width(), y + 40, UiTheme.BG_CARD);
            gui.outline(metric.x(), y, metric.width(), 40, UiTheme.LINE);
            gui.text(font, tr("diagnostics." + metric.label()), metric.x() + 6, y + 6, UiTheme.TEXT_SECONDARY, false);
            UiDraw.wrappedText(gui, font, metric.value().apply(snapshot), metric.x() + 6, y + 20, metric.width() - 12, UiTheme.TEXT_PRIMARY);
        }
        gui.disableScissor();
        viewport.render(gui, modal() ? -1 : mouseX, modal() ? -1 : mouseY, 0);
        gui.fill(box.x(), layout.status().y() - 3, box.right(), layout.status().y() - 2, UiTheme.LINE);
        Component status = draft.message().isEmpty() ? tr(draft.dirty() ? "unsaved" : "unchanged") : Component.translatable(draft.message());
        int color = switch (draft.saveState()) {
            case ACCEPTED -> UiTheme.OK_DARK;
            case REJECTED, TIMED_OUT -> UiTheme.ERROR_DARK;
            case SAVING -> UiTheme.INFO_DARK;
            default -> draft.dirty() ? UiTheme.WARN_DARK : UiTheme.TEXT_SECONDARY;
        };
        var statusBox = layout.status();
        gui.enableScissor(statusBox.x(), statusBox.y(), statusBox.right(), statusBox.bottom());
        UiDraw.wrappedText(gui, font, status, statusBox.x(), statusBox.y(), statusBox.width(), color);
        gui.disableScissor();
        if (!modal() && statusBox.contains(mouseX, mouseY)) gui.setTooltipForNextFrame(status, mouseX, mouseY);
        fixedWidgets.forEach(w -> w.extractRenderState(gui, modal() ? -1 : mouseX, modal() ? -1 : mouseY, 0));
    }
    public void renderOverlay(GuiGraphicsExtractor gui, Font font, int width, int height, int mouseX, int mouseY) {
        if (modal()) dialog.render(gui, font, width, height, mouseX, mouseY);
    }
    private IoSnapshot snapshot() {
        var level = Minecraft.getInstance().level;
        if (level != null && level.dimension().identifier().toString().equals(draft.dimension())
                && level.getBlockEntity(draft.target().pos()) instanceof HardwareIOBlockEntity io) return io.snapshot();
        return draft.target().snapshot();
    }
    private static Component tr(String suffix) { return Component.translatable("gui.serialcraft.io." + suffix); }
    private static Component wire(int value) { return Component.translatable("gui.serialcraft.io.value.wire", value); }
    private static Component redstone(int value) { return Component.translatable("gui.serialcraft.io.value.redstone", value); }

    public boolean mouseScrolled(double x, double y, double amount) {
        if (modal()) return dialog.scrolled(amount);
        if (!layout.viewport().contains(x, y)) return false;
        scroll().mouseScrolled(amount); viewport.position(); return true;
    }
    public boolean mouseClicked(MouseButtonEvent event) {
        if (modal()) return dialog.clicked(event);
        var area = layout.viewport();
        boolean consumed = scroll().mouseClicked(event.x(), event.y(), event.button(), area.right() - 6, area.y(), 6, area.height());
        viewport.position(); return consumed;
    }
    public boolean mouseDragged(MouseButtonEvent event) {
        if (modal()) return dialog.dragged(event.y());
        boolean consumed = scroll().mouseDragged(event.y(), layout.viewport().y(), layout.viewport().height());
        viewport.position(); return consumed;
    }
    public boolean mouseReleased(MouseButtonEvent event) {
        return modal() ? dialog.released(event.button()) : scroll().mouseReleased(event.button());
    }
    public boolean keyPressed(KeyEvent event) {
        if (modal()) return dialog.keyPressed(event);
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) { requestClose(close); return true; }
        if (event.key() == GLFW.GLFW_KEY_TAB) viewport.prepareKeyboardFocus();
        if (event.key() == GLFW.GLFW_KEY_PAGE_UP || event.key() == GLFW.GLFW_KEY_PAGE_DOWN) {
            scroll().setScrollAmount(scroll().getScrollAmount() + (event.key() == GLFW.GLFW_KEY_PAGE_DOWN ? 1 : -1) * layout.viewport().height() * .8);
            viewport.position(); return true;
        }
        return false;
    }
    public void afterKey() {
        if (modal()) { dialog.restoreFocus(panel); return; }
        if (!panel.children().contains(panel.getFocused()) && focusWidgets.containsKey(focusKey))
            panel.setFocused(focusWidgets.get(focusKey));
        viewport.revealFocus(panel.getFocused());
        focusWidgets.forEach((key, widget) -> { if (widget == panel.getFocused()) focusKey = key; });
    }
}
