package com.serialcraft.network;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProtocolTest {
    @Test void invalidWireDataAndReservedKeysAreRejected() {
        for (String bad : new String[]{"", "x:", ":12", "x:-1", "x:256", "x:1:2", "x:NaN", "x:1.5",
                "mc_health:15", "MC_HEALTH:15", "bad key:15", "x:" + "9".repeat(50)}) {
            assertTrue(SignalProtocol.parse(bad).isEmpty(), bad);
        }
        for (int value : new int[]{0, 128, 255}) assertEquals(value, SignalProtocol.parse("sensor:" + value).orElseThrow().value());
    }

    @Test void twoSensorsInSameBurstBothSurviveAndTakeTurns() {
        var inbox = new ChannelInbox();
        inbox.offer(new SignalProtocol.Sample("a", 255), 0);
        inbox.offer(new SignalProtocol.Sample("a", 0), 1);
        inbox.offer(new SignalProtocol.Sample("b", 128), 2);
        assertEquals("a", inbox.poll().channel());
        assertEquals("b", inbox.poll().channel());
        assertEquals(0, inbox.poll().value());
        assertNull(inbox.poll());
    }

    @Test void duplicateThrottlingKeepsRefreshAndCapacityIsBounded() {
        var inbox = new ChannelInbox();
        var sample = new SignalProtocol.Sample("a", 1);
        inbox.offer(sample, 0); inbox.offer(sample, 1);
        assertEquals(1, inbox.size());
        inbox.poll(); inbox.offer(sample, 1_000_000_000L);
        assertEquals(1, inbox.size());
        for (int channel = 0; channel < 100; channel++) {
            for (int v = 0; v < 100; v++) inbox.offer(new SignalProtocol.Sample("c" + channel, v), v);
        }
        assertTrue(inbox.size() <= ChannelInbox.MAX_CHANNELS * ChannelInbox.PER_CHANNEL_CAPACITY);
        assertTrue(inbox.dropped() > 0);
        inbox.clear(); assertEquals(0, inbox.size());
    }

    @Test void telemetryScalesAndNamesStaySeparate() {
        assertTrue(TelemetryProtocol.isReservedKey("MC_id"));
        assertEquals(32767, TelemetryProtocol.clamp(Integer.MAX_VALUE, 0, Integer.MAX_VALUE));
        assertEquals(-32768, TelemetryProtocol.clamp(Integer.MIN_VALUE, Integer.MIN_VALUE, 0));
        assertEquals("mc_health:20", TelemetryProtocol.format("mc_health", 20));
    }
}
