package com.serialcraft.connection;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class OutputValueStoreTest {
    @TempDir Path directory;

    @Test void valuesSurviveReloadWithoutCrossingDevicesWorldsOrChannels() {
        Path file = directory.resolve("output.json");
        var store = new OutputValueStore(file);
        store.remember("world-a|usb-a", "lamp", 80);
        store.remember("world-b|usb-a", "lamp", 200);
        store.remember("world-a|usb-b", "lamp", 123);
        store.flush();
        var loaded = new OutputValueStore(file);
        assertEquals(80, loaded.get("world-a|usb-a", "lamp"));
        assertEquals(200, loaded.get("world-b|usb-a", "lamp"));
        assertEquals(123, loaded.get("world-a|usb-b", "lamp"));
        assertNull(loaded.get("world-a|usb-a", "motor"));
    }

    @Test void invalidOrReservedChannelsAreNotPersistedAndCapacityIsBounded() throws Exception {
        Path file = directory.resolve("output.json");
        var store = new OutputValueStore(file);
        store.remember("board", "mc_time", 80);
        store.remember("board", "bad key", 80);
        store.remember("board", "lamp", 256);
        assertNull(store.get("board", "mc_time"));
        assertNull(store.get("board", "lamp"));
        for (int i = 0; i < 100; i++) store.remember("board", "ch_" + i, i);
        store.flush();
        assertFalse(Files.readString(file).contains("ch_0\""));
        assertEquals(99, new OutputValueStore(file).get("board", "ch_99"));
    }
}
