package com.serialcraft.block.entity;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.ModBlocks;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.function.BiFunction;

public final class ModBlockEntities {

    private ModBlockEntities() {}

    public static BlockEntityType<ArduinoIOBlockEntity>  IO_BLOCK_ENTITY;
    public static BlockEntityType<ConnectorBlockEntity>  CONNECTOR_BLOCK_ENTITY;

    /** Helper para no repetir el bloque de cinco lineas por cada tipo. */
    private static <T extends BlockEntity> BlockEntityType<T> register(
            String name,
            BiFunction<net.minecraft.core.BlockPos,
                       net.minecraft.world.level.block.state.BlockState, T> factory,
            Block... blocks) {

        Identifier id = Identifier.fromNamespaceAndPath(SerialCraft.MOD_ID, name);
        ResourceKey<BlockEntityType<?>> key = ResourceKey.create(Registries.BLOCK_ENTITY_TYPE, id);

        return Registry.register(
                BuiltInRegistries.BLOCK_ENTITY_TYPE,
                key,
                FabricBlockEntityTypeBuilder.create(factory::apply, blocks).build()
        );
    }

    public static void initialize() {
        IO_BLOCK_ENTITY        = register("io_block",        ArduinoIOBlockEntity::new, ModBlocks.IO_BLOCK);
        CONNECTOR_BLOCK_ENTITY = register("connector_block", ConnectorBlockEntity::new, ModBlocks.CONNECTOR_BLOCK);
    }
}
