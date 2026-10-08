package com.serialcraft.signal;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.serialcraft.signal.SignalRecorder.Direction.*;

class SignalTest {
    @Test void ringBufferOrderingDirectionAndWindowPredecessor() {
        var r = new SignalRecorder();
        var id = new SignalRecorder.SeriesId(RX, "x");
        for (int i = 0; i < 5000; i++) r.record(RX, "x:" + i, i);
        r.record(TX, "x:1", 5000);
        assertEquals(2, r.seriesCount());
        var s = r.snapshot(id, 4990, 4999);
        assertTrue(s.hasPrior());
        assertEquals(4989, s.times()[0]);
        assertEquals(11, s.size());
        assertEquals(4096, r.snapshot(id, 0, 6000).size());
        r.record(RX, "x:2", 0);
        assertEquals(4999, r.lastNanos(id));
    }
    @Test void boundedSeriesAndMalformedValues() {
        var r = new SignalRecorder();
        for (int i = 0; i < 50; i++) r.record(RX, "k" + i + ":1", i);
        assertEquals(16, r.seriesCount());
        assertFalse(r.record(RX, "x:NaN", 51));
        assertFalse(r.record(RX, "x:Infinity", 51));
    }
    @Test void statisticsUseTimeAndKeepSingleMessagePulse() {
        var id = new SignalRecorder.SeriesId(RX, "x");
        var s = new SignalRecorder.Snapshot(id, new long[]{0, 900_000_000L}, new float[]{0, 100}, false);
        var stats = SignalStats.compute(s, 0, 1_000_000_000L, null);
        assertEquals(10, stats.mean(), 0.0001);
        assertEquals(30, stats.stdDev(), 0.0001);
        var env = StepEnvelope.compute(s, 0, 1_000_000_000L, 1);
        assertEquals(0, env.lo[0]); assertEquals(100, env.hi[0]);
    }
    @Test void interpolationStopsAcrossLongGaps() {
        long[] t = {0, 100_000_000L}; float[] v = {0, 100};
        assertEquals(50, LineEnvelope.valueAt(t, v, 2, 50_000_000L));
        t[1] = 1_000_000_000L;
        assertEquals(0, LineEnvelope.valueAt(t, v, 2, 500_000_000L));
    }
    @Test void generatorHasDefinedWaveAndSafeStop() {
        assertEquals(0, Waveform.value(Waveform.Shape.SINE, 0, 2, 100));
        assertEquals(255, Waveform.value(Waveform.Shape.SINE, 1, 2, 100));
        var g = new GeneratorController();
        g.start(0);
        assertTrue(g.tick(0, Waveform.Shape.SQUARE, 1, 100).send());
        var action = g.tick(600_000_000L, Waveform.Shape.SQUARE, 1, 100);
        assertTrue(action.stopped()); assertEquals(0, action.value()); assertFalse(g.isRunning());
    }
}
