package com.serialcraft.client.ui.io;

/** GUI pixel layout, independent of rendering and Minecraft runtime state. */
public record IoEditorLayout(Rect panel, Rect viewport, Rect status, Rect save, Rect cancel,
                             Rect configurationTab, Rect diagnosticsTab, boolean tabsBesideTitle) {
    public record Rect(int x, int y, int width, int height) {
        public int right() { return x + width; }
        public int bottom() { return y + height; }
        public boolean contains(double px, double py) { return px >= x && px < right() && py >= y && py < bottom(); }
        public boolean contains(Rect other) { return other.x >= x && other.y >= y && other.right() <= right() && other.bottom() <= bottom(); }
    }

    /** Text widths include bold labels and horizontal padding, so switching sections cannot move tabs. */
    public static IoEditorLayout calculate(int screenWidth, int screenHeight, int contentX,
                                            int titleWidth, int configurationWidth, int diagnosticsWidth) {
        int margin = 8;
        int x = Math.max(margin, Math.min(contentX, screenWidth - 180));
        int width = Math.min(640, screenWidth - x - margin);
        int height = screenHeight - margin * 2;
        int y = margin;
        Rect panel = new Rect(x, y, width, height);
        boolean compact = screenHeight < 220;
        int tabGap = 6;
        int available = width - tabGap;
        int firstWidth = configurationWidth, secondWidth = diagnosticsWidth;
        if (firstWidth + secondWidth > available) {
            firstWidth = Math.max(1, available * firstWidth / (firstWidth + secondWidth));
            secondWidth = available - firstWidth;
        }
        int groupWidth = firstWidth + tabGap + secondWidth;
        boolean tabsBesideTitle = titleWidth + groupWidth + 16 <= width;
        int tabsY = tabsBesideTitle ? 18 : compact ? 40 : 62;
        Rect configurationTab = new Rect(panel.right() - groupWidth, tabsY, firstWidth, 24);
        Rect diagnosticsTab = new Rect(panel.right() - secondWidth, tabsY, secondWidth, 24);
        int headerHeight = tabsBesideTitle ? compact ? 44 : 54 : compact ? 62 : 88;
        int footerHeight = compact ? 54 : 66;
        int footerY = panel.bottom() - footerHeight;
        Rect viewport = new Rect(x, y + headerHeight, width, Math.max(1, footerY - y - headerHeight - 6));
        Rect status = new Rect(x, footerY + 3, width, compact ? 18 : 30);
        int buttonWidth = (width - 6) / 2;
        return new IoEditorLayout(panel, viewport, status,
                new Rect(x, panel.bottom() - 28, buttonWidth, 22),
                new Rect(x + 6 + buttonWidth, panel.bottom() - 28, buttonWidth, 22),
                configurationTab, diagnosticsTab, tabsBesideTitle);
    }
}
