package com.serialcraft.item;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.ModBlocks;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;

import java.util.function.Function;

public final class ModItems {

    private ModItems() {}

    // NOTA sobre orden de inicializacion: estos campos estaticos referencian
    // ModBlocks, asi que cargar esta clase carga ModBlocks primero. Funciona,
    // pero es una dependencia implicita: SerialCraft.onInitialize llama a
    // ModBlocks.initialize() antes que a ModItems.initialize() para dejarla
    // explicita y que no dependa del orden de carga de la JVM.

    private static Item register(String name,
                                 Function<Item.Properties, Item> factory,
                                 Item.Properties settings) {
        ResourceKey<Item> key = ResourceKey.create(
                Registries.ITEM,
                Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, name)
        );
        return Registry.register(BuiltInRegistries.ITEM, key, factory.apply(settings.setId(key)));
    }

    public static final Item CONNECTOR_BLOCK_ITEM = register(
            "connector_block",
            props -> new BlockItem(ModBlocks.CONNECTOR_BLOCK, props),
            new Item.Properties()
    );

    public static final Item IO_BLOCK_ITEM = register(
            "io_block",
            props -> new BlockItem(ModBlocks.IO_BLOCK, props),
            new Item.Properties()
    );

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.REDSTONE_BLOCKS)
                .register(entries -> {
                    entries.accept(CONNECTOR_BLOCK_ITEM);
                    entries.accept(IO_BLOCK_ITEM);
                });
    }
}
