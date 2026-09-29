package fr.nayz.hydrogen.paper.matchmaking;

import fr.nayz.endercloud.core.http.EnderCloudException;
import fr.nayz.endercloud.core.model.SessionAssignment;
import fr.nayz.hydrogen.api.matchmaking.MatchmakingQueue;
import fr.nayz.hydrogen.api.matchmaking.QueueStatus;
import fr.nayz.hydrogen.api.party.Party;
import fr.nayz.hydrogen.api.party.PartyService;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import fr.nayz.hydrogen.core.HydrogenConfig;
import fr.nayz.hydrogen.core.database.MigrationRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class EnderCloudMatchmakingServiceTest {
    private static HydrogenConfig config;

    @BeforeAll
    static void prepare() throws Exception {
        assumeTrue(System.getenv("HYDROGEN_TEST_DB_HOST") != null
                && System.getenv("HYDROGEN_TEST_REDIS_HOST") != null,
                "Integration services are not configured");
        config = HydrogenConfig.from(System.getenv(), "HYDROGEN_TEST_");
        MigrationRunner.migrate(config);
    }

    @Test
    void anyMemberEnqueuesAndCancelsOneFrozenTicket() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID partyId = UUID.randomUUID();
        FakeCloud cloud = new FakeCloud();
        PartyService parties = new FakeParties(new Party(
                partyId, leader, List.of(leader, member), true, Instant.now().plusSeconds(3600)));

        try (CoreHydrogenApi core = new CoreHydrogenApi(config)) {
            MatchmakingService matchmaking = new EnderCloudMatchmakingService(
                    cloud, parties, core.queueStore());

            matchmaking.enqueueParty(MatchmakingQueue.SKYWARS_DUO, member).join();

            assertEquals("skywars-duo", cloud.request().groupId());
            assertEquals(partyId.toString(), cloud.request().partyId());
            assertEquals(2, cloud.request().players().size());
            assertEquals(QueueStatus.QUEUED,
                    core.queueStates().get(leader).join().orElseThrow().status());
            assertEquals(core.queueStates().get(leader).join().orElseThrow().ticketId(),
                    core.queueStates().get(member).join().orElseThrow().ticketId());

            assertTrue(matchmaking.leaveQueue(member).join());
            assertFalse(core.queueStates().get(leader).join().isPresent());
            assertFalse(core.queueStates().get(member).join().isPresent());
        }
    }

    @Test
    void enderCloudValidationFailureRemovesTheIntentAndReachesCaller() {
        UUID playerId = UUID.randomUUID();
        FakeCloud cloud = new FakeCloud();
        cloud.rejectWith(new EnderCloudException(400, "group is too large"));
        try (CoreHydrogenApi core = new CoreHydrogenApi(config)) {
            MatchmakingService matchmaking = new EnderCloudMatchmakingService(
                    cloud, new FakeParties(null), core.queueStore());

            assertThrows(Exception.class, () -> matchmaking.enqueueSolo(
                    MatchmakingQueue.SKYWARS_SOLO, playerId).join());
            assertTrue(core.queueStates().get(playerId).join().isEmpty());
        }
    }

    @Test
    void transportFailureKeepsTheIntentForArrivalOnAnotherPaper() {
        UUID playerId = UUID.randomUUID();
        FakeCloud unavailable = new FakeCloud();
        unavailable.rejectWith(new IllegalStateException("orchestrator unavailable"));
        try (CoreHydrogenApi first = new CoreHydrogenApi(config);
             CoreHydrogenApi second = new CoreHydrogenApi(config)) {
            MatchmakingService matchmaking = new EnderCloudMatchmakingService(
                    unavailable, new FakeParties(null), first.queueStore());

            assertThrows(Exception.class, () -> matchmaking.enqueueSolo(
                    MatchmakingQueue.SKYWARS_SOLO, playerId).join());
            assertEquals(QueueStatus.VERIFYING,
                    second.queueStates().get(playerId).join().orElseThrow().status());
            second.queueStore().clearOnDisconnect(playerId).join();
        }
    }

    @Test
    void assignedPlayerFinishesQueueBeforeArrivalReadWithoutAnotherEnqueue() {
        UUID playerId = UUID.randomUUID();
        FakeCloud cloud = new FakeCloud();
        try (CoreHydrogenApi core = new CoreHydrogenApi(config)) {
            EnderCloudMatchmakingService matchmaking = new EnderCloudMatchmakingService(
                    cloud, new FakeParties(null), core.queueStore());
            matchmaking.enqueueSolo(MatchmakingQueue.SKYWARS_SOLO, playerId).join();
            assertEquals(QueueStatus.QUEUED,
                    core.queueStates().get(playerId).join().orElseThrow().status());

            SessionAssignment assignment = assignment(playerId, "ender-ticket");
            matchmaking.confirmAssignedPlayer(assignment, playerId).join();
            matchmaking.confirmForArrival(playerId).join();

            assertEquals(QueueStatus.MATCH_FOUND,
                    core.queueStates().get(playerId).join().orElseThrow().status());
            assertEquals(1, cloud.enqueueCount());
            core.queueStore().clearOnDisconnect(playerId).join();
        }
    }

    @Test
    void unrelatedAssignmentDoesNotFinishAnotherTicket() {
        UUID playerId = UUID.randomUUID();
        FakeCloud cloud = new FakeCloud();
        try (CoreHydrogenApi core = new CoreHydrogenApi(config)) {
            EnderCloudMatchmakingService matchmaking = new EnderCloudMatchmakingService(
                    cloud, new FakeParties(null), core.queueStore());
            matchmaking.enqueueSolo(MatchmakingQueue.SKYWARS_SOLO, playerId).join();

            assertThrows(Exception.class, () -> matchmaking.confirmAssignedPlayer(
                    assignment(playerId, "different-ticket"), playerId).join());
            assertEquals(QueueStatus.QUEUED,
                    core.queueStates().get(playerId).join().orElseThrow().status());
            assertEquals(1, cloud.enqueueCount());
            core.queueStore().clearOnDisconnect(playerId).join();
        }
    }

    @Test
    void assignmentWithoutHydrogenTicketNeedsNoQueueConfirmation() {
        UUID playerId = UUID.randomUUID();
        FakeCloud cloud = new FakeCloud();
        try (CoreHydrogenApi core = new CoreHydrogenApi(config)) {
            EnderCloudMatchmakingService matchmaking = new EnderCloudMatchmakingService(
                    cloud, new FakeParties(null), core.queueStore());

            matchmaking.confirmAssignedPlayer(assignment(playerId, "external-ticket"), playerId).join();

            assertTrue(core.queueStates().get(playerId).join().isEmpty());
            assertEquals(0, cloud.enqueueCount());
        }
    }

    private SessionAssignment assignment(UUID playerId, String ticketId) {
        return new SessionAssignment(
                "session-1", "skywars-solo", null, 1, 1, 1,
                false, false, List.of(), List.of(), List.of(
                        new SessionAssignment.AssignedPlayer(
                                playerId.toString(), null, ticketId, null)));
    }
}
