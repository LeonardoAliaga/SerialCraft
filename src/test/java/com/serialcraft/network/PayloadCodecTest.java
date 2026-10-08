package com.serialcraft.network;

import com.serialcraft.board.*;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.RegistryFriendlyByteBuf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PayloadCodecTest {
    @Test void draftAndCorrelatedResponseRoundTrip() {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            var config = new ConfigPayload(new BlockPos(1, 2, 3), IoMode.INPUT, "sensor", SignalType.ANALOG,
                    true, "pot", LogicMode.AND, 2, "minecraft:overworld", 72);
            ConfigPayload.CODEC.encode(buf, config);
            assertEquals(config, ConfigPayload.CODEC.decode(buf));
            buf.clear();
            var result = new ConfigResultPayload(config.pos(), true, "gui.serialcraft.editor.saved", 72);
            ConfigResultPayload.CODEC.encode(buf, result);
            assertEquals(result, ConfigResultPayload.CODEC.decode(buf));
        } finally { buf.release(); }
    }
    @Test void invalidNetworkEnumsAreRejectedWithoutAffectingNbtMigration() {
        assertThrows(io.netty.handler.codec.DecoderException.class, () -> IoMode.byId(2));
        assertThrows(io.netty.handler.codec.DecoderException.class, () -> SignalType.byId(-1));
        assertThrows(io.netty.handler.codec.DecoderException.class, () -> LogicMode.byId(3));
    }
    @Test void diagnosticsRoundTripHasNoImpliedAcknowledgement() {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            var board = new BoardInfo(BlockPos.ZERO, "pot", "sensor", IoMode.INPUT, SignalType.ANALOG, LogicMode.OR,
                    true, new IoSnapshot(512, 128, -1, 0, 8, 8, true, 20));
            BoardInfo.CODEC.encode(buf, board);
            assertEquals(board, BoardInfo.CODEC.decode(buf));
            buf.clear();
            var list = new BoardListResponsePayload(java.util.List.of(board), "minecraft:overworld");
            BoardListResponsePayload.CODEC.encode(buf, list);
            assertEquals(list, BoardListResponsePayload.CODEC.decode(buf));
        } finally { buf.release(); }
    }
    @Test void explicitSafetyStopSurvivesNetworkEncoding() {
        var buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), RegistryAccess.EMPTY);
        try {
            var stop = new SerialOutputPayload("led:0", true);
            SerialOutputPayload.CODEC.encode(buf, stop);
            assertEquals(stop, SerialOutputPayload.CODEC.decode(buf));
            assertFalse(new SerialOutputPayload("led:0").safetyStop());
        } finally { buf.release(); }
    }
}
