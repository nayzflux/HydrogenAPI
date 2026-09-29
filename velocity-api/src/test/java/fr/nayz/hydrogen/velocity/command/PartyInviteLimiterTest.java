package fr.nayz.hydrogen.velocity.command;

import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class PartyInviteLimiterTest {
    @Test
    void limitsEachAuthorIndependently() {
        AtomicLong time = new AtomicLong(1_000_000);
        PartyInviteLimiter limiter = new PartyInviteLimiter(time::get);
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        for (int attempt = 0; attempt < 10; attempt++) {
            assertTrue(limiter.allow(first));
        }
        assertFalse(limiter.allow(first));
        assertTrue(limiter.allow(second));
        time.addAndGet(10_001);
        assertTrue(limiter.allow(first));
    }
}
