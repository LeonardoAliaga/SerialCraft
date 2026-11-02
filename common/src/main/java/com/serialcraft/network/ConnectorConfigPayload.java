package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Ajustes de la laptop. baudRate viaja como VAR_INT acotado en el handler:
 * el original aceptaba cualquier int, incluido negativo, y lo guardaba en NBT.
 */
public record ConnectorConfigPayload(
        BlockPos pos,
        int      baudRate,
        boolean  connected,
        int      speedMode
) implements CustomPacketPayload {

    public static final Type<ConnectorConfigPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "connector_config"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConnectorConfigPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, ConnectorConfigPayload::pos,
                    ByteBufCodecs.VAR_INT, ConnectorConfigPayload::baudRate,
                    ByteBufCodecs.BOOL,    ConnectorConfigPayload::connected,
                    ByteBufCodecs.VAR_INT, ConnectorConfigPayload::speedMode,
                    ConnectorConfigPayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
