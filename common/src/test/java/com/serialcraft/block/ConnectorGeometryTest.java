package com.serialcraft.block;

import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ConnectorGeometryTest {
    @org.junit.jupiter.api.BeforeAll static void initialize() { com.serialcraft.test.TestBootstrap.initialize(); }
    @Test void eachVisibleTerminalHasAnUnambiguousHitRegion() {
        var block = (HardwareIOBlock) ModBlocks.IO_BLOCK;
        assertEquals(Direction.NORTH, block.getHitButton(new Vec3(8 / 16d, 6 / 16d, 1 / 16d)));
        assertEquals(Direction.SOUTH, block.getHitButton(new Vec3(8 / 16d, 6 / 16d, 15 / 16d)));
        assertEquals(Direction.EAST, block.getHitButton(new Vec3(15 / 16d, 6 / 16d, 8 / 16d)));
        assertEquals(Direction.WEST, block.getHitButton(new Vec3(1 / 16d, 6 / 16d, 8 / 16d)));
        assertEquals(Direction.DOWN, block.getHitButton(new Vec3(8 / 16d, 4 / 16d, 12 / 16d)));
        assertNull(block.getHitButton(new Vec3(0.3, 0.125, 0.3)));
    }
    @Test void raycastCanReachBottomTerminalFromAbove() {
        var block = (HardwareIOBlock) ModBlocks.IO_BLOCK;
        var shape = block.getShape(block.defaultBlockState(), null, net.minecraft.core.BlockPos.ZERO,
                net.minecraft.world.phys.shapes.CollisionContext.empty());
        var hit = shape.clip(new Vec3(0.5, 1, 0.75), new Vec3(0.5, 0, 0.75), net.minecraft.core.BlockPos.ZERO);
        assertNotNull(hit);
        assertEquals(Direction.DOWN, block.getHitButton(hit.getLocation()));
    }
}
