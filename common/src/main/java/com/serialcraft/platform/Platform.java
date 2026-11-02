package com.serialcraft.platform;

import java.nio.file.Path;
import java.util.ServiceLoader;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** Loader services only. This contract must never reference client or hardware classes. */
public interface Platform {
    static Platform get() { return Holder.INSTANCE; }
    final class Holder {
        private static final Platform INSTANCE = ServiceLoader.load(Platform.class, Platform.class.getClassLoader()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Missing SerialCraft platform adapter"));
        private Holder() {}
    }
    Path configDir();
    String modVersion();
    boolean hasPermission(ServerPlayer player, String node, int fallbackLevel);
    <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory, Block... blocks);
}
