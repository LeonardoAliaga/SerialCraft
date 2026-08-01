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
 * Cualquier valor fuera de rango que llegue por red se degrada a OUTPUT en
 * vez de corromper el estado del bloque.
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

    /** Nunca lanza: cualquier id invalido (paquete manipulado) cae en OUTPUT. */
    public static IoMode byId(int id) {
        return (id >= 0 && id < VALUES.length) ? VALUES[id] : OUTPUT;
    }

    public boolean isInput()  { return this == INPUT; }
    public boolean isOutput() { return this == OUTPUT; }

    @Override public @NotNull String getSerializedName() { return name; }
}
