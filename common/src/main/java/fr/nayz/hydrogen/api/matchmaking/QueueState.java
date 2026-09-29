package fr.nayz.hydrogen.api.matchmaking;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QueueState(
        UUID ticketId,
        MatchmakingQueue queue,
        String partyId,
        List<UUID> members,
        String enderCloudEntryId,
        Instant enteredAt,
        QueueStatus status
) {
    public QueueState {
        members = List.copyOf(members);
    }

    public Duration elapsedAt(Instant now) {
        Duration elapsed = Duration.between(enteredAt, now);
        return elapsed.isNegative() ? Duration.ZERO : elapsed;
    }
}
