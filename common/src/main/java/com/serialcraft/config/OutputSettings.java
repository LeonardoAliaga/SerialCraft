package com.serialcraft.config;

/** Independent policies for numeric IO channels. Values use the hardware range, 0..255. */
public record OutputSettings(ConnectAction onConnect, int connectValue,
                             DisconnectAction onDisconnect, int disconnectValue) {
    public enum ConnectAction { ZERO, CUSTOM, RESTORE }
    public enum DisconnectAction { ZERO, CUSTOM, KEEP }

    public OutputSettings {
        if (onConnect == null) onConnect = ConnectAction.ZERO;
        if (onDisconnect == null) onDisconnect = DisconnectAction.ZERO;
        connectValue = Math.clamp(connectValue, 0, 255);
        disconnectValue = Math.clamp(disconnectValue, 0, 255);
    }

    public static OutputSettings defaults() {
        return new OutputSettings(ConnectAction.ZERO, 0, DisconnectAction.ZERO, 0);
    }

    public int initialValue(Integer remembered) {
        return switch (onConnect) {
            case ZERO -> 0;
            case CUSTOM -> connectValue;
            case RESTORE -> remembered == null ? 0 : Math.clamp(remembered, 0, 255);
        };
    }

    /** Null deliberately means no final message. */
    public Integer finalValue() {
        return switch (onDisconnect) {
            case ZERO -> 0;
            case CUSTOM -> disconnectValue;
            case KEEP -> null;
        };
    }
}
