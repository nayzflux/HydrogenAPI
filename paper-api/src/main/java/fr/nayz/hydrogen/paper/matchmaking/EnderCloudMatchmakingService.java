package fr.nayz.hydrogen.paper.matchmaking;

import fr.nayz.endercloud.core.api.EnderCloudPaperApi;
import fr.nayz.endercloud.core.http.EnderCloudException;
import fr.nayz.endercloud.core.model.QueueRequest;
import fr.nayz.endercloud.core.model.QueueResult;
import fr.nayz.endercloud.core.model.SessionAssignment;
import fr.nayz.hydrogen.api.HydrogenErrorReason;
import fr.nayz.hydrogen.api.HydrogenException;
import fr.nayz.hydrogen.api.matchmaking.MatchmakingQueue;
import fr.nayz.hydrogen.api.matchmaking.QueueState;
import fr.nayz.hydrogen.api.matchmaking.QueueStatus;
import fr.nayz.hydrogen.api.party.Party;
import fr.nayz.hydrogen.api.party.PartyService;
import fr.nayz.hydrogen.core.matchmaking.JdbcQueueStateService;
import fr.nayz.hydrogen.core.matchmaking.QueueLease;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

public final class EnderCloudMatchmakingService implements MatchmakingService {
    private final EnderCloudPaperApi cloud;
    private final PartyService parties;
    private final JdbcQueueStateService store;

    public EnderCloudMatchmakingService(
            EnderCloudPaperApi cloud,
            PartyService parties,
            JdbcQueueStateService store
    ) {
        this.cloud = cloud;
        this.parties = parties;
        this.store = store;
    }

    @Override
    public CompletableFuture<QueueJoinResult> enqueueSolo(MatchmakingQueue queue, UUID playerId) {
        return store.createOrFind(queue, playerId.toString(), List.of(playerId))
                .thenCompose(this::confirmOrRead)
                .thenApply(this::toJoinResult);
    }

    @Override
    public CompletableFuture<QueueJoinResult> enqueueParty(MatchmakingQueue queue, UUID playerId) {
        return parties.getForPlayer(playerId).thenCompose(found -> {
            Party party = found.orElseThrow(() -> new HydrogenException(
                    HydrogenErrorReason.NOT_FOUND, "Player has no party"));
            return store.createOrFind(queue, party.id().toString(), party.members())
                    .thenCompose(this::confirmOrRead)
                    .thenApply(this::toJoinResult);
        });
    }

    @Override
    public CompletableFuture<Boolean> leaveQueue(UUID playerId) {
        return store.claimLeave(playerId).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(false);
            }
            QueueLease lease = found.get();
            QueueState state = lease.state();
            CompletableFuture<Boolean> leaving;
            try {
                leaving = cloud.leaveQueue(state.queue().groupId(), state.partyId());
            } catch (RuntimeException error) {
                return store.finishLeave(lease, false).thenCompose(ignored ->
                        CompletableFuture.failedFuture(error));
            }
            return leaving
                    .handle((accepted, error) -> {
                        boolean success = error == null && Boolean.TRUE.equals(accepted);
                        return store.finishLeave(lease, success).thenApply(ignored -> {
                            if (error != null) {
                                throw new HydrogenException(HydrogenErrorReason.UNAVAILABLE,
                                        "EnderCloud queue leave failed", error);
                            }
                            return success;
                        });
                    }).thenCompose(result -> result);
        });
    }

    public CompletableFuture<Void> confirmDue(Collection<UUID> playerIds) {
        return store.getMany(playerIds).thenCompose(states -> {
            Set<UUID> visited = new HashSet<>();
            CompletableFuture<?>[] confirmations = states.values().stream()
                    .filter(state -> state.status().hasQueueInventory())
                    .filter(state -> visited.add(state.ticketId()))
                    .map(this::confirmIfDue)
                    .toArray(CompletableFuture[]::new);
            return CompletableFuture.allOf(confirmations);
        });
    }

    public CompletableFuture<Void> confirmForArrival(UUID playerId) {
        return store.get(playerId).thenCompose(found -> {
            if (found.isEmpty() || !found.get().status().hasQueueInventory()) {
                return CompletableFuture.completedFuture(null);
            }
            return confirmIfDue(found.get());
        });
    }

    private CompletableFuture<Void> confirmIfDue(QueueState state) {
        return store.claimConfirmation(state.ticketId()).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            return sendConfirmation(found.get()).thenAccept(ignored -> {
            });
        });
    }

    private CompletableFuture<QueueResult> confirmOrRead(QueueState state) {
        if (state.status() == QueueStatus.MATCH_FOUND) {
            return CompletableFuture.completedFuture(new QueueResult(state.enderCloudEntryId(), "SELECTED"));
        }
        return store.claimConfirmation(state.ticketId()).thenCompose(found -> {
            if (found.isPresent()) {
                return sendConfirmation(found.get());
            }
            return awaitConfirmation(state.members().getFirst(), 0);
        });
    }

    private CompletableFuture<QueueResult> awaitConfirmation(UUID playerId, int attempts) {
        return store.get(playerId).thenCompose(found -> {
            if (found.isEmpty()) {
                return CompletableFuture.failedFuture(new HydrogenException(
                        HydrogenErrorReason.NOT_FOUND, "Queue ticket was removed"));
            }
            QueueState state = found.get();
            if (state.enderCloudEntryId() != null && state.status() != QueueStatus.VERIFYING) {
                String resultState = state.status() == QueueStatus.MATCH_FOUND ? "SELECTED" : "QUEUED";
                return CompletableFuture.completedFuture(new QueueResult(state.enderCloudEntryId(), resultState));
            }
            if (attempts >= 40) {
                return CompletableFuture.failedFuture(new HydrogenException(
                        HydrogenErrorReason.UNAVAILABLE, "Queue confirmation is still in progress"));
            }
            return CompletableFuture.supplyAsync(
                    () -> null,
                    CompletableFuture.delayedExecutor(100, TimeUnit.MILLISECONDS)
            ).thenCompose(ignored -> awaitConfirmation(playerId, attempts + 1));
        });
    }

    private CompletableFuture<QueueResult> sendConfirmation(QueueLease lease) {
        QueueState state = lease.state();
        QueueRequest request = new QueueRequest(
                state.queue().groupId(), state.partyId(), state.members());
        CompletableFuture<QueueResult> sent;
        try {
            sent = cloud.enqueue(request);
        } catch (RuntimeException error) {
            return finishFailedConfirmation(lease, error);
        }
        return sent.handle((result, error) -> {
            if (error != null || result == null) {
                Throwable failure = error == null
                        ? new IllegalStateException("EnderCloud returned no queue result")
                        : error;
                return finishFailedConfirmation(lease, failure);
            }
            boolean selected = "SELECTED".equals(result.state());
            return store.confirm(lease, result.entryId(), selected).thenApply(ignored -> result);
        }).thenCompose(result -> result);
    }

    private CompletableFuture<QueueResult> finishFailedConfirmation(QueueLease lease, Throwable failure) {
        Throwable cause = failure instanceof CompletionException && failure.getCause() != null
                ? failure.getCause()
                : failure;
        CompletableFuture<Void> stored;
        if (cause instanceof EnderCloudException cloudError
                && cloudError.statusCode() >= 400
                && cloudError.statusCode() < 500
                && cloudError.statusCode() != 408
                && cloudError.statusCode() != 429) {
            stored = store.confirmationRejected(lease);
        } else {
            stored = store.confirmationFailed(lease);
        }
        return stored.thenCompose(ignored -> CompletableFuture.failedFuture(failure));
    }

    public CompletableFuture<Void> confirmAssignment(SessionAssignment assignment) {
        CompletableFuture<?>[] confirmations = assignment.players().stream()
                .map(player -> confirmAssignedPlayer(
                        assignment, UUID.fromString(player.playerId())))
                .toArray(CompletableFuture[]::new);
        return CompletableFuture.allOf(confirmations);
    }

    CompletableFuture<Void> confirmAssignedPlayer(SessionAssignment assignment, UUID playerId) {
        SessionAssignment.AssignedPlayer assigned = assignment.players().stream()
                .filter(player -> playerId.toString().equals(player.playerId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Player is not in this assignment"));

        return store.get(playerId).thenCompose(found -> {
            if (found.isEmpty() || found.get().status() == QueueStatus.MATCH_FOUND) {
                return CompletableFuture.completedFuture(null);
            }

            QueueState state = found.get();
            String assignedPartyId = assigned.partyId() == null
                    ? assigned.playerId()
                    : assigned.partyId();
            if (!state.queue().groupId().equals(assignment.groupId())
                    || !state.partyId().equals(assignedPartyId)
                    || !assigned.ticketId().equals(state.enderCloudEntryId())) {
                return CompletableFuture.failedFuture(new IllegalStateException(
                        "Assignment does not match Hydrogen ticket for " + playerId));
            }

            return store.markFound(state.ticketId(), assigned.ticketId())
                    .thenCompose(ignored -> store.get(playerId))
                    .thenAccept(updated -> {
                        if (updated.isEmpty() || updated.get().status() != QueueStatus.MATCH_FOUND) {
                            throw new IllegalStateException(
                                    "Assignment was not confirmed for " + playerId);
                        }
                    });
        });
    }

    @Override
    public Optional<MatchAssignment> currentAssignment() {
        return cloud.currentAssignment().map(assignment -> {
            Map<UUID, String> partyIds = new HashMap<>();
            assignment.players().forEach(player -> {
                String group = player.partyId() == null
                        ? player.playerId()
                        : player.partyId();
                partyIds.put(UUID.fromString(player.playerId()), group);
            });
            return new MatchAssignment(
                    assignment.sessionId(),
                    MatchmakingQueue.fromGroupId(assignment.groupId()),
                    partyIds
            );
        });
    }

    public Optional<SessionAssignment> currentCloudAssignment() {
        return cloud.currentAssignment();
    }

    private QueueJoinResult toJoinResult(QueueResult result) {
        return new QueueJoinResult(result.entryId(), "SELECTED".equals(result.state()));
    }

    @Override
    public CompletableFuture<Void> reportGameStarting(String sessionId) {
        return cloud.reportGameStarting(sessionId);
    }

    @Override
    public CompletableFuture<Void> reportGameStarted(String sessionId) {
        return cloud.reportGameStarted(sessionId);
    }

    @Override
    public CompletableFuture<Void> reportPlayerEliminated(String sessionId, UUID playerId) {
        return cloud.reportPlayerEliminated(sessionId, playerId);
    }

    @Override
    public CompletableFuture<Void> reportGameCancelled(String sessionId, String reason) {
        return cloud.reportGameCancelled(sessionId, reason);
    }

    @Override
    public CompletableFuture<Void> reportGameFinished(String sessionId, Map<String, Object> results) {
        return cloud.reportGameFinished(sessionId, results);
    }
}
