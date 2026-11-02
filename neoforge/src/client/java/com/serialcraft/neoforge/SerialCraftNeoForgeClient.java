package com.serialcraft.neoforge;

import com.serialcraft.SerialCraft;
import com.serialcraft.SerialCraftClient;
import com.serialcraft.client.SerialDebugHud;
import com.serialcraft.platform.ClientNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.lifecycle.ClientStoppingEvent;
import net.neoforged.neoforge.client.network.event.RegisterClientPayloadHandlersEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

@Mod(value = SerialCraft.MOD_ID, dist = Dist.CLIENT)
public final class SerialCraftNeoForgeClient {
    public SerialCraftNeoForgeClient(IEventBus modBus) {
        modBus.addListener((net.neoforged.neoforge.data.event.GatherDataEvent.Client event) ->
                event.createProvider(com.serialcraft.datagen.SerialCraftRecipes.Runner::new));
        modBus.addListener((RegisterKeyMappingsEvent event) -> event.register(SerialCraftClient.createDebugKey()));
        modBus.addListener((RegisterGuiLayersEvent event) -> event.registerAboveAll(Identifier.parse("serialcraft:debug_hud"), SerialDebugHud::render));
        modBus.addListener((RegisterClientPayloadHandlersEvent event) -> {
            ((NeoForgeClientNetworking) ClientNetworking.get()).begin(event);
            SerialCraftClient.registerNetworkHandlers();
        });
        NeoForge.EVENT_BUS.addListener((ClientStoppingEvent event) -> SerialCraftClient.stopping());
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event) -> SerialCraftClient.tick(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingIn event) -> SerialCraftClient.joined(Minecraft.getInstance()));
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> SerialCraftClient.disconnected());
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (!event.getLevel().isClientSide()) return;
            var result = SerialCraftClient.interact(event.getEntity(), event.getLevel(), event.getHand(), event.getHitVec());
            if (result != InteractionResult.PASS) { event.setCancellationResult(result); event.setCanceled(true); }
        });
    }
}
