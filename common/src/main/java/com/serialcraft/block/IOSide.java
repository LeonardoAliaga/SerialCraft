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

    public IOSide next() { return values()[(ordinal() + 1) % values().length]; }

    /** Two bits per side in CONFIGURABLE_SIDES order. Value 3 is rejected. */
    public static boolean isValidPacked(int packed) {
        if (packed < 0 || packed >= (1 << 10)) return false;
        for (int i = 0; i < 5; i++) if (((packed >> (2 * i)) & 3) == 3) return false;
        return true;
    }

    public static IOSide at(int packed, int index) {
        int id = (packed >> (2 * index)) & 3;
        return id < 3 ? values()[id] : NONE;
    }

    public static int with(int packed, int index, IOSide side) {
        return (packed & ~(3 << (2 * index))) | (side.ordinal() << (2 * index));
    }

    public static int pack(net.minecraft.world.level.block.state.BlockState state) {
        int result = 0;
        for (int i = 0; i < HardwareIOBlock.CONFIGURABLE_SIDES.length; i++) {
            result = with(result, i, state.getValue(HardwareIOBlock.propertyFor(HardwareIOBlock.CONFIGURABLE_SIDES[i])));
        }
        return result;
    }

    public static net.minecraft.world.level.block.state.BlockState apply(
            net.minecraft.world.level.block.state.BlockState state, int packed) {
        if (!isValidPacked(packed)) throw new IllegalArgumentException("invalid connectors");
        for (int i = 0; i < HardwareIOBlock.CONFIGURABLE_SIDES.length; i++) {
            state = state.setValue(HardwareIOBlock.propertyFor(HardwareIOBlock.CONFIGURABLE_SIDES[i]), at(packed, i));
        }
        return state;
    }

    private final String name;

    IOSide(String name) { this.name = name; }

    @Override public @NotNull String getSerializedName() { return name; }
}
