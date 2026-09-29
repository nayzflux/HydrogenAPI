package fr.nayz.hydrogen.api.friend;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.UUID;

public interface FriendService {
    CompletableFuture<Void> request(UUID sender, UUID recipient);

    CompletableFuture<Void> accept(UUID recipient, UUID sender);

    CompletableFuture<Void> decline(UUID recipient, UUID sender);

    CompletableFuture<Void> remove(UUID playerId, UUID friendId);

    CompletableFuture<List<UUID>> list(UUID playerId);

    CompletableFuture<List<FriendRequest>> pending(UUID playerId);
}
