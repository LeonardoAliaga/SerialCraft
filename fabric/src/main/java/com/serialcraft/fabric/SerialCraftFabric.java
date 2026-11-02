package com.serialcraft.fabric;
import com.serialcraft.SerialCraft;
import com.serialcraft.board.BoardRegistry;
import com.serialcraft.item.ModItems;
import com.serialcraft.network.ModNetworking;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.creativetab.v1.CreativeModeTabEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.item.CreativeModeTabs;

public final class SerialCraftFabric implements ModInitializer {
    public void onInitialize() {
        new SerialCraft().initialize();
        CreativeModeTabEvents.modifyOutputEvent(CreativeModeTabs.REDSTONE_BLOCKS).register(entries -> {
            entries.accept(ModItems.CONNECTOR_BLOCK_ITEM); entries.accept(ModItems.IO_BLOCK_ITEM);
        });
        ServerLifecycleEvents.SERVER_STARTING.register(server -> BoardRegistry.serverStarting());
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> BoardRegistry.serverStopping());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> BoardRegistry.serverStopped());
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register(BoardRegistry::loaded);
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register(BoardRegistry::unloaded);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ModNetworking.disconnected(handler.getPlayer()));
    }
}
