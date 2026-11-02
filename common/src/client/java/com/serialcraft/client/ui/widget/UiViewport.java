package com.serialcraft.client.ui.widget;

import com.serialcraft.client.ui.ScrollState;
import com.serialcraft.client.ui.io.IoEditorLayout.Rect;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;

import java.util.ArrayList;
import java.util.List;

/** Scroll positioning, clipping and focus visibility shared by editor sections. */
public final class UiViewport {
    private record Entry(AbstractWidget widget, int relativeY) {}
    private final List<Entry> entries = new ArrayList<>();
    private final ScrollState scroll;
    private final Rect bounds;

    public UiViewport(ScrollState scroll, Rect bounds) { this.scroll = scroll; this.bounds = bounds; }
    public void add(AbstractWidget widget, int relativeY) { entries.add(new Entry(widget, relativeY)); }
    public void size(int contentHeight) { scroll.update(bounds.height(), contentHeight); position(); }
    public void position() {
        for (Entry e : entries) {
            e.widget.setY(bounds.y() + e.relativeY - (int) scroll.getScrollAmount());
            e.widget.visible = e.widget.getY() >= bounds.y() && e.widget.getBottom() <= bounds.bottom();
        }
    }
    public void prepareKeyboardFocus() { entries.forEach(e -> e.widget.visible = true); }
    public void revealFocus(GuiEventListener focused) {
        for (Entry e : entries) if (e.widget == focused) {
            if (e.widget.getY() < bounds.y()) scroll.setScrollAmount(e.relativeY);
            else if (e.widget.getBottom() > bounds.bottom()) scroll.setScrollAmount(e.relativeY + e.widget.getHeight() - bounds.height());
        }
        position();
    }
    public void render(GuiGraphicsExtractor gui, int mouseX, int mouseY, float delta) {
        position();
        gui.enableScissor(bounds.x(), bounds.y(), bounds.right(), bounds.bottom());
        for (Entry e : entries) e.widget.extractRenderState(gui, mouseX, mouseY, delta);
        gui.disableScissor();
        scroll.renderScrollbar(gui, bounds.right() - 6, bounds.y(), 6, bounds.height());
    }
}
