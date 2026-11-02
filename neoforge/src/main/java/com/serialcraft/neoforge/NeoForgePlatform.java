package com.serialcraft.neoforge;

import com.serialcraft.platform.Platform;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

public final class NeoForgePlatform implements Platform {
    public static final PermissionNode<Boolean> BYPASS = new PermissionNode<>("serialcraft", "admin.bypass",
            PermissionTypes.BOOLEAN, (player, uuid, context) -> player != null &&
            player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER));
    public Path configDir() { return FMLPaths.CONFIGDIR.get(); }
    public String modVersion() { return ModList.get().getModContainerById("serialcraft").orElseThrow().getModInfo().getVersion().toString(); }
    public boolean hasPermission(ServerPlayer player, String node, int fallbackLevel) {
        if (!node.equals(BYPASS.getNodeName()) || fallbackLevel != 2) throw new IllegalArgumentException("Unsupported permission: " + node);
        return PermissionAPI.getPermission(player, BYPASS);
    }
    public <T extends BlockEntity> BlockEntityType<T> blockEntityType(BiFunction<BlockPos, BlockState, T> factory, Block... blocks) {
        return new BlockEntityType<>(factory::apply, Set.of(blocks));
    }
}
