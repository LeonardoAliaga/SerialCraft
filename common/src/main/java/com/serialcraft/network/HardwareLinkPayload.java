package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Actual client transport changes, including a new connection while still connected. */
public record HardwareLinkPayload(boolean connected, boolean resyncOnly) implements CustomPacketPayload {
    public static final Type<HardwareLinkPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "hardware_link"));
    public static final StreamCodec<RegistryFriendlyByteBuf, HardwareLinkPayload> CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, HardwareLinkPayload::connected,
                    ByteBufCodecs.BOOL, HardwareLinkPayload::resyncOnly, HardwareLinkPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
