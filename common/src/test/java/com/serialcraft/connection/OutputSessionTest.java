package com.serialcraft.connection;

import com.serialcraft.config.OutputSettings;
import com.serialcraft.config.OutputSettings.ConnectAction;
import com.serialcraft.config.OutputSettings.DisconnectAction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OutputSessionTest {
    @Test void independentActionsAcceptTheWholeHardwareRange() {
        for (ConnectAction connect : ConnectAction.values()) for (DisconnectAction disconnect : DisconnectAction.values()) {
            var settings = new OutputSettings(connect, 80, disconnect, 200);
            assertEquals(connect == ConnectAction.ZERO ? 0 : connect == ConnectAction.CUSTOM ? 80 : 123,
                    settings.initialValue(123));
            assertEquals(disconnect == DisconnectAction.KEEP ? null : disconnect == DisconnectAction.ZERO ? 0 : 200,
                    settings.finalValue());
        }
        assertEquals(0, new OutputSettings(ConnectAction.RESTORE, 0, null, 0).initialValue(null));
        assertEquals(0, new OutputSettings(null, -1, null, 300).connectValue());
        assertEquals(255, new OutputSettings(null, -1, null, 300).disconnectValue());
    }

    @Test void bootResendsRetainInitialValueUntilTheFirstRealTransition() {
        var session = new OutputSession();
        var settings = new OutputSettings(ConnectAction.CUSTOM, 80, DisconnectAction.ZERO, 0);
        assertEquals(80, session.initialize("lamp", 255, 123, settings));
        assertEquals(80, session.initialize("lamp", 255, 123, settings));
        assertEquals(0, session.initialize("lamp", 0, 123, settings));
        assertEquals(255, session.initialize("lamp", 255, 123, settings));
        session.manualOverride("other");
        assertEquals(42, session.initialize("other", 42, 123, settings));
        session.clear();
        assertEquals(80, session.initialize("lamp", 255, 123, settings));
    }

    @Test void normalWritesAndStopsTrackOnlyActiveChannels() {
        var session = new OutputSession();
        session.written("a", 80, false); session.written("b", 255, false);
        session.written("a", 0, true);
        assertEquals(java.util.Map.of("b", 255), session.activeValues());
    }
}
