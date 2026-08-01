package com.serialcraft.board;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/** Tipo de senal transportada por el canal serial. */
public enum SignalType implements StringRepresentable {
    /** ON/OFF. Redstone 0 o 15, serial "0" o "1". */
    DIGITAL("digital"),
    /** Proporcional. Redstone 0..15 <-> serial 0..255 (PWM). */
    ANALOG("analog");

    public static final SignalType[] VALUES = values();

    public static final StreamCodec<RegistryFriendlyByteBuf, SignalType> STREAM_CODEC =
            ByteBufCodecs.idMapper(SignalType::byId, SignalType::ordinal).cast();

    public static final int PWM_MAX      = 255;
    public static final int REDSTONE_MAX = 15;

    private final String name;

    SignalType(String name) { this.name = name; }

    public static SignalType byId(int id) {
        return (id >= 0 && id < VALUES.length) ? VALUES[id] : DIGITAL;
    }

    /**
     * Redstone (0-15) -> valor enviado a la placa.
     *
     * Centraliza la conversion para que ambos extremos usen la MISMA escala.
     * El codigo original enviaba 0-255 en salida pero al recibir clampeaba a
     * 0-15, dejando el protocolo asimetrico: un 200 enviado volvia como 15.
     */
    public int redstoneToWire(int redstone) {
        int rs = Math.clamp(redstone, 0, REDSTONE_MAX);
        return this == DIGITAL ? (rs > 0 ? 1 : 0)
                               : Math.round((rs * (float) PWM_MAX) / REDSTONE_MAX);
    }

    /** Inversa de {@link #redstoneToWire(int)}. Tolera basura sin lanzar. */
    public int wireToRedstone(int wire) {
        if (this == DIGITAL) return wire > 0 ? REDSTONE_MAX : 0;
        int pwm = Math.clamp(wire, 0, PWM_MAX);
        return Math.round((pwm * (float) REDSTONE_MAX) / PWM_MAX);
    }

    @Override public @NotNull String getSerializedName() { return name; }
}
