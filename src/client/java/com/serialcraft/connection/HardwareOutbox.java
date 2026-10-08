package com.serialcraft.connection;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Shared bounded transport pacing. IO/manual transitions are FIFO; continuous samples coalesce. */
public final class HardwareOutbox {
    public enum Kind { IO, TELEMETRY, SIGNAL, MANUAL }
    public record Line(String text, Kind kind, boolean quiet) {}
    public static final int MAX_CHANNELS = 64;
    public static final int PER_CHANNEL_CAPACITY = 8;
    private final Map<String, Deque<Line>> channels = new LinkedHashMap<>();
    private final Deque<Line> urgent = new ArrayDeque<>();
    private int urgentRun;
    private long dropped;

    public synchronized boolean offer(String text, Kind kind, boolean quiet, boolean safetyStop) {
        if (text == null || text.length() > 256 || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0) return false;
        Line line = new Line(text, kind, quiet);
        int colon = text.indexOf(':');
        String key = colon > 0 ? text.substring(0, colon) : "manual";
        if (safetyStop) {
            channels.remove(key); // no generated sample may follow the rest value
            urgent.removeIf(p -> p.text.startsWith(key + ':'));
            if (urgent.size() >= 16) { urgent.removeFirst(); dropped++; }
            urgent.addFirst(line);
            return true;
        }
        if (!channels.containsKey(key) && channels.size() >= MAX_CHANNELS) { dropped++; return false; }
        Deque<Line> queue = channels.computeIfAbsent(key, k -> new ArrayDeque<>());
        boolean edge = text.startsWith("mc_damage:") || text.startsWith("mc_death:");
        if (!edge && (kind == Kind.SIGNAL || kind == Kind.TELEMETRY)
                && !queue.isEmpty() && queue.peekLast().kind == kind) queue.removeLast();
        if (queue.size() >= PER_CHANNEL_CAPACITY) { queue.removeFirst(); dropped++; }
        queue.addLast(line);
        return true;
    }

    public synchronized Line poll() {
        if (!urgent.isEmpty() && (urgentRun < 2 || channels.isEmpty())) { urgentRun++; return urgent.removeFirst(); }
        urgentRun = 0;
        if (channels.isEmpty()) return null;
        String key = channels.keySet().iterator().next();
        Deque<Line> queue = channels.remove(key);
        Line result = queue.removeFirst();
        if (!queue.isEmpty()) channels.put(key, queue);
        return result;
    }

    public synchronized void discard(String key) {
        channels.remove(key);
        urgent.removeIf(p -> p.text.startsWith(key + ':'));
    }
    public synchronized void clear() { channels.clear(); urgent.clear(); urgentRun = 0; }
    public synchronized int size() { return urgent.size() + channels.values().stream().mapToInt(Deque::size).sum(); }
    public synchronized long dropped() { return dropped; }
}
