package com.serialcraft.fabric;
import com.serialcraft.platform.ClientNetworking;
import java.util.function.BiConsumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public final class FabricClientNetworking implements ClientNetworking {
    public void sendPayload(CustomPacketPayload payload) { ClientPlayNetworking.send(payload); }
    public boolean canSendPayload(CustomPacketPayload.Type<?> type) { return ClientPlayNetworking.canSend(type); }
    public <T extends CustomPacketPayload> void receiver(CustomPacketPayload.Type<T> type, BiConsumer<T, Context> handler) {
        ClientPlayNetworking.registerGlobalReceiver(type, (payload, ctx) -> handler.accept(payload, new Context(ctx.client())));
    }
}
