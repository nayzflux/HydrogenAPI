package fr.nayz.hydrogen.api.presence;

import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.UUID;

public interface PresenceService {
    CompletableFuture<Optional<Presence>> get(UUID playerId);

    CompletableFuture<Void> claim(UUID playerId, String proxyId, String serverId, UUID sessionId);

    CompletableFuture<Void> update(UUID playerId, String proxyId, String serverId, UUID sessionId);

    CompletableFuture<Void> clear(UUID playerId, UUID sessionId);
}
