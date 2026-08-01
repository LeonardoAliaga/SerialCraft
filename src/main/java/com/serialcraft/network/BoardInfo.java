package com.serialcraft.network;

import com.serialcraft.board.IoMode;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Vista de solo lectura de una placa, enviada del servidor al cliente para
 * poblar la lista de la pestana "Placas".
 *
 * Los limites de longitud son deliberados: son datos que el servidor reenvia,
 * y una lista de 200 placas con IDs de 32 KB cada una desbordaria el limite de
 * tamano de paquete de Minecraft y desconectaria al cliente.
 */
public record BoardInfo(
        BlockPos pos,
        String id,
        String data,
        IoMode mode,
        boolean enabled
) {
    public static final int MAX_ID_LENGTH   = 32;
    public static final int MAX_DATA_LENGTH = 32;

    public static final StreamCodec<RegistryFriendlyByteBuf, BoardInfo> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,                    BoardInfo::pos,
            ByteBufCodecs.stringUtf8(MAX_ID_LENGTH),  BoardInfo::id,
            ByteBufCodecs.stringUtf8(MAX_DATA_LENGTH),BoardInfo::data,
            IoMode.STREAM_CODEC,                      BoardInfo::mode,
            ByteBufCodecs.BOOL,                       BoardInfo::enabled,
            BoardInfo::new
    );
}
