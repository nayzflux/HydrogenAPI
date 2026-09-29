package fr.nayz.hydrogen.api.inventory;

import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.UUID;

public interface InventoryService {
    CompletableFuture<List<Entitlement>> list(UUID playerId, String gameId);

    CompletableFuture<Entitlement> grant(UUID playerId, String gameId, String itemId, int quantity, UUID operationId);
}
