package com.serialcraft.platform;

import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public interface PayloadRegistry {
    static PayloadRegistry get() { return Holder.INSTANCE; }
    final class Holder {
        private static final PayloadRegistry INSTANCE = ServiceLoader.load(PayloadRegistry.class, PayloadRegistry.class.getClassLoader()).findFirst().orElseThrow();
        private Holder() {}
    }
    static Direction serverboundPlay() { return new Direction(true); }
    static Direction clientboundPlay() { return new Direction(false); }
    record Direction(boolean serverbound) {
        public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec) {
            get().register(type, codec, serverbound);
        }
    }
    <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, boolean serverbound);
    <T extends CustomPacketPayload> void receiver(CustomPacketPayload.Type<T> type, BiConsumer<T, ServerNetworking.Context> handler);
    void send(net.minecraft.server.level.ServerPlayer player, CustomPacketPayload payload);
    boolean canSend(net.minecraft.server.level.ServerPlayer player, CustomPacketPayload.Type<?> type);
}
