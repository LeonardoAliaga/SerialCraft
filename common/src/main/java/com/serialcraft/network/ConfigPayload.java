package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import com.serialcraft.board.*;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Complete draft, including its dimension and all five connectors. Bounded before validation. */
public record ConfigPayload(BlockPos pos, IoMode mode, String targetData, SignalType signalType,
                            boolean enabled, String boardId, LogicMode logicMode, int sides,
                            String dimension, int requestId) implements CustomPacketPayload {
    public static final Type<ConfigPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "config_packet_v2"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                BlockPos.STREAM_CODEC.encode(buf, p.pos);
                IoMode.STREAM_CODEC.encode(buf, p.mode);
                ByteBufCodecs.stringUtf8(32).encode(buf, p.targetData);
                SignalType.STREAM_CODEC.encode(buf, p.signalType);
                buf.writeBoolean(p.enabled);
                ByteBufCodecs.stringUtf8(32).encode(buf, p.boardId);
                LogicMode.STREAM_CODEC.encode(buf, p.logicMode);
                buf.writeVarInt(p.sides);
                ByteBufCodecs.stringUtf8(128).encode(buf, p.dimension);
                buf.writeVarInt(p.requestId);
            }, buf -> new ConfigPayload(BlockPos.STREAM_CODEC.decode(buf), IoMode.STREAM_CODEC.decode(buf),
                    ByteBufCodecs.stringUtf8(32).decode(buf), SignalType.STREAM_CODEC.decode(buf), buf.readBoolean(),
                    ByteBufCodecs.stringUtf8(32).decode(buf), LogicMode.STREAM_CODEC.decode(buf), buf.readVarInt(),
                    ByteBufCodecs.stringUtf8(128).decode(buf), buf.readVarInt()));
    @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
