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
        assertTrue(out.size() <= HardwareOutbox.MAX_CHANNELS * (HardwareOutbox.PER_CHANNEL_CAPACITY + 1));
        assertTrue(out.dropped() > 0);
    }

    @Test void bulkStopsPreserveEverySupportedChannelAndCancelPendingValues() {
        for (int count : new int[]{17, HardwareOutbox.MAX_CHANNELS}) {
            var out = new HardwareOutbox();
            var expected = new java.util.HashSet<String>();
            for (int channel = 0; channel < count; channel++) {
                String key = "led_" + channel;
                assertTrue(out.offer(key + ":255", IO, false, false));
                assertTrue(out.offer(key + ":128", SIGNAL, true, false));
                expected.add(key + ":0");
            }
            // Modules can all stop before the next drain on removal, unload or dimension change.
            for (int channel = 0; channel < count; channel++) {
                assertTrue(out.offer("led_" + channel + ":0", IO, false, true));
            }
            assertEquals(count, out.size());
            for (int i = 0; i < count; i++) assertTrue(expected.remove(out.poll().text()));
            assertTrue(expected.isEmpty());
            assertNull(out.poll());
            assertEquals(0, out.dropped());
        }
    }

    @Test void fullStopQueueDeduplicatesAndRejectsOverflowWithoutEvictingAcceptedStops() {
        var out = new HardwareOutbox();
        for (int i = 0; i < HardwareOutbox.MAX_CHANNELS; i++) {
            assertTrue(out.offer("led_" + i + ":0", IO, false, true));
        }
        assertTrue(out.offer("extra_0:255", IO, false, false));
        for (int i = 0; i < 200; i++) {
            assertTrue(out.offer("led_0:0", SIGNAL, true, true));
            assertFalse(out.offer("extra_" + i + ":0", IO, false, true));
        }
        assertEquals(HardwareOutbox.MAX_CHANNELS, out.size());
        assertEquals(200, out.dropped());
        for (int i = 0; i < HardwareOutbox.MAX_CHANNELS; i++) assertEquals("led_" + i + ":0", out.poll().text());
        assertNull(out.poll());
    }

    @Test void stopsKeepTheTwoToOneBudgetAndCanBeExplicitlyCancelled() {
        var out = new HardwareOutbox();
        for (int i = 0; i < 4; i++) out.offer("led_" + i + ":0", IO, false, true);
        out.offer("mc_time:1", TELEMETRY, true, false);
        out.discard("led_3");
        assertEquals("led_0:0", out.poll().text());
        assertEquals("led_1:0", out.poll().text());
        assertEquals("mc_time:1", out.poll().text());
        assertEquals("led_2:0", out.poll().text());
        assertNull(out.poll());
        out.offer("led_0:0", IO, false, true);
        out.clear();
        assertNull(out.poll());
    }
}
