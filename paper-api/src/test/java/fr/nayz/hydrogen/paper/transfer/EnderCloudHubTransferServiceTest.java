package fr.nayz.hydrogen.paper.transfer;

import fr.nayz.hydrogen.paper.matchmaking.FakeCloud;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class EnderCloudHubTransferServiceTest {
    @Test
    void mapsAcceptedAndRejectedPlayers() {
        UUID accepted = UUID.randomUUID();
        UUID rejected = UUID.randomUUID();
        HubTransferService transfers = new EnderCloudHubTransferService(new FakeCloud());

        HubTransferResult result = transfers.sendToHub(List.of(accepted, rejected)).join();

        assertEquals(List.of(accepted), result.acceptedPlayers());
        assertEquals(List.of(rejected), result.rejectedPlayers());
        assertTrue(transfers.sendToHub(accepted).join());
    }

}
