package com.serialcraft.neoforge.mixin;

import com.serialcraft.block.entity.HardwareIOBlockEntity;
import com.serialcraft.board.BoardRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;

/** Bridge NeoForge's block entity lifecycle to the same index used on Fabric. */
@Mixin(HardwareIOBlockEntity.class)
public abstract class HardwareIOLifecycleMixin extends BlockEntity {
    protected HardwareIOLifecycleMixin(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server) BoardRegistry.loaded(this, server);
    }
    @Override public void setRemoved() {
        if (level instanceof ServerLevel server) BoardRegistry.unloaded(this, server);
        super.setRemoved();
    }
}
