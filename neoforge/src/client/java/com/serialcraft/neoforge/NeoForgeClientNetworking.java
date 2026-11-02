package com.serialcraft.neoforge;

import com.serialcraft.platform.ClientNetworking;
import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;

public final class NeoForgeClientNetworking implements ClientNetworking {
    private RegisterClientPayloadHandlersEvent event;
    public void begin(RegisterClientPayloadHandlersEvent event) { this.event = event; }
    public void sendPayload(CustomPacketPayload payload) { Minecraft.getInstance().getConnection().send(payload); }
    public boolean canSendPayload(CustomPacketPayload.Type<?> type) {
        var connection = Minecraft.getInstance().getConnection();
        return connection != null && connection.hasChannel(type.id());
    }
    public <T extends CustomPacketPayload> void receiver(CustomPacketPayload.Type<T> type, BiConsumer<T, Context> handler) {
        event.register(type, (payload, context) -> handler.accept(payload, new Context(Minecraft.getInstance())));
    }
}
