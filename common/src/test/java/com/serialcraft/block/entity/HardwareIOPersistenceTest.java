package com.serialcraft.block.entity;

import com.serialcraft.SerialCraft;
import com.serialcraft.block.HardwareIOBlock;
import com.serialcraft.block.IOSide;
import com.serialcraft.block.ModBlocks;
import com.serialcraft.board.*;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.level.storage.TagValueInput;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class HardwareIOPersistenceTest {
    @BeforeAll static void initialize() {
        com.serialcraft.test.TestBootstrap.initialize();
    }

    @Test void registrationsAndDefaultEnabledStateAreCompatible() {
        assertEquals(Identifier.parse("serialcraft:io_block"), BuiltInRegistries.BLOCK.getKey(ModBlocks.IO_BLOCK));
        assertEquals(Identifier.parse("serialcraft:io_block"), BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(ModBlockEntities.IO_BLOCK_ENTITY));
        var io = new HardwareIOBlockEntity(BlockPos.ZERO, ModBlocks.IO_BLOCK.defaultBlockState());
        assertEquals(io.isEnabled(), io.getBlockState().getValue(HardwareIOBlock.ENABLED));
        assertEquals(1458, ModBlocks.IO_BLOCK.getStateDefinition().getPossibleStates().size());
    }

    @Test void namedAndNumericLegacyNbtLoadWithoutSessionPower() {
        var registry = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for (boolean numeric : new boolean[]{false, true}) {
            CompoundTag old = new CompoundTag();
            if (numeric) { old.putInt("ioMode", 1); old.putInt("signalType", 1); old.putInt("logicMode", 2); }
            else { old.putString("ioMode", "input"); old.putString("signalType", "analog"); old.putString("logicMode", "xor"); }
            old.putString("boardId", "sensor"); old.putString("targetData", "pot_val");
            old.putBoolean("enabled", false); old.putInt("redstoneOut", 15);
            old.putString("ownerUUID", "12345678-1234-1234-1234-123456789abc");
            var io = new HardwareIOBlockEntity(BlockPos.ZERO, ModBlocks.IO_BLOCK.defaultBlockState());
            io.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registry, old));
            assertEquals(IoMode.INPUT, io.getIoMode()); assertEquals(SignalType.ANALOG, io.getSignalType());
            assertEquals(LogicMode.XOR, io.getLogicMode()); assertFalse(io.isEnabled());
            assertEquals("pot_val", io.getTargetData()); assertNotNull(io.getOwnerUUID());
            assertEquals(0, io.getRedstoneSignal()); assertEquals(-1, io.snapshot().received());
            CompoundTag saved = io.saveCustomOnly(registry);
            assertEquals("input", saved.getString("ioMode").orElseThrow());
            assertFalse(saved.contains("redstoneOut")); assertFalse(saved.contains("connected"));
            var copy = new HardwareIOBlockEntity(BlockPos.ZERO, io.getBlockState());
            copy.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registry, saved));
            assertEquals(io.getOwnerUUID(), copy.getOwnerUUID()); assertEquals(io.getBoardId(), copy.getBoardId());
            assertEquals(io.getTargetData(), copy.getTargetData()); assertEquals(io.getIoMode(), copy.getIoMode());
        }
    }

    @Test void unknownOldVisualPropertiesDoNotRemoveBlockOrConnectors() {
        CompoundTag state = new CompoundTag(); state.putString("Name", "serialcraft:io_block");
        CompoundTag props = new CompoundTag();
        props.putString("north", "output"); props.putString("down", "input");
        props.putString("blinking", "true"); props.putString("powered", "true"); props.putString("up", "output");
        props.putString("mode", "2"); state.put("Properties", props);
        var decoded = NbtUtils.readBlockState(BuiltInRegistries.BLOCK, state);
        assertTrue(decoded.is(ModBlocks.IO_BLOCK));
        assertEquals(IOSide.OUTPUT, decoded.getValue(HardwareIOBlock.NORTH));
        assertEquals(IOSide.INPUT, decoded.getValue(HardwareIOBlock.DOWN));
        assertEquals(2, decoded.getValue(HardwareIOBlock.MODE));
        // Chunk palettes use this codec, rather than the NbtUtils helper.
        var palette = net.minecraft.world.level.block.state.BlockState.CODEC.parse(net.minecraft.nbt.NbtOps.INSTANCE, state).getOrThrow();
        assertTrue(palette.is(ModBlocks.IO_BLOCK));
        assertEquals(decoded, palette);
    }

    @Test void all243ConnectorCombinationsRoundTripAndRejectUnknownBits() {
        for (int packed = 0; packed < 1024; packed++) {
            if (IOSide.isValidPacked(packed)) assertEquals(packed, IOSide.pack(IOSide.apply(ModBlocks.IO_BLOCK.defaultBlockState(), packed)));
        }
        assertFalse(IOSide.isValidPacked(-1)); assertFalse(IOSide.isValidPacked(1024));
    }
    @Test void legacyInvalidChannelCannotSilentlyDriveDefaultChannel() {
        var registry = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for (String channel : new String[]{"mc_health", "", "bad:key"}) {
            var tag = new CompoundTag(); tag.putString("targetData", channel); tag.putBoolean("enabled", true);
            var io = new HardwareIOBlockEntity(BlockPos.ZERO, ModBlocks.IO_BLOCK.defaultBlockState());
            io.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registry, tag));
            assertFalse(io.isEnabled()); assertEquals(channel, io.getTargetData());
            io.setEnabled(true);
            assertFalse(io.isEnabled(), "a power toggle cannot bypass channel validation");
        }
    }
    @Test void unknownLegacyModeCannotSilentlyStartTransmitting() {
        var registry = RegistryAccess.fromRegistryOfRegistries(BuiltInRegistries.REGISTRY);
        for (String field : new String[]{"ioMode", "signalType", "logicMode"}) {
            var tag = new CompoundTag(); tag.putString(field, "unknown"); tag.putBoolean("enabled", true);
            var io = new HardwareIOBlockEntity(BlockPos.ZERO, ModBlocks.IO_BLOCK.defaultBlockState());
            io.loadCustomOnly(TagValueInput.create(ProblemReporter.DISCARDING, registry, tag));
            assertFalse(io.isEnabled()); assertEquals(0, io.getRedstoneSignal());
        }
    }
}
