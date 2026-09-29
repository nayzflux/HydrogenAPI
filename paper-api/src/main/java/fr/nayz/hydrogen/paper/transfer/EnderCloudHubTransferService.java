package fr.nayz.hydrogen.paper.transfer;

import fr.nayz.endercloud.core.api.EnderCloudPaperApi;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class EnderCloudHubTransferService implements HubTransferService {
    private final EnderCloudPaperApi cloud;

    public EnderCloudHubTransferService(EnderCloudPaperApi cloud) {
        this.cloud = cloud;
    }

    @Override
    public CompletableFuture<Boolean> sendToHub(UUID playerId) {
        return cloud.sendToHub(playerId);
    }

    @Override
    public CompletableFuture<HubTransferResult> sendToHub(Collection<UUID> playerIds) {
        return cloud.sendToHub(playerIds).thenApply(result ->
                new HubTransferResult(result.acceptedPlayers(), result.rejectedPlayers()));
    }
}
