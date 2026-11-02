package com.serialcraft.network;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded round-robin input. Keeps channel transitions; overload drops the oldest of that channel. */
public final class ChannelInbox {
    public static final int MAX_CHANNELS = 64;
    public static final int PER_CHANNEL_CAPACITY = 8;
    private final Map<String, Deque<SignalProtocol.Sample>> pending = new LinkedHashMap<>();
    private final Map<String, Last> last = new LinkedHashMap<>();
    private record Last(int value, long time) {}
    private long dropped;

    public synchronized void offer(SignalProtocol.Sample sample, long now) {
        Last previous = last.get(sample.channel());
        if (previous != null && previous.value == sample.value() && now - previous.time < 1_000_000_000L) return;
        if (!last.containsKey(sample.channel()) && last.size() >= MAX_CHANNELS) {
            String idle = last.keySet().stream().filter(key -> !pending.containsKey(key)).findFirst().orElse(null);
            if (idle == null) { dropped++; return; }
            last.remove(idle);
        }
        last.put(sample.channel(), new Last(sample.value(), now));
        Deque<SignalProtocol.Sample> queue = pending.computeIfAbsent(sample.channel(), k -> new ArrayDeque<>());
        if (queue.size() >= PER_CHANNEL_CAPACITY) { queue.removeFirst(); dropped++; }
        queue.addLast(sample);
    }

    public synchronized SignalProtocol.Sample poll() {
        if (pending.isEmpty()) return null;
        String channel = pending.keySet().iterator().next();
        Deque<SignalProtocol.Sample> queue = pending.remove(channel);
        SignalProtocol.Sample next = queue.removeFirst();
        if (!queue.isEmpty()) pending.put(channel, queue); // move to the back for fairness
        return next;
    }

    public synchronized int size() { return pending.values().stream().mapToInt(Deque::size).sum(); }
    public synchronized long dropped() { return dropped; }
    public synchronized void clear() { pending.clear(); last.clear(); dropped = 0; }
}
