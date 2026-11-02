package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/** Comando que el servidor pide al cliente que entregue a su placa fisica. */
public record SerialOutputPayload(String message, boolean safetyStop) implements CustomPacketPayload {
    public SerialOutputPayload(String message) { this(message, false); }

    public static final Type<SerialOutputPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "serial_out_packet_v2"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SerialOutputPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(SerialInputPayload.MAX_MESSAGE_LENGTH),
                    SerialOutputPayload::message,
                    ByteBufCodecs.BOOL,
                    SerialOutputPayload::safetyStop,
                    SerialOutputPayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
