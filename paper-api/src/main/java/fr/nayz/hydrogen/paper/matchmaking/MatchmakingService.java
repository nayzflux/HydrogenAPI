package fr.nayz.hydrogen.paper.matchmaking;

import fr.nayz.hydrogen.api.matchmaking.MatchmakingQueue;

import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface MatchmakingService {
    CompletableFuture<QueueJoinResult> enqueueSolo(MatchmakingQueue queue, UUID playerId);

    CompletableFuture<QueueJoinResult> enqueueParty(MatchmakingQueue queue, UUID playerId);

    CompletableFuture<Boolean> leaveQueue(UUID playerId);

    Optional<MatchAssignment> currentAssignment();

    CompletableFuture<Void> reportGameStarting(String sessionId);

    CompletableFuture<Void> reportGameStarted(String sessionId);

    CompletableFuture<Void> reportPlayerEliminated(String sessionId, UUID playerId);

    CompletableFuture<Void> reportGameCancelled(String sessionId, String reason);

    CompletableFuture<Void> reportGameFinished(String sessionId, Map<String, Object> results);
}
