package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record ConfigResultPayload(BlockPos pos, boolean accepted, String reason, int requestId) implements CustomPacketPayload {
    public static final Type<ConfigResultPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "config_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigResultPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, ConfigResultPayload::pos, ByteBufCodecs.BOOL, ConfigResultPayload::accepted,
            ByteBufCodecs.stringUtf8(96), ConfigResultPayload::reason,
            ByteBufCodecs.VAR_INT, ConfigResultPayload::requestId, ConfigResultPayload::new);
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
