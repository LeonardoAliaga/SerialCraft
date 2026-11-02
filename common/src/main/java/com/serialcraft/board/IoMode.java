package com.serialcraft.board;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import org.jetbrains.annotations.NotNull;

/**
 * Direccion del flujo de datos de una placa IO.
 *
 * Reemplaza los antiguos "int ioMode" magicos (0/1) dispersos por el codigo.
 * Los nombres y ordinales originales permanecen para NBT/protocolo; la UI
 * muestra la direccion completa. Los valores de red invalidos se rechazan.
 */
public enum IoMode implements StringRepresentable {
    /** Minecraft -> placa fisica. La redstone entrante se envia por serial. */
    OUTPUT("output"),
    /** Placa fisica -> Minecraft. El serial entrante genera redstone. */
    INPUT("input");

    public static final IoMode[] VALUES = values();

    public static final StreamCodec<RegistryFriendlyByteBuf, IoMode> STREAM_CODEC =
            ByteBufCodecs.idMapper(IoMode::byId, IoMode::ordinal).cast();

    private final String name;

    IoMode(String name) { this.name = name; }

    /** Network decoding must not turn an invalid direction into a hardware command. */
    public static IoMode byId(int id) {
        if (id < 0 || id >= VALUES.length) throw new io.netty.handler.codec.DecoderException("invalid IO mode");
        return VALUES[id];
    }

    public boolean isInput()  { return this == INPUT; }
    public boolean isOutput() { return this == OUTPUT; }

    @Override public @NotNull String getSerializedName() { return name; }
}
