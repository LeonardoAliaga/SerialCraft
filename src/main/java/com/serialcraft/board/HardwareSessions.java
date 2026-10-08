package com.serialcraft.board;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Server-thread-only session reports. Never persisted or inferred from silence. */
public final class HardwareSessions {
    private static final Set<UUID> CONNECTED = new HashSet<>();
    private HardwareSessions() {}
    public static boolean connected(UUID owner) { return owner != null && CONNECTED.contains(owner); }
    public static void update(UUID owner, boolean connected) {
        if (connected) CONNECTED.add(owner); else CONNECTED.remove(owner);
        for (var io : BoardRegistry.allBoardsOf(owner)) io.onHardwareSessionChanged();
    }
    public static void clear() { CONNECTED.clear(); }
}
