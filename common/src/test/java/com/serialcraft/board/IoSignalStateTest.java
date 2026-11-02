package com.serialcraft.board;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IoSignalStateTest {
    @Test void analogRoundTripAndSaturation() {
        for (int rs = 0; rs <= 15; rs++) {
            assertEquals(rs * 17, SignalType.ANALOG.redstoneToWire(rs));
            assertEquals(rs, SignalType.ANALOG.wireToRedstone(rs * 17));
        }
        assertEquals(8, SignalType.ANALOG.wireToRedstone(128));
        assertEquals(0, SignalType.ANALOG.wireToRedstone(-1));
        assertEquals(15, SignalType.ANALOG.wireToRedstone(999));
        assertEquals(255, SignalType.DIGITAL.redstoneToWire(1));
        assertEquals(15, SignalType.DIGITAL.wireToRedstone(1));
    }

    @Test void allDigitalTruthTablesUpToFiveInputs() {
        for (int count = 1; count <= 5; count++) {
            for (int mask = 0; mask < (1 << count); mask++) {
                int[] levels = new int[count];
                for (int i = 0; i < count; i++) levels[i] = (mask & (1 << i)) != 0 ? 15 : 0;
                int active = Integer.bitCount(mask);
                assertEquals(active > 0 ? 15 : 0, IoLogic.combine(LogicMode.OR, levels));
                assertEquals(active == count ? 15 : 0, IoLogic.combine(LogicMode.AND, levels));
                assertEquals(active % 2 == 1 ? 15 : 0, IoLogic.combine(LogicMode.XOR, levels));
            }
        }
    }

    @Test void analogMagnitudeIsDefinedPerGate() {
        assertEquals(12, IoLogic.combine(LogicMode.OR, 2, 7, 12));
        assertEquals(2, IoLogic.combine(LogicMode.AND, 2, 7, 12));
        assertEquals(0, IoLogic.combine(LogicMode.AND, 0, 15));
        assertEquals(0, IoLogic.combine(LogicMode.XOR, 2, 7));
        assertEquals(12, IoLogic.combine(LogicMode.XOR, 2, 7, 12));
        for (var gate : LogicMode.values()) assertEquals(0, IoLogic.combine(gate));
    }

    @Test void hardwareSampleSurvivesClosedGate() {
        var state = new IoSignalState();
        state.receive(128);
        state.evaluate(IoMode.INPUT, SignalType.ANALOG, LogicMode.AND, true, 0, 15);
        assertEquals(0, state.emitted());
        assertEquals(128, state.receivedWire());
        state.evaluate(IoMode.INPUT, SignalType.ANALOG, LogicMode.AND, true, 7, 15);
        assertEquals(8, state.emitted());
        state.evaluate(IoMode.INPUT, SignalType.DIGITAL, LogicMode.AND, true, 7, 15);
        assertEquals(15, state.emitted());
    }

    @Test void worldInputCannotFeedWorldOutputInTransmitMode() {
        var state = new IoSignalState();
        state.receive(255);
        for (int value : new int[]{0, 1, 7, 15}) {
            state.evaluate(IoMode.OUTPUT, SignalType.ANALOG, LogicMode.OR, true, value);
            assertEquals(value, state.processed());
            assertEquals(0, state.emitted());
            assertEquals(value * 17, SignalType.ANALOG.redstoneToWire(state.processed()));
        }
    }

    @Test void disableDisconnectAndNoConnectors() {
        var state = new IoSignalState();
        state.receive(255);
        state.evaluate(IoMode.INPUT, SignalType.DIGITAL, LogicMode.OR, true);
        assertEquals(15, state.emitted());
        state.evaluate(IoMode.INPUT, SignalType.DIGITAL, LogicMode.OR, false);
        assertEquals(0, state.emitted());
        state.invalidateHardware();
        state.evaluate(IoMode.INPUT, SignalType.DIGITAL, LogicMode.OR, true);
        assertEquals(0, state.emitted());
        assertEquals(-1, state.receivedWire());
        state.evaluate(IoMode.OUTPUT, SignalType.DIGITAL, LogicMode.AND, true);
        assertEquals(0, state.processed());
        assertThrows(IllegalArgumentException.class, () -> state.receive(256));
    }
}
