package com.serialcraft.connection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.serialcraft.SerialCraft;
import com.serialcraft.network.SignalProtocol;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Last successful normal writes, separated by world/dimension/device and channel. */
public final class OutputValueStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final int MAX_DEVICES = 64;
    private final Path path;
    private final Map<String, Map<String, Integer>> values = new LinkedHashMap<>();
    private boolean dirty;

    public OutputValueStore(Path path) {
        this.path = path;
        if (!Files.isRegularFile(path)) return;
        try (var reader = Files.newBufferedReader(path)) {
            Map<String, Map<String, Integer>> loaded = GSON.fromJson(reader,
                    new TypeToken<LinkedHashMap<String, Map<String, Integer>>>() {}.getType());
            if (loaded != null) loaded.forEach((device, channels) -> {
                if (device == null || device.isBlank() || device.length() > 2048 || channels == null) return;
                channels.forEach((key, value) -> {
                    if (SignalProtocol.isValidChannel(key) && value != null && value >= 0 && value <= 255)
                        remember(device, key, value);
                });
            });
            dirty = false;
        } catch (IOException | RuntimeException e) {
            SerialCraft.LOGGER.warn("Could not read saved hardware output values", e);
        }
    }

    public synchronized Integer get(String device, String channel) {
        var channels = values.get(device);
        return channels == null ? null : channels.get(channel);
    }

    public synchronized void remember(String device, String channel, int value) {
        if (device == null || device.isBlank() || !SignalProtocol.isValidChannel(channel) || value < 0 || value > 255) return;
        if (!values.containsKey(device) && values.size() >= MAX_DEVICES)
            values.remove(values.keySet().iterator().next());
        var channels = values.computeIfAbsent(device, ignored -> new LinkedHashMap<>());
        if (!channels.containsKey(channel) && channels.size() >= HardwareOutbox.MAX_CHANNELS)
            channels.remove(channels.keySet().iterator().next());
        Integer previous = channels.put(channel, value);
        dirty |= previous == null || previous != value;
    }

    public synchronized void flush() {
        if (!dirty) return;
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(temporary, GSON.toJson(values));
            try { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temporary, path, StandardCopyOption.REPLACE_EXISTING); }
            dirty = false;
        } catch (IOException e) { SerialCraft.LOGGER.warn("Could not save hardware output values", e); }
    }
}
