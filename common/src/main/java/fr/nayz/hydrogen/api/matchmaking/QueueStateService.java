package fr.nayz.hydrogen.api.matchmaking;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface QueueStateService {
    CompletableFuture<Optional<QueueState>> get(UUID playerId);

    CompletableFuture<Map<UUID, QueueState>> getMany(Collection<UUID> playerIds);
}
