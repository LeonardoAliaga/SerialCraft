package com.serialcraft.network;

import java.util.Optional;
import java.util.regex.Pattern;

/** IO channels share the visualizer's ASCII grammar; mc_* belongs to telemetry/identity. */
public final class SignalProtocol {
    private static final Pattern KEY = Pattern.compile("[A-Za-z0-9_.-]{1,32}");
    private static final Pattern VALUE = Pattern.compile("[0-9]{1,3}");
    public record Sample(String channel, int value) {}
    private SignalProtocol() {}

    public static boolean isValidChannel(String channel) {
        return channel != null && KEY.matcher(channel).matches()
                && !TelemetryProtocol.isReservedKey(channel);
    }

    public static Optional<Sample> parse(String message) {
        if (message == null || message.length() > 40) return Optional.empty();
        int colon = message.indexOf(':');
        if (colon <= 0) return Optional.empty();
        String channel = message.substring(0, colon);
        String raw = message.substring(colon + 1).trim();
        if (!isValidChannel(channel) || !VALUE.matcher(raw).matches()) return Optional.empty();
        int value = Integer.parseInt(raw);
        return value <= 255 ? Optional.of(new Sample(channel, value)) : Optional.empty();
    }
}
