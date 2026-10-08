package com.serialcraft.client.events;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TelemetryOutboxTest {
    @Test void statesCoalesceEdgesKeepPriorityAndDisableClearsPending() {
        var out = new TelemetryOutbox();
        out.offerState("mc_health", 20, false);
        out.offerState("mc_health", 19, true);
        out.offerEdge("mc_damage", 1);
        assertEquals("mc_damage:1", out.poll().text());
        var state = out.poll(); assertEquals("mc_health:19", state.text()); assertFalse(state.quiet());
        out.offerState("mc_health", 10, true); out.discard("mc_health"); assertTrue(out.isEmpty());
    }
    @Test void edgeOverflowIsBoundedAndCounted() {
        var out = new TelemetryOutbox();
        for (int i = 0; i < 40; i++) out.offerEdge("mc_damage", i);
        assertEquals(16, out.size()); assertEquals(24, out.droppedEdges());
        assertEquals("mc_damage:24", out.poll().text());
    }
}
