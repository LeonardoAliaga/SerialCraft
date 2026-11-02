package com.serialcraft.neoforge;

import com.serialcraft.platform.PayloadRegistry;
import com.serialcraft.platform.ServerNetworking;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class NeoForgeNetworking implements PayloadRegistry {
    private PayloadRegistrar registrar;
    private final Map<CustomPacketPayload.Type<?>, StreamCodec<?, ?>> codecs = new HashMap<>();
    public void begin(PayloadRegistrar registrar) { this.registrar = registrar; }
    public <T extends CustomPacketPayload> void register(CustomPacketPayload.Type<T> type, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, boolean serverbound) {
        if (serverbound) codecs.put(type, codec);
        else registrar.playToClient(type, codec);
    }
    @SuppressWarnings("unchecked")
    public <T extends CustomPacketPayload> void receiver(CustomPacketPayload.Type<T> type, BiConsumer<T, ServerNetworking.Context> handler) {
        var codec = (StreamCodec<? super RegistryFriendlyByteBuf, T>) codecs.remove(type);
        if (codec == null) throw new IllegalStateException("No codec registered for " + type);
        registrar.playToServer(type, codec, (payload, context) -> {
            var player = (ServerPlayer) context.player();
            handler.accept(payload, new ServerNetworking.Context(player, player.level().getServer()));
        });
    }
    public void send(ServerPlayer player, CustomPacketPayload payload) { PacketDistributor.sendToPlayer(player, payload); }
    public boolean canSend(ServerPlayer player, CustomPacketPayload.Type<?> type) { return player.connection.hasChannel(type.id()); }
}
