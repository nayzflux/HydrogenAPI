package fr.nayz.hydrogen.paper.matchmaking;

import fr.nayz.endercloud.core.api.EnderCloudPaperApi;
import fr.nayz.endercloud.core.model.HubTransferResult;
import fr.nayz.endercloud.core.model.QueueRequest;
import fr.nayz.endercloud.core.model.QueueResult;
import fr.nayz.endercloud.core.model.SessionAssignment;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class FakeCloud implements EnderCloudPaperApi {
    private QueueRequest request;
    private RuntimeException rejection;
    private SessionAssignment assignment;
    private int enqueueCount;

    void assignment(SessionAssignment value) {
        assignment = value;
    }

    void rejectWith(RuntimeException error) {
        rejection = error;
    }

    QueueRequest request() {
        return request;
    }

    int enqueueCount() {
        return enqueueCount;
    }

    @Override
    public CompletableFuture<QueueResult> enqueue(QueueRequest request) {
        this.request = request;
        enqueueCount++;
        if (rejection != null) {
            return CompletableFuture.failedFuture(rejection);
        }
        return CompletableFuture.completedFuture(new QueueResult("ender-ticket", "QUEUED"));
    }

    @Override
    public CompletableFuture<Boolean> leaveQueue(String groupId, String partyId) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public Optional<SessionAssignment> currentAssignment() {
        return Optional.ofNullable(assignment);
    }

    @Override
    public CompletableFuture<Boolean> sendToHub(UUID playerId) {
        return CompletableFuture.completedFuture(true);
    }

    @Override
    public CompletableFuture<HubTransferResult> sendToHub(Collection<UUID> playerIds) {
        UUID accepted = playerIds.iterator().next();
        return CompletableFuture.completedFuture(new HubTransferResult(
                java.util.List.of(accepted),
                playerIds.stream().filter(id -> !id.equals(accepted)).toList()
        ));
    }

    @Override
    public CompletableFuture<Void> reportGameStarting(String sessionId) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> reportGameStarted(String sessionId) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> reportPlayerEliminated(String sessionId, UUID playerId) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> reportGameCancelled(String sessionId, String reason) {
        return CompletableFuture.completedFuture(null);
    }

    @Override
    public CompletableFuture<Void> reportGameFinished(String sessionId, Map<String, Object> results) {
        return CompletableFuture.completedFuture(null);
    }
}
