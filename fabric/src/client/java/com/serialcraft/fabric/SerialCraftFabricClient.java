package com.serialcraft.fabric;
import com.serialcraft.SerialCraftClient;
import com.serialcraft.client.SerialDebugHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.resources.Identifier;

public final class SerialCraftFabricClient implements ClientModInitializer {
    public void onInitializeClient() {
        HudElementRegistry.addLast(Identifier.parse("serialcraft:debug_hud"), SerialDebugHud::render);
        KeyMappingHelper.registerKeyMapping(SerialCraftClient.createDebugKey());
        ClientTickEvents.END_CLIENT_TICK.register(SerialCraftClient::tick);
        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> SerialCraftClient.stopping());
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> SerialCraftClient.joined(client));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> SerialCraftClient.disconnected());
        UseBlockCallback.EVENT.register(SerialCraftClient::interact);
        SerialCraftClient.registerNetworkHandlers();
    }
}
