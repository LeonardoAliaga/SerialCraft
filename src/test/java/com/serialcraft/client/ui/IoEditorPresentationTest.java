package com.serialcraft.client.ui;

import com.serialcraft.block.IOSide;
import com.serialcraft.board.IoMode;
import com.serialcraft.board.LogicMode;
import com.serialcraft.board.SignalType;
import com.serialcraft.client.ui.io.IoEditorDraft;
import com.serialcraft.client.ui.io.IoEditorLayout;
import com.serialcraft.client.ui.widget.ContextHelpDialog;
import com.serialcraft.client.ui.widget.IconTextButton;
import com.serialcraft.client.ui.widget.SectionTabButton;
import com.serialcraft.network.BoardInfo;
import com.serialcraft.network.ConfigResultPayload;
import com.serialcraft.network.IoSnapshot;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class IoEditorPresentationTest {
    @BeforeAll static void initialize() { com.serialcraft.test.TestBootstrap.initialize(); }
    private static IoEditorDraft draft() {
        return draft(0);
    }
    private static IoEditorDraft draft(int sides) {
        return new IoEditorDraft(new BoardInfo(new BlockPos(1, 2, 3), "module", "led_verde", IoMode.OUTPUT,
                SignalType.DIGITAL, LogicMode.OR, true, new IoSnapshot(sides, -1, -1, 0, 0, 0, false, -1)), "minecraft:overworld");
    }

    @Test void switchingSectionsAndResizingPreservesEveryDraftField() {
        var d = draft(IOSide.with(0, 4, IOSide.OUTPUT));
        assertFalse(d.dirty());
        d.name("new module"); d.channel("pot_val"); d.mode(IoMode.INPUT);
        d.signal(SignalType.ANALOG); d.logic(LogicMode.XOR); d.enabled(false);
        var values = d.values();
        for (var section : IoEditorDraft.Section.values()) {
            d.section(section);
            for (int width : new int[]{320, 426, 640, 1920}) IoEditorLayout.calculate(width, 240, UiTheme.contentX(width), 200, 110, 100);
            assertEquals(values, d.values());
            assertEquals(IOSide.OUTPUT, IOSide.at(d.values().sides(), 4));
            assertTrue(d.dirty());
        }
    }

    @Test void pendingSaveFreezesDataAndRejectsDuplicateClicks() {
        var d = draft(); d.channel("pot_val");
        assertTrue(d.beginSave(42, 100));
        var submitted = d.values();
        d.channel("other"); d.mode(IoMode.INPUT);
        assertEquals(submitted, d.values());
        assertFalse(d.beginSave(43, 200));
        assertEquals(42, d.payload().requestId());
        assertEquals("pot_val", d.payload().targetData());
        d.section(IoEditorDraft.Section.DIAGNOSTICS);
        assertTrue(d.saving());
    }

    @Test void onlyMatchingServerAcceptanceMarksDraftSaved() {
        var d = draft(); d.name("renamed"); d.beginSave(7, 100);
        assertFalse(d.accept(new ConfigResultPayload(BlockPos.ZERO, true, "", 7)));
        assertFalse(d.accept(new ConfigResultPayload(d.target().pos(), true, "", 6)));
        assertTrue(d.dirty());
        assertTrue(d.accept(new ConfigResultPayload(d.target().pos(), true, "", 7)));
        assertEquals(IoEditorDraft.SaveState.ACCEPTED, d.saveState());
        assertFalse(d.dirty());
        assertEquals("gui.serialcraft.editor.saved", d.message());
        d.name("again"); assertTrue(d.dirty());
        assertEquals(IoEditorDraft.SaveState.IDLE, d.saveState());
    }

    @Test void rejectionKeepsChangesAndItsReason() {
        var d = draft(); d.channel("pot_val"); d.beginSave(1, 100);
        assertTrue(d.accept(new ConfigResultPayload(d.target().pos(), false, "message.serialcraft.not_owner", 1)));
        assertEquals("pot_val", d.values().channel());
        assertTrue(d.dirty()); assertFalse(d.saving());
        assertEquals("message.serialcraft.not_owner", d.message());
        assertTrue(d.beginSave(2, 200));
    }

    @Test void acceptanceDisplaysTheSameNormalizedIdentityAsTheServer() {
        var d = draft(); d.name("  renamed  "); d.channel(" pot_val "); d.beginSave(1, 0);
        assertEquals("renamed", d.payload().boardId());
        assertEquals("  renamed  ", d.values().name());
        d.accept(new ConfigResultPayload(d.target().pos(), true, "", 1));
        assertEquals("renamed", d.values().name()); assertEquals("pot_val", d.values().channel());
        assertFalse(d.dirty());
    }

    @Test void timeoutKeepsDraftAndIgnoresLateOrOldResponses() {
        var d = draft(); d.enabled(false); d.beginSave(1, 100);
        assertFalse(d.tick(100 + IoEditorDraft.SAVE_TIMEOUT_NANOS));
        assertTrue(d.tick(101 + IoEditorDraft.SAVE_TIMEOUT_NANOS));
        assertTrue(d.dirty()); assertFalse(d.values().enabled());
        assertEquals(IoEditorDraft.SaveState.TIMED_OUT, d.saveState());
        assertFalse(d.accept(new ConfigResultPayload(d.target().pos(), true, "", 1)));
        assertTrue(d.beginSave(2, 6_000_000_000L));
        assertFalse(d.accept(new ConfigResultPayload(d.target().pos(), true, "", 1)));
    }

    @Test void presentationValidationRetainsProtocolAndLengthLimits() {
        var d = draft();
        for (String invalid : new String[]{"", "mc_health", "bad:key", "a".repeat(33), "á"}) {
            d.channel(invalid); assertFalse(d.valid()); assertFalse(d.beginSave(1, 0));
        }
        d.channel(" pot_val "); assertTrue(d.valid());
        d.name("n".repeat(33)); assertFalse(d.valid());
        d.name("n".repeat(32)); assertTrue(d.valid());
        d.beginSave(3, 0); assertEquals("pot_val", d.payload().targetData());
    }

    @Test void editingConfigurationPreservesAllFiveConnectorRolesInPayload() {
        int sides = 0;
        for (int i = 0; i < 5; i++) {
            sides = IOSide.with(sides, i, IOSide.values()[i % 3]);
        }
        var d = draft(sides);
        d.mode(IoMode.INPUT); d.signal(SignalType.ANALOG); d.logic(LogicMode.AND);
        d.name("edited"); d.channel("pot_val"); d.enabled(false);
        d.beginSave(12, 100);
        assertEquals(sides, d.values().sides());
        assertEquals(sides, d.payload().sides());
        d.accept(new ConfigResultPayload(d.target().pos(), true, "", 12));
        assertEquals(sides, d.values().sides());
        assertTrue(IOSide.isValidPacked(sides));
    }

    @Test void layoutsKeepFooterAndViewportInsideScreenAtDifferentGuiSizes() {
        for (int width : new int[]{320, 426, 640, 1920}) for (int height : new int[]{180, 240, 384, 1080}) {
            var screen = new IoEditorLayout.Rect(0, 0, width, height);
            var l = IoEditorLayout.calculate(width, height, UiTheme.contentX(width), 200, 110, 100);
            assertTrue(screen.contains(l.panel()));
            for (var rect : new IoEditorLayout.Rect[]{l.viewport(), l.status(), l.save(), l.cancel(), l.configurationTab(), l.diagnosticsTab()})
                assertTrue(l.panel().contains(rect));
            assertTrue(l.viewport().bottom() < l.status().y());
            assertTrue(l.status().bottom() <= l.save().y());
            assertTrue(l.save().right() < l.cancel().x());
            assertTrue(l.viewport().height() >= 38);
            assertEquals(8, l.panel().y());
            assertEquals(UiTheme.contentX(width), l.viewport().x());
            assertTrue(l.viewport().y() >= l.diagnosticsTab().bottom() + 6);
            assertTrue(l.configurationTab().right() < l.diagnosticsTab().x());
            assertEquals(l.panel().right(), l.diagnosticsTab().right());
            if (l.tabsBesideTitle()) assertTrue(l.panel().x() + 200 + 16 <= l.configurationTab().x());
            else assertTrue(l.configurationTab().y() >= 40);
        }
    }

    @Test void editorExposesConfigurationAndDiagnosticsWithoutConnectorControls() {
        assertArrayEquals(new IoEditorDraft.Section[]{IoEditorDraft.Section.CONFIGURATION, IoEditorDraft.Section.DIAGNOSTICS},
                IoEditorDraft.Section.values());
    }

    @Test void helpDialogBoundsStayInsideSmallAndLargeScreens() {
        for (int width : new int[]{320, 426, 1920}) for (int height : new int[]{180, 240, 1080}) {
            var screen = new IoEditorLayout.Rect(0, 0, width, height);
            assertTrue(screen.contains(ContextHelpDialog.bounds(width, height, 900)));
        }
        assertEquals(153, SpriteIcon.QUEST.u()); assertEquals(153, SpriteIcon.QUEST.v());
    }

    @Test void sharedButtonsSupportKeyboardActivationAndDisabledStates() {
        AtomicInteger clicks = new AtomicInteger();
        var solid = SolidButton.primary(0, 0, 80, 22, Component.empty(), b -> clicks.incrementAndGet());
        var icon = new IconTextButton(0, 0, 28, 22, SpriteIcon.QUEST, Component.empty(), b -> clicks.incrementAndGet(), 0, 0);
        var tab = new SectionTabButton(0, 0, 100, 24, Component.empty(), true, UiTheme.ACCENT_BOARDS_BORDER, b -> clicks.incrementAndGet());
        solid.setFocused(true); icon.setFocused(true); tab.setFocused(true);
        assertTrue(solid.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)));
        assertTrue(icon.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0)));
        assertTrue(tab.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)));
        solid.active = false; icon.visible = false; tab.active = false;
        assertFalse(solid.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0)));
        assertFalse(icon.keyPressed(new KeyEvent(GLFW.GLFW_KEY_ENTER, 0, 0)));
        assertFalse(tab.keyPressed(new KeyEvent(GLFW.GLFW_KEY_SPACE, 0, 0)));
        assertEquals(3, clicks.get());
    }
}
