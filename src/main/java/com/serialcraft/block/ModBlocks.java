package com.serialcraft.block;

import com.serialcraft.SerialCraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

import java.util.function.Function;

public final class ModBlocks {

    private ModBlocks() {}

    private static Block register(String name,
                                  Function<BlockBehaviour.Properties, Block> factory,
                                  BlockBehaviour.Properties settings) {
        ResourceKey<Block> key = ResourceKey.create(
                Registries.BLOCK,
                Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, name)
        );
        return Registry.register(BuiltInRegistries.BLOCK, key, factory.apply(settings.setId(key)));
    }

    public static final Block CONNECTOR_BLOCK = register(
            "connector_block",
            props -> new ConnectorBlock(props
                    .mapColor(MapColor.STONE)
                    .strength(2.0f)
                    .noOcclusion()),
            BlockBehaviour.Properties.of()
    );

    public static final Block IO_BLOCK = register(
            "io_block",
            props -> new ArduinoIOBlock(props
                    .mapColor(MapColor.METAL)
                    .strength(3.0f)
                    .noOcclusion()),
            BlockBehaviour.Properties.of()
    );

    /** Fuerza la carga de la clase, ejecutando los registros estaticos. */
    public static void initialize() {}
}
