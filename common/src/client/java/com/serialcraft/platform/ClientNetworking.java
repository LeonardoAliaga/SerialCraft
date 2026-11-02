package com.serialcraft.platform;

import java.util.ServiceLoader;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Loaded exclusively by client code; dedicated servers never resolve this service. */
public interface ClientNetworking {
    static ClientNetworking get() { return Holder.INSTANCE; }
    final class Holder {
        private static final ClientNetworking INSTANCE = ServiceLoader.load(ClientNetworking.class, ClientNetworking.class.getClassLoader()).findFirst().orElseThrow();
        private Holder() {}
    }
    record Context(Minecraft client) {}
    static void send(CustomPacketPayload payload) { get().sendPayload(payload); }
    static boolean canSend(CustomPacketPayload.Type<?> type) { return get().canSendPayload(type); }
    static <T extends CustomPacketPayload> void registerGlobalReceiver(CustomPacketPayload.Type<T> type, BiConsumer<T, Context> handler) {
        get().receiver(type, handler);
    }
    void sendPayload(CustomPacketPayload payload);
    boolean canSendPayload(CustomPacketPayload.Type<?> type);
    <T extends CustomPacketPayload> void receiver(CustomPacketPayload.Type<T> type, BiConsumer<T, Context> handler);
}
