package com.serialcraft.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Authoritative diagnostics; -1 means no sample/send. No physical acknowledgement is implied. */
public record IoSnapshot(int sides, int received, int lastSent, int read, int processed,
                         int emitted, boolean connected, long receivedAgeTicks) {
    public static final IoSnapshot EMPTY = new IoSnapshot(0, -1, -1, 0, 0, 0, false, -1);
    public static final StreamCodec<RegistryFriendlyByteBuf, IoSnapshot> CODEC = StreamCodec.of(
            (buf, value) -> {
                buf.writeVarInt(value.sides); buf.writeVarInt(value.received);
                buf.writeVarInt(value.lastSent); buf.writeVarInt(value.read);
                buf.writeVarInt(value.processed); buf.writeVarInt(value.emitted);
                buf.writeBoolean(value.connected); buf.writeVarLong(value.receivedAgeTicks);
            }, buf -> new IoSnapshot(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readVarLong()));
}
