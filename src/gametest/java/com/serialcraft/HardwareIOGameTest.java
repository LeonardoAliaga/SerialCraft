package com.serialcraft;

import com.serialcraft.block.HardwareIOBlock;
import com.serialcraft.block.IOSide;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.block.entity.HardwareIOBlockEntity;
import com.serialcraft.board.*;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedstoneLampBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import com.serialcraft.network.guard.NetGuard;

import java.util.ArrayList;
import java.util.UUID;

/** Actual world behavior; opt in with -PioGameTests. Requires a test server environment. */
public class HardwareIOGameTest {
    private static final BlockPos POS = new BlockPos(2, 2, 2);
    private HardwareIOBlockEntity place(GameTestHelper test, IoMode mode) {
        test.setBlock(POS, ModBlocks.IO_BLOCK);
        var io = (HardwareIOBlockEntity) test.getLevel().getBlockEntity(test.absolutePos(POS));
        io.claim(test.makeMockServerPlayerInLevel());
        HardwareSessions.update(io.getOwnerUUID(), true);
        io.applyConfig(mode, "sensor", SignalType.ANALOG, true, "test", LogicMode.OR);
        return io;
    }

    @GameTest public void receiveAllFiveFacesAndDisconnect(GameTestHelper test) {
        var io = place(test, IoMode.INPUT);
        int sides = 0;
        for (int i = 0; i < 5; i++) sides = IOSide.with(sides, i, IOSide.OUTPUT);
        io.setSides(sides);
        io.acceptSerialInput("sensor:128"); io.tickServer();
        for (Direction side : Direction.values()) {
            int expected = side == Direction.UP ? 0 : 8;
            test.assertValueEqual(test.getLevel().getSignal(io.getBlockPos(), side.getOpposite()), expected, "weak output face");
            test.assertValueEqual(test.getLevel().getDirectSignal(io.getBlockPos(), side.getOpposite()), expected, "strong output face");
        }
        HardwareSessions.update(io.getOwnerUUID(), false);
        test.assertValueEqual(io.getRedstoneSignal(), 0, "disconnect clears redstone");
        test.succeed();
    }

    @GameTest public void gateReopensWithoutAnotherHardwareMessage(GameTestHelper test) {
        var io = place(test, IoMode.INPUT);
        io.setSides(IOSide.with(IOSide.with(0, 0, IOSide.INPUT), 1, IOSide.OUTPUT));
        io.acceptSerialInput("sensor:255"); io.tickServer();
        test.assertValueEqual(io.getRedstoneSignal(), 0, "closed gate");
        test.runAtTickTime(2, () -> test.setBlock(POS.north(), Blocks.REDSTONE_BLOCK));
        test.runAtTickTime(4, () -> {
            test.assertValueEqual(io.getRedstoneSignal(), 15, "stored sample opens gate");
            test.setBlock(POS.north(), Blocks.AIR);
        });
        test.runAtTickTime(6, () -> { test.assertValueEqual(io.getRedstoneSignal(), 0, "neighbor removal closes gate"); test.succeed(); });
    }

    @GameTest public void transmittingDoesNotPowerOutputConnectors(GameTestHelper test) {
        var io = place(test, IoMode.OUTPUT);
        io.setSides(IOSide.with(IOSide.with(0, 0, IOSide.INPUT), 1, IOSide.OUTPUT));
        test.setBlock(POS.north(), Blocks.REDSTONE_BLOCK);
        test.runAtTickTime(2, () -> {
            test.assertValueEqual(io.snapshot().processed(), 15, "input detected");
            test.assertValueEqual(test.getLevel().getSignal(io.getBlockPos(), Direction.NORTH), 0, "transmit output is isolated");
            test.succeed();
        });
    }

    @GameTest public void inputConnectsToSideOfThroughDustLine(GameTestHelper test) {
        var io = place(test, IoMode.OUTPUT);
        io.setSides(IOSide.with(0, 3, IOSide.INPUT));
        BlockPos dust = POS.west();
        for (BlockPos p : new BlockPos[]{dust, dust.north(), dust.south()}) {
            test.setBlock(p.below(), Blocks.STONE);
            test.setBlock(p, Blocks.REDSTONE_WIRE);
        }
        test.setBlock(dust.north(2), Blocks.REDSTONE_BLOCK);
        test.runAtTickTime(4, () -> {
            test.assertTrue(io.snapshot().processed() > 0, "input must receive side branch of dust");
            test.assertValueEqual(io.getRedstoneSignal(), 0, "reading dust must not emit");
            test.succeed();
        });
    }

    @GameTest public void lampBehindStronglyPoweredBlockClearsOnDisable(GameTestHelper test) {
        var io = place(test, IoMode.INPUT);
        test.setBlock(POS.east(), Blocks.STONE);
        test.setBlock(POS.east(2), Blocks.REDSTONE_LAMP);
        io.setSides(IOSide.with(0, 2, IOSide.OUTPUT));
        io.acceptSerialInput("sensor:255"); io.tickServer();
        test.runAtTickTime(2, () -> {
            test.assertBlockProperty(POS.east(2), RedstoneLampBlock.LIT, true);
            io.setEnabled(false);
        });
        test.runAtTickTime(8, () -> {
            test.assertBlockProperty(POS.east(2), RedstoneLampBlock.LIT, false);
            test.succeed();
        });
    }

    @GameTest public void ownershipDistanceAndUnloadedChunksAreGuarded(GameTestHelper test) {
        var io = place(test, IoMode.INPUT);
        var owner = test.makeMockServerPlayerInLevel();
        owner.setUUID(io.getOwnerUUID());
        var at = io.getBlockPos();
        owner.setPos(at.getX() + 0.5, at.getY() + 0.5, at.getZ() + 0.5);
        var stranger = test.makeMockServerPlayerInLevel();
        stranger.setUUID(UUID.randomUUID());
        test.assertTrue(NetGuard.canOperate(owner, io.getOwnerUUID()), "owner may configure");
        test.assertTrue(!NetGuard.canOperate(stranger, io.getOwnerUUID()), "stranger cannot configure");
        test.assertTrue(NetGuard.resolve(owner, io.getBlockPos(), HardwareIOBlockEntity.class) == io, "nearby entity resolves");
        owner.setPos(at.getX() + 100.5, at.getY() + 0.5, at.getZ() + 0.5);
        test.assertTrue(NetGuard.resolve(owner, io.getBlockPos(), HardwareIOBlockEntity.class) == null, "distance rejected");
        BlockPos far = new BlockPos(1_000_000, 64, 1_000_000);
        owner.setPos(far.getX() + 0.5, far.getY() + 0.5, far.getZ() + 0.5);
        test.assertTrue(!test.getLevel().isLoaded(far), "fixture chunk is unloaded");
        test.assertTrue(NetGuard.resolve(owner, far, HardwareIOBlockEntity.class) == null, "unloaded entity rejected");
        test.assertTrue(!test.getLevel().isLoaded(far), "request did not load the chunk");
        test.succeed();
    }

    @GameTest(maxTicks = 50) public void leverAndButtonTransitionsReachInput(GameTestHelper test) {
        var io = place(test, IoMode.OUTPUT);
        io.setSides(IOSide.with(0, 0, IOSide.INPUT));
        var player = test.makeMockServerPlayerInLevel();
        BlockPos control = POS.north();
        test.setBlock(control.below(), Blocks.STONE);
        test.setBlock(control, Blocks.LEVER.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
        test.useBlock(control, player);
        test.runAtTickTime(4, () -> {
            test.assertValueEqual(io.snapshot().processed(), 15, "lever on");
            test.useBlock(control, player);
        });
        test.runAtTickTime(8, () -> {
            test.assertValueEqual(io.snapshot().processed(), 0, "lever off");
            test.setBlock(control, Blocks.STONE_BUTTON.defaultBlockState().setValue(BlockStateProperties.ATTACH_FACE, AttachFace.FLOOR));
            test.useBlock(control, player);
        });
        test.runAtTickTime(12, () -> test.assertValueEqual(io.snapshot().processed(), 15, "button on"));
        test.runAtTickTime(34, () -> {
            test.assertValueEqual(io.snapshot().processed(), 0, "button released itself");
            test.succeed();
        });
    }

    @GameTest public void repeaterNeighborUpdatesReachInput(GameTestHelper test) {
        var io = place(test, IoMode.OUTPUT);
        io.setSides(IOSide.with(0, 0, IOSide.INPUT));
        test.setBlock(POS.north().below(), Blocks.STONE);
        test.setBlock(POS.north(), Blocks.REPEATER.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        test.setBlock(POS.north(2), Blocks.REDSTONE_BLOCK);
        test.runAtTickTime(6, () -> {
            test.assertValueEqual(io.snapshot().processed(), 15, "repeater output");
            test.setBlock(POS.north(2), Blocks.AIR);
        });
        test.runAtTickTime(12, () -> {
            test.assertValueEqual(io.snapshot().processed(), 0, "repeater falling edge");
            test.succeed();
        });
    }

    @GameTest(maxTicks = 30) public void comparatorReadsZeroOneSevenFifteen(GameTestHelper test) {
        var io = place(test, IoMode.OUTPUT);
        io.setSides(IOSide.with(0, 0, IOSide.INPUT));
        test.setBlock(POS.north().below(), Blocks.STONE);
        test.setBlock(POS.north(), Blocks.COMPARATOR.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH));
        test.setBlock(POS.north(2), Blocks.CHEST);
        var chest = (ChestBlockEntity) test.getLevel().getBlockEntity(test.absolutePos(POS.north(2)));
        test.runAtTickTime(4, () -> {
            test.assertValueEqual(io.snapshot().processed(), 0, "empty chest");
            fill(chest, 1);
        });
        test.runAtTickTime(8, () -> {
            test.assertValueEqual(io.snapshot().processed(), 1, "one full slot");
            fill(chest, 13);
        });
        test.runAtTickTime(12, () -> {
            test.assertValueEqual(io.snapshot().processed(), 7, "13 full slots");
            fill(chest, 27);
        });
        test.runAtTickTime(16, () -> {
            test.assertValueEqual(io.snapshot().processed(), 15, "full chest");
            fill(chest, 0);
        });
        test.runAtTickTime(20, () -> {
            test.assertValueEqual(io.snapshot().processed(), 0, "cleared chest");
            test.succeed();
        });
    }

    private static void fill(ChestBlockEntity chest, int fullSlots) {
        for (int i = 0; i < chest.getContainerSize(); i++) chest.setItem(i, i < fullSlots ? new ItemStack(Items.STONE, 64) : ItemStack.EMPTY);
        chest.setChanged();
    }

    /** Load smoke test, not a TPS benchmark: 64 entities settle, react and leave the index. */
    @GameTest(maxTicks = 40) public void sixtyFourModulesSettleAndUnload(GameTestHelper test) {
        var owner = test.makeMockServerPlayerInLevel();
        var modules = new ArrayList<HardwareIOBlockEntity>();
        HardwareSessions.update(owner.getUUID(), true);
        for (int x = 0; x < 8; x++) for (int z = 0; z < 8; z++) {
            BlockPos pos = new BlockPos(x, 2, z);
            test.setBlock(pos, ModBlocks.IO_BLOCK);
            var io = (HardwareIOBlockEntity) test.getLevel().getBlockEntity(test.absolutePos(pos));
            io.claim(owner);
            io.applyConfig(IoMode.INPUT, "shared", SignalType.ANALOG, true, "load", LogicMode.OR);
            io.setSides(IOSide.with(0, 4, IOSide.OUTPUT));
            io.acceptSerialInput("shared:128");
            modules.add(io);
        }
        test.runAtTickTime(8, () -> {
            test.assertValueEqual(BoardRegistry.countFor(test.getLevel().dimension(), owner.getUUID()), 64, "64 indexed modules");
            for (var io : modules) {
                test.assertValueEqual(io.getRedstoneSignal(), 8, "shared RX fanout");
                io.acceptSerialInput("shared:0");
            }
        });
        test.runAtTickTime(20, () -> {
            for (var io : modules) {
                test.assertValueEqual(io.getRedstoneSignal(), 0, "shared falling edge");
                test.getLevel().setBlockAndUpdate(io.getBlockPos(), Blocks.AIR.defaultBlockState());
            }
        });
        test.runAtTickTime(24, () -> {
            test.assertValueEqual(BoardRegistry.countFor(test.getLevel().dimension(), owner.getUUID()), 0, "unload removes registry references");
            test.succeed();
        });
    }
}
