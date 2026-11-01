package com.serialcraft.connection;

import com.serialcraft.config.OutputSettings;
import java.util.LinkedHashMap;
import java.util.Map;

/** Pure session logic: boot resends cannot overwrite the chosen initial value. */
public final class OutputSession {
    private final Map<String, Integer> initialRequests = new LinkedHashMap<>();
    private final Map<String, Integer> initialValues = new LinkedHashMap<>();
    private final Map<String, Integer> active = new LinkedHashMap<>();

    public synchronized int initialize(String channel, int requested, Integer remembered, OutputSettings settings) {
        if (!initialRequests.containsKey(channel)) {
            if (initialRequests.size() >= HardwareOutbox.MAX_CHANNELS) return requested;
            initialRequests.put(channel, requested);
            initialValues.put(channel, settings.initialValue(remembered));
        }
        Integer baseline = initialRequests.get(channel);
        if (baseline != null && baseline == requested) return initialValues.get(channel);
        // A real world transition releases the startup override, including transitions back.
        initialRequests.put(channel, null);
        return requested;
    }

    public synchronized void manualOverride(String channel) {
        if (initialRequests.containsKey(channel) || initialRequests.size() < HardwareOutbox.MAX_CHANNELS)
            initialRequests.put(channel, null);
    }

    public synchronized void written(String channel, int value, boolean stop) {
        if (stop) active.remove(channel);
        else {
            if (!active.containsKey(channel) && active.size() >= HardwareOutbox.MAX_CHANNELS)
                active.remove(active.keySet().iterator().next());
            active.put(channel, value);
        }
    }

    public synchronized Map<String, Integer> activeValues() { return Map.copyOf(active); }
    public synchronized void clear() { initialRequests.clear(); initialValues.clear(); active.clear(); }
}
