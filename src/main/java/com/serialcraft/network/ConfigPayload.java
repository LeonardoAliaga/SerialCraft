package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import com.serialcraft.board.IoMode;
import com.serialcraft.board.LogicMode;
import com.serialcraft.board.SignalType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Configuracion completa de una placa, enviada del cliente al servidor.
 *
 * Dos cambios respecto al original, ambos de seguridad:
 *
 *  1. Las cadenas usan ByteBufCodecs.stringUtf8(n) en vez de STRING_UTF8. El
 *     codec por defecto acepta 32767 caracteres; multiplicado por el numero de
 *     placas de un mundo eso son megabytes de NBT persistido y replicado a
 *     cada cliente en getUpdateTag. El limite se aplica en la DECODIFICACION,
 *     asi que un cliente modificado no puede saltarselo: el paquete se rechaza
 *     antes de llegar al handler.
 *
 *  2. mode/signal/logic viajan como enums con codec propio, no como int suelto.
 *     Un id fuera de rango se degrada al valor por defecto en vez de escribirse
 *     tal cual en el BlockEntity, que era lo que pasaba antes (un logicMode=7
 *     caia en el default del switch y dejaba la placa en un estado que no se
 *     podia representar en la UI).
 */
public record ConfigPayload(
        BlockPos   pos,
        IoMode     mode,
        String     targetData,
        SignalType signalType,
        boolean    enabled,
        String     boardId,
        LogicMode  logicMode
) implements CustomPacketPayload {

    public static final Type<ConfigPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "config_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConfigPayload> CODEC =
            StreamCodec.composite(
                    BlockPos.STREAM_CODEC,                                ConfigPayload::pos,
                    IoMode.STREAM_CODEC,                                  ConfigPayload::mode,
                    ByteBufCodecs.stringUtf8(BoardInfo.MAX_DATA_LENGTH),  ConfigPayload::targetData,
                    SignalType.STREAM_CODEC,                              ConfigPayload::signalType,
                    ByteBufCodecs.BOOL,                                   ConfigPayload::enabled,
                    ByteBufCodecs.stringUtf8(BoardInfo.MAX_ID_LENGTH),    ConfigPayload::boardId,
                    LogicMode.STREAM_CODEC,                               ConfigPayload::logicMode,
                    ConfigPayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
