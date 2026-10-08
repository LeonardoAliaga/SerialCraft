package com.serialcraft.connection;

import org.junit.jupiter.api.Test;
import static com.serialcraft.connection.HardwareOutbox.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class HardwareOutboxTest {
    @Test void ioTransitionsAndTelemetryAreFair() {
        var out = new HardwareOutbox();
        out.offer("led:255", IO, false, false);
        out.offer("led:0", IO, false, false);
        out.offer("mc_time:1", TELEMETRY, true, false);
        assertEquals("led:255", out.poll().text());
        assertEquals("mc_time:1", out.poll().text());
        assertEquals("led:0", out.poll().text());
    }
    @Test void restCancelsPendingGeneratorValues() {
        var out = new HardwareOutbox();
        out.offer("servo:200", SIGNAL, true, false);
        out.offer("servo:0", SIGNAL, true, true);
        assertEquals("servo:0", out.poll().text());
        assertNull(out.poll());
    }
    @Test void coalescingDoesNotEraseDamageEventsAndMemoryIsBounded() {
        var out = new HardwareOutbox();
        out.offer("mc_time:1", TELEMETRY, true, false);
        out.offer("mc_time:2", TELEMETRY, true, false);
        out.offer("mc_damage:2", TELEMETRY, false, false);
        out.offer("mc_damage:2", TELEMETRY, false, false);
        assertEquals(3, out.size());
        out.discard("mc_damage"); assertEquals(1, out.size());
        for (int key = 0; key < 200; key++) for (int value = 0; value < 20; value++) out.offer("k" + key + ':' + value, IO, false, false);
        assertTrue(out.size() <= 64 * 8 + 16);
        assertTrue(out.dropped() > 0);
    }
}
