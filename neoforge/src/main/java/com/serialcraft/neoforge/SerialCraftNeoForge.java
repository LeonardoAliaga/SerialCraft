package com.serialcraft.neoforge;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.block.entity.ModBlockEntities;
import com.serialcraft.board.BoardRegistry;
import com.serialcraft.item.ModItems;
import com.serialcraft.network.ModNetworking;
import com.serialcraft.platform.PayloadRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.RegisterEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

@Mod(SerialCraft.MOD_ID)
public final class SerialCraftNeoForge {
    public SerialCraftNeoForge(IEventBus modBus) {
        modBus.addListener(this::register);
        modBus.addListener(this::network);
        modBus.addListener(this::creative);
        NeoForge.EVENT_BUS.addListener((ServerStartingEvent event) -> BoardRegistry.serverStarting());
        NeoForge.EVENT_BUS.addListener((ServerStoppingEvent event) -> BoardRegistry.serverStopping());
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> BoardRegistry.serverStopped());
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) ModNetworking.disconnected(player);
        });
        NeoForge.EVENT_BUS.addListener((PermissionGatherEvent.Nodes event) -> event.addNodes(NeoForgePlatform.BYPASS));
    }
    private void register(RegisterEvent event) {
        // Construct intrusive holders only while their registry is open.
        if (event.getRegistryKey().equals(Registries.BLOCK)) ModBlocks.initialize();
        if (event.getRegistryKey().equals(Registries.ITEM)) ModItems.initialize();
        if (event.getRegistryKey().equals(Registries.BLOCK_ENTITY_TYPE)) ModBlockEntities.initialize();
    }
    private void network(RegisterPayloadHandlersEvent event) {
        ((NeoForgeNetworking) PayloadRegistry.get()).begin(event.registrar("1").optional());
        ModNetworking.registerPayloads();
        ModNetworking.registerServerHandlers();
    }
    private void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.REDSTONE_BLOCKS)) {
            event.accept(ModItems.CONNECTOR_BLOCK_ITEM); event.accept(ModItems.IO_BLOCK_ITEM);
        }
    }
}
