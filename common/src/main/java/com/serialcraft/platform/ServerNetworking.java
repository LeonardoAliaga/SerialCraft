package com.serialcraft.platform;

import java.util.function.BiConsumer;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class ServerNetworking {
    private ServerNetworking() {}
    public record Context(ServerPlayer player, MinecraftServer server) {}
    public static <T extends CustomPacketPayload> void registerGlobalReceiver(CustomPacketPayload.Type<T> type, BiConsumer<T, Context> handler) {
        PayloadRegistry.get().receiver(type, handler);
    }
    public static void send(ServerPlayer player, CustomPacketPayload payload) { PayloadRegistry.get().send(player, payload); }
    public static boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) { return PayloadRegistry.get().canSend(player, type); }
}
