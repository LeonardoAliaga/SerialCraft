package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Peticion de la lista de placas del jugador.
 *
 * Sin cuerpo: el campo "dummy" del original era ruido, y ademas invitaba a
 * pensar que el paquete llevaba informacion. Un payload vacio usa
 * StreamCodec.unit, que no lee ni escribe nada del buffer.
 */
public record BoardListRequestPayload() implements CustomPacketPayload {

    public static final BoardListRequestPayload INSTANCE = new BoardListRequestPayload();

    public static final Type<BoardListRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "board_list_req"));

    public static final StreamCodec<RegistryFriendlyByteBuf, BoardListRequestPayload> CODEC =
            StreamCodec.unit(INSTANCE);

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
