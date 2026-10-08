package com.serialcraft.network.guard;

import org.junit.jupiter.api.Test;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class PacketRateLimiterTest {
    @Test void burstBudgetIsPerPlayerAndForgetResetsIt() {
        var limiter = new PacketRateLimiter(2, 1);
        var one = UUID.randomUUID(); var two = UUID.randomUUID();
        assertTrue(limiter.tryAcquire(one)); assertTrue(limiter.tryAcquire(one));
        assertFalse(limiter.tryAcquire(one)); assertTrue(limiter.tryAcquire(two));
        limiter.forget(one); assertTrue(limiter.tryAcquire(one));
    }
}
