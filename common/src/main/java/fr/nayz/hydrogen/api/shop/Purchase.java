package fr.nayz.hydrogen.api.shop;

import java.util.UUID;

public record Purchase(UUID operationId, UUID playerId, String gameId, String offerId, long balanceAfter) {
}
