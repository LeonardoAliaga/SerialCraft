package com.serialcraft.board;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/** Condicion de habilitacion de una placa segun sus pines de entrada. */
public enum LogicMode implements StringRepresentable {
    OR("or"),
    AND("and"),
    XOR("xor");

    public static final LogicMode[] VALUES = values();

    public static final StreamCodec<RegistryFriendlyByteBuf, LogicMode> STREAM_CODEC =
            ByteBufCodecs.idMapper(LogicMode::byId, LogicMode::ordinal).cast();

    private final String name;

    LogicMode(String name) { this.name = name; }

    public static LogicMode byId(int id) {
        return (id >= 0 && id < VALUES.length) ? VALUES[id] : OR;
    }

    /**
     * Evalua la condicion. Sin pines de entrada configurados la placa se
     * considera habilitada: no tiene sentido bloquearla por una condicion
     * que el jugador nunca definio.
     */
    public boolean evaluate(int activePins, int totalPins) {
        if (totalPins == 0) return true;
        return switch (this) {
            case OR  -> activePins > 0;
            case AND -> activePins == totalPins;
            case XOR -> (activePins & 1) == 1;
        };
    }

    @Override public @NotNull String getSerializedName() { return name; }
}
