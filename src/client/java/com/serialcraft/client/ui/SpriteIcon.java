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

    BELL(5, 5),
    CODE(79, 5),
    CONNECT(153, 5),
    TERMINAL(227, 5),

    DISCONNECT(5, 79),
    DOWN(79, 79),
    HOME(153, 79),
    USB(227, 79),

    LIST(5, 153),
    MONITOR(79, 153),
    QUEST(153, 153),
    WIFI(227, 153);

    /** Tamano de cada icono dentro de la hoja. */
    public static final int SPRITE_SIZE = 64;
    /** Dimensiones reales del PNG. */
    public static final int SHEET_WIDTH  = 296;
    public static final int SHEET_HEIGHT = 222;
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
