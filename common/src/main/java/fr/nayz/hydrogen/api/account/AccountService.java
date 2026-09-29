package fr.nayz.hydrogen.api.account;

import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.UUID;

public interface AccountService {
    CompletableFuture<Optional<Account>> find(UUID playerId);

    CompletableFuture<Account> getOrCreate(UUID playerId);

    CompletableFuture<Account> setRank(UUID playerId, Rank rank);

    CompletableFuture<Account> setLevel(UUID playerId, int level);

    CompletableFuture<Account> setGameLevel(UUID playerId, String gameId, int level);

    CompletableFuture<Account> changeCoins(UUID playerId, long delta, UUID operationId);
}
