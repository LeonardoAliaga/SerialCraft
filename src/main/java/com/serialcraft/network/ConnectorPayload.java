package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/** Avisa al servidor de que la laptop del jugador se conecto o desconecto. */
public record ConnectorPayload(BlockPos pos, boolean connected) implements CustomPacketPayload {

    public static final Type<ConnectorPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "connector_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectorPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ConnectorPayload::pos,
                    ByteBufCodecs.BOOL,    ConnectorPayload::connected,
                    ConnectorPayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
