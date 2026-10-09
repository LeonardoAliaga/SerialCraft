package com.serialcraft.client.ui;

import com.serialcraft.block.IOSide;
import com.serialcraft.board.IoMode;
import com.serialcraft.board.LogicMode;
import com.serialcraft.board.SignalType;
import com.serialcraft.client.ui.io.IoEditorDraft;
import com.serialcraft.network.BoardInfo;
import com.serialcraft.network.ConfigResultPayload;
import com.serialcraft.network.IoSnapshot;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Only protect configuration data and the server confirmation handshake; visual UI is reviewed in game. */
class IoEditorDraftTest {
    @BeforeAll static void initialize() { com.serialcraft.test.TestBootstrap.initialize(); }

    private static IoEditorDraft draft() {
        int sides = IOSide.with(0, 4, IOSide.OUTPUT);
        return new IoEditorDraft(new BoardInfo(new BlockPos(1, 2, 3), "module", "led_verde",
                IoMode.OUTPUT, SignalType.DIGITAL, LogicMode.OR, true,
                new IoSnapshot(sides, -1, -1, 0, 0, 0, false, -1)), "minecraft:overworld");
    }

    @Test void editingSectionsDoesNotDiscardValuesOrConnectorRoles() {
        var d = draft();
        int sides = d.values().sides();
        d.name("renamed");
        d.channel("pot_val");
        d.mode(IoMode.INPUT);
        d.signal(SignalType.ANALOG);
        d.logic(LogicMode.XOR);
        var edited = d.values();
        d.section(IoEditorDraft.Section.DIAGNOSTICS);
        d.section(IoEditorDraft.Section.CONFIGURATION);
        assertEquals(edited, d.values());
        assertEquals(sides, d.values().sides());
        assertTrue(d.dirty());
    }

    @Test void saveFreezesPayloadAndRequiresMatchingServerAcknowledgement() {
        var d = draft();
        d.name("  renamed  ");
        d.channel(" pot_val ");
        assertTrue(d.beginSave(42, 100));
        assertFalse(d.beginSave(43, 200));
        d.channel("another");
        assertEquals("pot_val", d.payload().targetData());
        assertEquals("renamed", d.payload().boardId());
        assertEquals(d.values().sides(), d.payload().sides());
        assertFalse(d.accept(new ConfigResultPayload(d.target().pos(), true, "", 41)));
        assertTrue(d.dirty());
        assertTrue(d.accept(new ConfigResultPayload(d.target().pos(), true, "", 42)));
        assertFalse(d.dirty());
        assertEquals("renamed", d.values().name());
        assertEquals(IoEditorDraft.SaveState.ACCEPTED, d.saveState());
    }

    @Test void rejectionAndTimeoutKeepUnsavedChanges() {
        var rejected = draft();
        rejected.channel("pot_val");
        rejected.beginSave(1, 100);
        assertTrue(rejected.accept(new ConfigResultPayload(rejected.target().pos(), false, "not_owner", 1)));
        assertTrue(rejected.dirty());
        assertEquals("pot_val", rejected.values().channel());

        var timedOut = draft();
        timedOut.enabled(false);
        timedOut.beginSave(2, 100);
        assertTrue(timedOut.tick(101 + IoEditorDraft.SAVE_TIMEOUT_NANOS));
        assertTrue(timedOut.dirty());
        assertFalse(timedOut.values().enabled());
        assertFalse(timedOut.accept(new ConfigResultPayload(timedOut.target().pos(), true, "", 2)));
    }

    @Test void channelValidationKeepsProtocolRestrictions() {
        var d = draft();
        for (String invalid : new String[]{"", "mc_health", "bad:key", "a".repeat(33)}) {
            d.channel(invalid);
            assertFalse(d.valid());
        }
        d.channel("pot_val");
        assertTrue(d.valid());
        d.name("n".repeat(33));
        assertFalse(d.valid());
    }
}
