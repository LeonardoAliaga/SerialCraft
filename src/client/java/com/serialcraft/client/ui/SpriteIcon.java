package com.serialcraft.client.ui;

import net.minecraft.resources.Identifier;

public enum SpriteIcon {

    HOME(153, 79),
    WIFI(227, 153),
    BELL(5, 5),
    CODE(79, 5),
    CONNECT(153, 5),
    DISCONNECT(5, 79),
    DOWN(79, 79),
    LIST(5, 153),
    MONITOR(79, 153),
    QUEST(153, 153),
    TERMINAL(227, 5),
    USB(227, 79);

    public static final int SOURCE_SIZE = 64;

    // spritesheet único de iconos UI
    public static final Identifier ICONS_TEXTURE =
            Identifier.fromNamespaceAndPath(
                    "serialcraft",
                    "textures/gui/icons-w.png"
            );

    private final int u;
    private final int v;

    SpriteIcon(int u, int v) {
        this.u = u;
        this.v = v;
    }

    public int u() { return u; }
    public int v() { return v; }
}
