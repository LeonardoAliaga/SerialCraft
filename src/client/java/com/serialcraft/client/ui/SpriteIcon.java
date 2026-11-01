package com.serialcraft.client.ui;

import com.serialcraft.SerialCraft;
import net.minecraft.resources.Identifier;

/**
 * Iconos de la hoja de sprites de la interfaz.
 *
 * Las dimensiones de la hoja estaban codificadas a mano en cada llamada a
 * blit() dentro de IconTextButton (64, 64, 296, 222). Ahora viven aqui, junto
 * a las coordenadas que describen: si se regenera la hoja, se cambia un sitio.
 */
public enum SpriteIcon {

    BELL(8, 8),
    CODE(136, 8),
    CONNECT(264, 8),
    TERMINAL(392, 8),

    DISCONNECT(8, 136),
    DOWN(136, 136),
    HOME(264, 136),
    USB(392, 136),

    LIST(8, 264),
    MONITOR(136, 264),
    QUEST(264, 264),
    WIFI(392, 264),
    SETTINGS(8, 392);

    /** Tamano de cada icono dentro de la hoja. */
    public static final int SPRITE_SIZE = 112;
    /** Dimensiones reales del PNG. */
    public static final int SHEET_WIDTH  = 512;
    public static final int SHEET_HEIGHT = 512;
    /** Tamano al que se dibujan los iconos en pantalla. */
    public static final int DRAW_SIZE = 16;

    public static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(
            SerialCraft.MOD_ID, "textures/gui/icons-w.png");

    private final int u;
    private final int v;

    SpriteIcon(int u, int v) { this.u = u; this.v = v; }

    public int u() { return u; }
    public int v() { return v; }
}
