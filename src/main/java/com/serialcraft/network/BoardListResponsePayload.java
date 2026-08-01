package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Respuesta con las placas del jugador.
 *
 * ByteBufCodecs.list() sin argumento acepta hasta Integer.MAX_VALUE elementos.
 * Aqui se acota explicitamente: aunque el emisor sea el servidor, un limite
 * duro evita que un mundo con miles de placas genere un paquete que supere el
 * maximo de Minecraft y desconecte al jugador al abrir el panel.
 */
public record BoardListResponsePayload(List<BoardInfo> boards) implements CustomPacketPayload {

    public static final int MAX_BOARDS = 256;

    public static final Type<BoardListResponsePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "board_list_res"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoardListResponsePayload> CODEC =
            StreamCodec.composite(
                    BoardInfo.CODEC.apply(ByteBufCodecs.list(MAX_BOARDS)),
                    BoardListResponsePayload::boards,
                    BoardListResponsePayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
