package com.serialcraft.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Primitivas de dibujo compartidas.
 */
public final class UiDraw {

    private UiDraw() {}

    /** Wrapped paragraphs preserve complete explanations at larger GUI scales. */
    public static void wrappedText(GuiGraphicsExtractor gui, Font font, Component text,
                                   int x, int y, int width, int color) {
        for (var line : font.split(text, Math.max(1, width))) {
            gui.text(font, line, x, y, color, false);
            y += font.lineHeight;
        }
    }

    /**
     * Tarjeta blanca con sombra y linea inferior.
     * @return la Y donde termina la tarjeta, sombra incluida.
     */
    public static int card(GuiGraphicsExtractor gui, int x, int y, int width, int height) {
        gui.fill(x + 2, y + height + 1, x + width + 2, y + height + 3, UiTheme.SHADOW);
        gui.fill(x, y, x + width, y + height, UiTheme.BG_CARD);
        gui.fill(x, y + height, x + width, y + height + 2, UiTheme.LINE);
        return y + height + 3;
    }

    /** Dashboard surface with a semantic top edge; the original surface remains available. */
    public static void card(GuiGraphicsExtractor gui, int x, int y, int width, int height,
                            int accent, boolean hovered) {
        card(gui, x, y, width, height);
        gui.outline(x, y, width, height, hovered ? UiTheme.LINE_STRONG : UiTheme.LINE);
        gui.fill(x + 1, y + 1, x + width - 1, y + 4, accent);
    }

    public static void icon(GuiGraphicsExtractor gui, SpriteIcon icon, int x, int y, int size) {
        gui.blit(RenderPipelines.GUI_TEXTURED, SpriteIcon.TEXTURE, x, y, icon.u(), icon.v(),
                size, size, SpriteIcon.SPRITE_SIZE, SpriteIcon.SPRITE_SIZE,
                SpriteIcon.SHEET_WIDTH, SpriteIcon.SHEET_HEIGHT);
    }

    /** One visible line, with the complete value available on hover. */
    public static void clippedText(GuiGraphicsExtractor gui, Font font, Component text,
                                   int x, int y, int width, int color, int mouseX, int mouseY) {
        width = Math.max(1, width);
        boolean clipped = font.width(text) > width;
        if (clipped) {
            int ellipsisWidth = font.width("…");
            if (width > ellipsisWidth) {
                var line = font.split(text, width - ellipsisWidth).getFirst();
                gui.text(font, line, x, y, color, false);
                gui.text(font, "…", x + font.width(line), y, color, false);
            }
        } else gui.text(font, text, x, y, color, false);
        if (clipped && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + font.lineHeight + 2)
            gui.setTooltipForNextFrame(text, mouseX, mouseY);
    }

    public static int sectionHeaderHeight(Font font, Component title, int width) {
        return font.split(title, Math.max(1, width - 8)).size() * font.lineHeight + 10;
    }

    public static void sectionHeader(GuiGraphicsExtractor gui, Font font, Component title,
                                     int x, int y, int width, int accent) {
        int height = sectionHeaderHeight(font, title, width);
        gui.fill(x, y, x + 2, y + height - 10, accent);
        wrappedText(gui, font, title, x + 8, y, width - 8, UiTheme.TEXT_PRIMARY);
        gui.fill(x, y + height - 5, x + width, y + height - 4, UiTheme.LINE);
    }

    public static int noticeHeight(Font font, Component text, int width) {
        return font.split(text, Math.max(1, width - 20)).size() * font.lineHeight + 16;
    }

    public static void notice(GuiGraphicsExtractor gui, Font font, Component text,
                              int x, int y, int width, int background, int color) {
        int height = noticeHeight(font, text, width);
        gui.fill(x, y, x + width, y + height, background);
        gui.fill(x, y, x + 2, y + height, color);
        wrappedText(gui, font, text, x + 10, y + 8, width - 20, color);
    }

    /**
     * Insignia de texto con fondo.
     * @return la X donde termina la insignia, util para encadenar varias.
     */
    public static int badge(GuiGraphicsExtractor gui, Font font, int x, int y,
                            String text, int background, int textColor) {
        int width = font.width(text) + 8;
        gui.fill(x, y, x + width, y + 14, background);
        gui.text(font, text, x + 4, y + 3, textColor, false);
        return x + width;
    }

    public static int badge(GuiGraphicsExtractor gui, Font font, int x, int y,
                            Component text, int background, int textColor) {
        return badge(gui, font, x, y, text.getString(), background, textColor);
    }

    public static int badge(GuiGraphicsExtractor gui, Font font, int x, int y, int maxWidth,
                            Component text, int background, int color, int mouseX, int mouseY) {
        int width = Math.min(Math.max(8, maxWidth), font.width(text) + 8);
        gui.fill(x, y, x + width, y + 14, background);
        clippedText(gui, font, text, x + 4, y + 3, width - 8, color, mouseX, mouseY);
        return x + width;
    }

    /**
     * Cabecera de pagina: una palabra en color de acento y otra en negro,
     * ambas a escala aumentada.
     */
    public static void pageTitle(GuiGraphicsExtractor gui, Font font, int x,
                                 Component accent, int accentColor, Component rest) {
        float scale = UiTheme.TITLE_SCALE;
        gui.pose().pushMatrix();
        gui.pose().scale(scale, scale);

        int scaledX = (int) (x / scale);
        int scaledY = (int) (UiTheme.TITLE_Y / scale);

        String accentText = accent.getString();
        gui.text(font, accentText, scaledX, scaledY, accentColor, false);
        gui.text(font, rest.getString(),
                scaledX + font.width(accentText) + 4, scaledY, UiTheme.TEXT_PRIMARY, false);

        gui.pose().popMatrix();
    }

    /** Fila etiqueta/valor con la columna de valores alineada. */
    public static void labelledRow(GuiGraphicsExtractor gui, Font font, int x, int y,
                                   Component label, String value, int valueColor) {
        gui.text(font, label, x, y, UiTheme.TEXT_SECONDARY, false);
        gui.text(font, value, x + LABEL_COLUMN_WIDTH, y, valueColor, false);
    }

    public static final int LABEL_COLUMN_WIDTH = 93;

    /** Narrow cards stack the label and value; wider cards align the value column. */
    public static int labelledRowHeight(Font font, Component label, int width) {
        int labelWidth = width < 260 ? width : width * 2 / 5;
        return font.split(label, Math.max(1, labelWidth)).size() * font.lineHeight
                + (width < 260 ? font.lineHeight + 4 : 0) + 8;
    }

    public static void labelledRow(GuiGraphicsExtractor gui, Font font, int x, int y, int width,
                                   Component label, Component value, int color, int mouseX, int mouseY) {
        int labelWidth = width < 260 ? width : width * 2 / 5;
        wrappedText(gui, font, label, x, y, labelWidth, UiTheme.TEXT_SECONDARY);
        int valueX = width < 260 ? x : x + labelWidth + 8;
        int valueY = width < 260 ? y + font.split(label, Math.max(1, labelWidth)).size() * font.lineHeight + 4 : y;
        clippedText(gui, font, value, valueX, valueY, width - (valueX - x), color, mouseX, mouseY);
    }

    /** Campo de texto hundido, con borde y fondo oscuro. */
    public static void inputWell(GuiGraphicsExtractor gui, int x, int y, int width, int height) {
        gui.fill(x, y, x + width, y + height, UiTheme.LINE_STRONG);
        gui.fill(x + 1, y + 1, x + width - 1, y + height - 1, UiTheme.BG_CONSOLE);
    }

    /**
     * Dibuja una forma de onda continua dentro de un rectangulo a partir de muestras en [0, 1].
     */
    public static void polyline(GuiGraphicsExtractor gui, int x, int y, int width, int height,
                                float[] samples, int sampleCount, int color) {
        smoothPolyline(gui, x, y, width, height, samples, sampleCount, color, true);
    }

    /**
     * Dibuja una forma de onda continua ultra-fluida mediante interpolación Catmull-Rom Spline,
     * renderizado anti-escalonado, halo de fósforo y área sombreada estilo osciloscopio profesional.
     *
     * @param samples arreglo de muestras normalizadas en [0, 1] (0 = base inferior, 1 = tope superior).
     * @param sampleCount cantidad de muestras validas a considerar en el arreglo.
     * @param filledArea si es true, rellena el área bajo la curva con un degradado de brillo suave.
     */
    public static void smoothPolyline(GuiGraphicsExtractor gui, int x, int y, int width, int height,
                                      float[] samples, int sampleCount, int color, boolean filledArea) {
        if (width <= 0 || height <= 0 || sampleCount <= 0 || samples == null || samples.length == 0) {
            return;
        }

        int count = Math.min(sampleCount, samples.length);
        int baselineY = y + height - 1;

        if (count == 1 || width == 1) {
            float clamped = Math.clamp(samples[0], 0.0f, 1.0f);
            int py = baselineY - Math.round(clamped * (height - 1));
            gui.fill(x, py, x + width, py + 2, color);
            return;
        }

        int glowColor = (color & 0x00FFFFFF) | 0x30000000;
        int areaColor = (color & 0x00FFFFFF) | 0x18000000;

        int prevY = -1;

        for (int col = 0; col < width; col++) {
            int screenX = x + col;

            float u = (float) col / (float) (width - 1) * (count - 1);
            float sampleVal = sampleCatmullRom(samples, count, u);

            float clamped = Math.clamp(sampleVal, 0.0f, 1.0f);
            int currentY = baselineY - Math.round(clamped * (height - 1));

            if (col == 0) {
                if (filledArea && currentY < baselineY) {
                    gui.fill(screenX, currentY + 1, screenX + 1, baselineY + 1, areaColor);
                }
                gui.fill(screenX, currentY - 1, screenX + 1, currentY + 2, glowColor);
                gui.fill(screenX, currentY, screenX + 1, currentY + 1, color);
            } else {
                int minY = Math.min(prevY, currentY);
                int maxY = Math.max(prevY, currentY);

                if (filledArea && maxY < baselineY) {
                    gui.fill(screenX, maxY + 1, screenX + 1, baselineY + 1, areaColor);
                }

                // Halo de fósforo (glow)
                gui.fill(screenX, minY - 1, screenX + 1, maxY + 2, glowColor);

                // Núcleo de trazo sólido
                gui.fill(screenX, minY, screenX + 1, maxY + 1, color);
            }

            prevY = currentY;
        }
    }

    /**
     * Interpolación de spline cúbica Catmull-Rom para curvas continuas suaves C1 sin vértices angulares.
     */
    private static float sampleCatmullRom(float[] samples, int count, float u) {
        if (count <= 1) return samples[0];
        int i = (int) Math.floor(u);
        float t = u - i;

        int i0 = Math.max(0, i - 1);
        int i1 = Math.clamp(i, 0, count - 1);
        int i2 = Math.clamp(i + 1, 0, count - 1);
        int i3 = Math.clamp(i + 2, 0, count - 1);

        float p0 = samples[i0];
        float p1 = samples[i1];
        float p2 = samples[i2];
        float p3 = samples[i3];

        float t2 = t * t;
        float t3 = t2 * t;

        float val = 0.5f * (
                (2.0f * p1) +
                (-p0 + p2) * t +
                (2.0f * p0 - 5.0f * p1 + 4.0f * p2 - p3) * t2 +
                (-p0 + 3.0f * p1 - 3.0f * p2 + p3) * t3
        );
        return val;
    }
}
