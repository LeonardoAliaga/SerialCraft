package com.serialcraft.block;

import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/** Rol de un conector lateral de la placa IO. */
public enum IOSide implements StringRepresentable {
    /** Sin uso. No lee ni emite. */
    NONE("none"),
    /** Lee redstone del vecino. */
    INPUT("input"),
    /** Emite redstone hacia el vecino. */
    OUTPUT("output");

    private final String name;

    IOSide(String name) { this.name = name; }

    @Override public @NotNull String getSerializedName() { return name; }
}
