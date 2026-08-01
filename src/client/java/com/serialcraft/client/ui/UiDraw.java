package com.serialcraft.client.ui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Primitivas de dibujo compartidas.
 *
 * Cada uno de estos metodos existia duplicado en el codigo original:
 *  - drawBorder: copiado en SolidButton, PlacasScreen e IconTextButton;
 *  - la tarjeta con sombra: repetida en HomeScreen, PlacasScreen y WelcomeScreen;
 *  - la insignia de color: repetida cinco veces en HomeScreen sola;
 *  - la cabecera de pagina a escala 1.8: repetida en las tres paginas.
 *
 * Las tres copias de la tarjeta ya habian divergido: una dibujaba la sombra a
 * +2 px y otra a +3 px, y una tercera usaba un ancho distinto para el cuerpo y
 * para la sombra, que es el motivo del borde desalineado en la lista de placas.
 */
public final class UiDraw {

    private UiDraw() {}

    /** Rectangulo de un pixel de grosor. */
    public static void border(GuiGraphics gui, int x, int y, int width, int height, int color) {
        gui.fill(x,             y,              x + width, y + 1,      color);
        gui.fill(x,             y + height - 1, x + width, y + height, color);
        gui.fill(x,             y,              x + 1,     y + height, color);
        gui.fill(x + width - 1, y,              x + width, y + height, color);
    }

    /**
     * Tarjeta blanca con sombra y linea inferior.
     * @return la Y donde termina la tarjeta, sombra incluida.
     */
    public static int card(GuiGraphics gui, int x, int y, int width, int height) {
        gui.fill(x + 2, y + height + 1, x + width + 2, y + height + 3, UiTheme.SHADOW);
        gui.fill(x, y, x + width, y + height, UiTheme.BG_CARD);
        gui.fill(x, y + height, x + width, y + height + 2, UiTheme.LINE);
        return y + height + 3;
    }

    /**
     * Insignia de texto con fondo. Se mide el texto para dimensionarla, en vez
     * de usar anchos fijos que se desbordan al traducir a otro idioma: el
     * original usaba +8 px sobre el ancho de la cadena en espanol, y en aleman
     * o ruso el texto se salia de la caja.
     *
     * @return la X donde termina la insignia, util para encadenar varias.
     */
    public static int badge(GuiGraphics gui, Font font, int x, int y,
                            String text, int background, int textColor) {
        int width = font.width(text) + 8;
        gui.fill(x, y, x + width, y + 14, background);
        gui.drawString(font, text, x + 4, y + 3, textColor, false);
        return x + width;
    }

    public static int badge(GuiGraphics gui, Font font, int x, int y,
                            Component text, int background, int textColor) {
        return badge(gui, font, x, y, text.getString(), background, textColor);
    }

    /**
     * Cabecera de pagina: una palabra en color de acento y otra en negro,
     * ambas a escala aumentada.
     */
    public static void pageTitle(GuiGraphics gui, Font font, int x,
                                 Component accent, int accentColor, Component rest) {
        float scale = UiTheme.TITLE_SCALE;
        gui.pose().pushMatrix();
        gui.pose().scale(scale, scale);

        int scaledX = (int) (x / scale);
        int scaledY = (int) (UiTheme.TITLE_Y / scale);

        String accentText = accent.getString();
        gui.drawString(font, accentText, scaledX, scaledY, accentColor, false);
        gui.drawString(font, rest.getString(),
                scaledX + font.width(accentText) + 4, scaledY, UiTheme.TEXT_PRIMARY, false);

        gui.pose().popMatrix();
    }

    /** Fila etiqueta/valor con la columna de valores alineada. */
    public static void labelledRow(GuiGraphics gui, Font font, int x, int y,
                                   Component label, String value, int valueColor) {
        gui.drawString(font, label, x, y, UiTheme.TEXT_SECONDARY, false);
        gui.drawString(font, value, x + LABEL_COLUMN_WIDTH, y, valueColor, false);
    }

    public static final int LABEL_COLUMN_WIDTH = 93;

    /** Campo de texto hundido, con borde y fondo oscuro. */
    public static void inputWell(GuiGraphics gui, int x, int y, int width, int height) {
        gui.fill(x, y, x + width, y + height, UiTheme.LINE_STRONG);
        gui.fill(x + 1, y + 1, x + width - 1, y + height - 1, UiTheme.BG_CONSOLE);
    }
}
