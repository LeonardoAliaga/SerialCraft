package com.serialcraft.client.ui;

import net.minecraft.ChatFormatting;
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
     * Tarjeta de cristal con borde tenue.
     * @return la Y donde termina la tarjeta, sombra incluida.
     */
    public static int card(GuiGraphicsExtractor gui, int x, int y, int width, int height) {
        glassSurface(gui, x, y, width, height, UiTheme.BG_CARD, UiTheme.LINE, false);
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


    /** Soft, stepped corners drawn with native GUI primitives (no added textures). */
    public static void pixelRounded(GuiGraphicsExtractor gui, int x, int y, int width, int height, int color) {
        if (width < 10 || height < 10) { gui.fill(x, y, x + width, y + height, color); return; }
        gui.fill(x + 4, y, x + width - 4, y + 1, color);
        gui.fill(x + 2, y + 1, x + width - 2, y + 2, color);
        gui.fill(x + 1, y + 2, x + width - 1, y + 4, color);
        gui.fill(x, y + 4, x + width, y + height - 4, color);
        gui.fill(x + 1, y + height - 4, x + width - 1, y + height - 2, color);
        gui.fill(x + 2, y + height - 2, x + width - 2, y + height - 1, color);
        gui.fill(x + 4, y + height - 1, x + width - 4, y + height, color);
    }

    /** Glass uses disjoint fill bands so translucent pixels are blended only once. */
    public static void glassSurface(GuiGraphicsExtractor gui, int x, int y, int width, int height,
                                     int background, int border, boolean glow) {
        if (width < 6 || height < 6) return;
        if (glow && (border >>> 24) != 0) {
            glassOutline(gui, x - 2, y - 2, width + 4, height + 4, (border & 0x00FFFFFF) | 0x12000000);
            glassOutline(gui, x - 1, y - 1, width + 2, height + 2, (border & 0x00FFFFFF) | 0x28000000);
        }
        if ((background >>> 24) != 0) {
            gui.fill(x + 2, y, x + width - 2, y + 1, background);
            gui.fill(x + 1, y + 1, x + width - 1, y + 2, background);
            gui.fill(x, y + 2, x + width, y + height - 2, background);
            gui.fill(x + 1, y + height - 2, x + width - 1, y + height - 1, background);
            gui.fill(x + 2, y + height - 1, x + width - 2, y + height, background);
            gui.fillGradient(x + 2, y + 2, x + width - 2, y + height / 2, 0x10FFFFFF, 0x00FFFFFF);
        }
        if ((border >>> 24) != 0) glassOutline(gui, x, y, width, height, border);
    }

    private static void glassOutline(GuiGraphicsExtractor gui, int x, int y, int width, int height, int color) {
        gui.fill(x + 2, y, x + width - 2, y + 1, color);
        gui.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
        gui.fill(x, y + 2, x + 1, y + height - 2, color);
        gui.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
        gui.fill(x + 1, y + 1, x + 2, y + 2, color);
        gui.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
        gui.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        gui.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
    }

    /** Parallel diagonal cuts; each translucent pixel belongs to a single fill band. */
    public static void angledGlassSurface(GuiGraphicsExtractor gui, int x, int y, int width, int height,
                                           int leftSlant, int rightSlant, int background, int border) {
        if (width < 6 || height < 6) return;
        for (int row = 0; row < height; row++) {
            int corner = row == 0 || row == height - 1 ? 3 : row == 1 || row == height - 2 ? 1 : 0;
            int left = x + (leftSlant == 0 ? corner : leftSlant - row * leftSlant / (height - 1));
            int right = x + width - (rightSlant == 0 ? corner : row * rightSlant / (height - 1));
            if (right <= left) continue;
            if (row == 0 || row == height - 1 || right - left <= 2) {
                gui.fill(left, y + row, right, y + row + 1, border);
                continue;
            }
            int lift = Math.max(0, 22 - row * 44 / height);
            if (row == 3) lift += 18;
            int color = (background & 0xFF000000)
                    | Math.min(255, ((background >> 16) & 255) + lift) << 16
                    | Math.min(255, ((background >> 8) & 255) + lift) << 8
                    | Math.min(255, (background & 255) + lift);
            gui.fill(left, y + row, left + 1, y + row + 1, border);
            gui.fill(left + 1, y + row, right - 1, y + row + 1, color);
            gui.fill(right - 1, y + row, right, y + row + 1, border);
        }
    }

    /** Shared card surface; a zero-height header leaves a quiet background for details. */
    public static void dashboardCard(GuiGraphicsExtractor gui, int x, int y, int width, int height,
                                     int headerHeight, int background, int accent) {
        glassSurface(gui, x, y, width, height, background, UiTheme.LINE_STRONG, false);
        if (headerHeight <= 0) return;
        int bottom = y + Math.min(Math.max(28, headerHeight), height - 4);
        gui.fill(x + 4, y + 4, x + width - 4, bottom, (accent & 0x00FFFFFF) | 0x18000000);
        gui.fill(x + 5, y + 8, x + 8, bottom - 7, accent);
        gui.fill(x + 12, bottom, x + width - 12, bottom + 1, UiTheme.LINE);
    }

    /** Mitad superior de pixelRounded: cabeceras tintadas que respetan las esquinas. */
    public static void pixelRoundedTop(GuiGraphicsExtractor gui, int x, int y, int width, int height, int color) {
        if (width < 10 || height < 5) { gui.fill(x, y, x + width, y + height, color); return; }
        gui.fill(x + 4, y, x + width - 4, y + 1, color);
        gui.fill(x + 2, y + 1, x + width - 2, y + 2, color);
        gui.fill(x + 1, y + 2, x + width - 1, y + 4, color);
        gui.fill(x, y + 4, x + width, y + height, color);
    }

    /**
     * Superficie unica de la interfaz: borde y esquinas escalonadas, riel de acento
     * a la izquierda y cabecera opcional tintada. Sustituye a las tres variantes de
     * tarjeta que convivian (Placas, Bienvenida y Panel).
     */
    public static void surface(GuiGraphicsExtractor gui, int x, int y, int width, int height,
                               int accent, int tint, int headerHeight, boolean hovered) {
        if (width < 18 || height < 18) { card(gui, x, y, width, height, accent, hovered); return; }
        glassSurface(gui, x, y, width, height, UiTheme.BG_CARD,
                hovered ? UiTheme.LINE_STRONG : UiTheme.LINE, hovered);
        if (tint != 0 && headerHeight >= 5)
            pixelRoundedTop(gui, x + 1, y + 1, width - 2, Math.min(headerHeight, height - 6), tint);
        if ((accent >>> 24) != 0) gui.fill(x + 5, y + 9, x + 8, y + height - 9, accent);
    }

    /** Color de una linea de consola segun su prefijo; misma regla en Inicio y Eventos. */
    public static int consoleLineColor(String line) {
        return line.startsWith("TX:") ? UiTheme.OK
             : line.startsWith("RX:") ? UiTheme.CONSOLE_RX
             : line.startsWith("TM:") ? UiTheme.WARN
             : line.startsWith("ERR:") ? UiTheme.ERROR
             : UiTheme.TEXT_ON_DARK;
    }

    /** Altura de la cabecera de consola. */
    public static final int CONSOLE_HEADER_H = 21;

    /** Panel de consola oscuro con barra de titulo; el contenido empieza en y + CONSOLE_HEADER_H. */
    public static void consolePanel(GuiGraphicsExtractor gui, Font font, int x, int y, int width, int height,
                                    Component title, int mouseX, int mouseY) {
        pixelRounded(gui, x, y, width, height, UiTheme.BG_CONSOLE);
        pixelRoundedTop(gui, x, y, width, CONSOLE_HEADER_H, UiTheme.BG_CONSOLE_ALT);
        clippedText(gui, font, title, x + 8, y + 6, width - 16, UiTheme.TEXT_ON_DARK, mouseX, mouseY);
    }

    /** A contextual explanation without another title, border, or modal overlay. */
    public static int helpPanelHeight(Font font, Component body, int width) {
        return 12 + font.split(body, Math.max(1, width - 18)).size() * font.lineHeight;
    }

    public static void helpPanel(GuiGraphicsExtractor gui, Font font, Component body,
                                 int x, int y, int width, int background, int accent) {
        int height = helpPanelHeight(font, body, width);
        gui.fill(x, y, x + width, y + height, background);
        gui.fill(x + 1, y + 3, x + 3, y + height - 3, accent);
        wrappedText(gui, font, body, x + 9, y + 6, Math.max(1, width - 18), UiTheme.TEXT_PRIMARY);
    }

    /** Subsection label aligns with the property rows; no additional divider. */
    public static void dashboardSectionHeader(GuiGraphicsExtractor gui, Font font, Component title,
                                               int x, int y, int width, int accent) {
        wrappedText(gui, font, title.copy().withStyle(ChatFormatting.BOLD),
                x, y, Math.max(1, width), accent);
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
        pixelRounded(gui, x, y, width, 14, background);
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
        pixelRounded(gui, x, y, width, 14, background);
        clippedText(gui, font, text, x + 4, y + 3, width - 8, color, mouseX, mouseY);
        return x + width;
    }

    /** All dashboard card titles share the same size, weight and clipping behavior. */
    public static void dashboardTitle(GuiGraphicsExtractor gui, Font font, Component title,
                                      int x, int y, int width, int mouseX, int mouseY) {
        dashboardTitle(gui, font, title, x, y, width, mouseX, mouseY, UiTheme.TEXT_PRIMARY);
    }

    public static void dashboardTitle(GuiGraphicsExtractor gui, Font font, Component title,
                                      int x, int y, int width, int mouseX, int mouseY, int color) {
        float scale = UiTheme.CARD_TITLE_SCALE;
        int available = Math.max(1, (int) (width / scale));
        Component label = title.copy().withStyle(ChatFormatting.BOLD);
        boolean clipped = font.width(label) > available;
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(scale, scale);
        clippedText(gui, font, label, 0, 0, available, color, -1, -1);
        gui.pose().popMatrix();
        if (clipped && mouseX >= x && mouseX < x + width
                && mouseY >= y && mouseY < y + Math.ceil(font.lineHeight * scale))
            gui.setTooltipForNextFrame(title, mouseX, mouseY);
    }

    public static void pageHeader(GuiGraphicsExtractor gui, Font font, int screenWidth, int screenHeight,
                                   Component accent, int accentColor, Component rest, Component description,
                                   int mouseX, int mouseY) {
        int right = UiTheme.headerLayout(screenWidth, screenHeight).textRight();
        pageHeader(gui, font, screenWidth, screenHeight, accent, accentColor, rest, description,
                right, right, mouseX, mouseY);
    }

    /** Small section eyebrow, a clear page title, then the supporting description. */
    public static void pageHeader(GuiGraphicsExtractor gui, Font font, int screenWidth, int screenHeight,
                                   Component accent, int accentColor, Component rest, Component description,
                                   int titleRight, int descriptionRight, int mouseX, int mouseY) {
        var header = UiTheme.headerLayout(screenWidth, screenHeight);
        int x = header.textX();
        int available = Math.max(1, Math.min(titleRight, header.textRight()) - x);
        boolean compact = UiTheme.compact(screenHeight);
        Component title = compact ? accent.copy().append(" · ").append(rest) : rest;
        Component boldTitle = title.copy().withStyle(ChatFormatting.BOLD);
        float scale = Math.min(UiTheme.TITLE_SCALE,
                (float) available / Math.max(1, font.width(boldTitle)));
        int titleHeight = (int) Math.ceil(font.lineHeight * scale);
        int titleBoxHeight = (int) Math.ceil(font.lineHeight * UiTheme.TITLE_SCALE);
        int y = UiTheme.headerTitleY(screenHeight, font.lineHeight) + (titleBoxHeight - titleHeight) / 2;
        if (!compact) clippedText(gui, font, accent.copy().withStyle(ChatFormatting.BOLD),
                x, header.y(), available, accentColor, mouseX, mouseY);
        gui.pose().pushMatrix();
        gui.pose().translate(x, y);
        gui.pose().scale(scale, scale);
        gui.text(font, boldTitle, 0, 0, UiTheme.HEADER_TEXT, true);
        gui.pose().popMatrix();
        if (description != null && !UiTheme.compact(screenHeight)) {
            int descriptionWidth = Math.min(descriptionRight, header.textRight()) - x;
            if (descriptionWidth > 0)
                clippedText(gui, font, description, x, UiTheme.headerDescriptionY(screenHeight, font.lineHeight),
                        descriptionWidth, UiTheme.HEADER_TEXT_MUTED, mouseX, mouseY);
        }
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
        int labelWidth = width < 220 ? width : width * 2 / 5;
        return font.split(label, Math.max(1, labelWidth)).size() * font.lineHeight
                + (width < 220 ? font.lineHeight + 4 : 0) + 8;
    }

    public static void labelledRow(GuiGraphicsExtractor gui, Font font, int x, int y, int width,
                                   Component label, Component value, int color, int mouseX, int mouseY) {
        int labelWidth = width < 220 ? width : width * 2 / 5;
        wrappedText(gui, font, label, x, y, labelWidth, UiTheme.TEXT_SECONDARY);
        int valueX = width < 220 ? x : x + labelWidth + 8;
        int valueY = width < 220 ? y + font.split(label, Math.max(1, labelWidth)).size() * font.lineHeight + 4 : y;
        clippedText(gui, font, value, valueX, valueY, width - (valueX - x), color, mouseX, mouseY);
    }

    public static final int STAT_TILE_HEIGHT = 30;

    /** Quiet inset fields make addresses and protocol values easy to scan. */
    public static void statTile(GuiGraphicsExtractor gui, Font font, int x, int y, int width,
                                Component label, Component value, int accent, int valueColor,
                                int mouseX, int mouseY) {
        glassSurface(gui, x, y, width, STAT_TILE_HEIGHT, UiTheme.BG_FIELD, UiTheme.LINE_SOFT, false);
        clippedText(gui, font, label, x + 7, y + 4, Math.max(1, width - 14),
                UiTheme.TEXT_SECONDARY, mouseX, mouseY);
        clippedText(gui, font, value.copy().withStyle(ChatFormatting.BOLD),
                x + 7, y + 17, Math.max(1, width - 14), valueColor, mouseX, mouseY);
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
