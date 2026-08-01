package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Enciende o apaga una placa desde la lista del panel.
 *
 * En el original este paquete NO comprobaba dueno en el servidor. Bastaba
 * enviarlo con una posicion arbitraria para apagar la placa de otro jugador
 * en cualquier punto del mundo. La comprobacion se anade ahora en
 * ModNetworking; el payload en si queda igual de simple a proposito.
 */
public record RemoteTogglePayload(BlockPos targetPos) implements CustomPacketPayload {

    public static final Type<RemoteTogglePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "remote_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteTogglePayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC, RemoteTogglePayload::targetPos,
                    RemoteTogglePayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
