package fr.nayz.hydrogen.velocity.command;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

public final class PartyInviteLimiter {
    private final Map<UUID, ArrayDeque<Long>> attempts = new ConcurrentHashMap<>();
    private final LongSupplier clock;

    public PartyInviteLimiter() {
        this(System::currentTimeMillis);
    }

    PartyInviteLimiter(LongSupplier clock) {
        this.clock = clock;
    }

    public boolean allow(UUID author) {
        ArrayDeque<Long> times = attempts.computeIfAbsent(author, ignored -> new ArrayDeque<>());
        synchronized (times) {
            long now = clock.getAsLong();
            while (!times.isEmpty() && times.peekFirst() <= now - 10_000) {
                times.removeFirst();
            }
            if (times.size() >= 10) {
                return false;
            }
            times.addLast(now);
            return true;
        }
    }
}
