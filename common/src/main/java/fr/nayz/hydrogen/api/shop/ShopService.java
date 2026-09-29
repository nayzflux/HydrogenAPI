package fr.nayz.hydrogen.api.shop;

import java.util.concurrent.CompletableFuture;
import java.util.UUID;

public interface ShopService {
    void register(Offer offer);

    CompletableFuture<Purchase> purchase(UUID playerId, String gameId, String offerId, UUID operationId);
}
