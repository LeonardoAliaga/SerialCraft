package com.serialcraft.fabric;
import com.serialcraft.platform.PayloadRegistry;
import com.serialcraft.platform.ServerNetworking;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

public final class FabricNetworking implements PayloadRegistry {
    public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, boolean serverbound) {
        (serverbound ? PayloadTypeRegistry.serverboundPlay() : PayloadTypeRegistry.clientboundPlay()).register(type, codec);
    }
    public <T extends CustomPacketPayload> void receiver(CustomPacketPayload.Type<T> type, BiConsumer<T, ServerNetworking.Context> handler) {
        ServerPlayNetworking.registerGlobalReceiver(type, (payload, ctx) -> handler.accept(payload, new ServerNetworking.Context(ctx.player(), ctx.server())));
    }
    public void send(ServerPlayer player, CustomPacketPayload payload) { ServerPlayNetworking.send(player, payload); }
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) { return ServerPlayNetworking.canSend(player, type); }
}
