package fr.nayz.hydrogen.paper.transfer;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface HubTransferService {
    CompletableFuture<Boolean> sendToHub(UUID playerId);

    CompletableFuture<HubTransferResult> sendToHub(Collection<UUID> playerIds);
}
