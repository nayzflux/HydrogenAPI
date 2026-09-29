package fr.nayz.hydrogen.paper.matchmaking;

import fr.nayz.hydrogen.api.matchmaking.MatchmakingQueue;

import java.util.Map;
import java.util.UUID;

public record MatchAssignment(
        String sessionId,
        MatchmakingQueue queue,
        Map<UUID, String> partyIds
) {
    public MatchAssignment {
        partyIds = Map.copyOf(partyIds);
    }
}
