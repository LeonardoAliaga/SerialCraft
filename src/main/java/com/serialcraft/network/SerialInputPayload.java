package com.serialcraft.network;

import com.serialcraft.SerialCraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;

/**
 * Una linea recibida de la placa fisica del jugador, reenviada al servidor.
 *
 * ESTE ES EL PAQUETE MAS SENSIBLE DEL MOD. Su contenido lo produce hardware
 * fuera del control del servidor y lo reenvia el cliente, es decir: es entrada
 * no confiable dos veces. Se le aplican tres defensas, aqui y en ModNetworking:
 *
 *  - longitud acotada en el codec (una linea serial util no pasa de 64 chars);
 *  - limite de tasa por jugador antes de tocar el mundo;
 *  - el servidor solo aplica el mensaje a placas del propio emisor, en su
 *    propia dimension.
 */
public record SerialInputPayload(String message) implements CustomPacketPayload {

    public static final int MAX_MESSAGE_LENGTH = 64;

    public static final Type<SerialInputPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, "serial_in_packet"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SerialInputPayload> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.stringUtf8(MAX_MESSAGE_LENGTH), SerialInputPayload::message,
                    SerialInputPayload::new
            );

    @Override public @NotNull Type<? extends CustomPacketPayload> type() { return TYPE; }
}
