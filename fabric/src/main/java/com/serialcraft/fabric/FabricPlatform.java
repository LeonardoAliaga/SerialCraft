package com.serialcraft.fabric;
import com.serialcraft.platform.Platform;
import java.nio.file.Path;
import java.util.function.BiFunction;
import me.lucko.fabric.api.permissions.v0.Permissions;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public final class FabricPlatform implements Platform {
    public Path configDir() { return FabricLoader.getInstance().getConfigDir(); }
    public String modVersion() { return FabricLoader.getInstance().getModContainer("serialcraft").orElseThrow().getMetadata().getVersion().getFriendlyString(); }
    public boolean hasPermission(ServerPlayer player, String node, int level) { return Permissions.check(player, node, level); }
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory, Block... blocks) {
        return FabricBlockEntityTypeBuilder.create(factory::apply, blocks).build();
    }
}
