package fr.nayz.hydrogen.core.integration;

import fr.nayz.hydrogen.api.matchmaking.MatchmakingQueue;
import fr.nayz.hydrogen.api.matchmaking.QueueState;
import fr.nayz.hydrogen.api.matchmaking.QueueStatus;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import fr.nayz.hydrogen.core.HydrogenConfig;
import fr.nayz.hydrogen.core.database.MigrationRunner;
import fr.nayz.hydrogen.core.matchmaking.JdbcQueueStateService;
import fr.nayz.hydrogen.core.matchmaking.QueueLease;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class QueueStateIntegrationTest {
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
    void concurrentMembersShareOneTicketAndAnyMemberCanLeave() {
        UUID firstMember = UUID.randomUUID();
        UUID secondMember = UUID.randomUUID();
        String partyId = UUID.randomUUID().toString();
        List<UUID> members = List.of(firstMember, secondMember);
        try (CoreHydrogenApi first = new CoreHydrogenApi(config);
             CoreHydrogenApi second = new CoreHydrogenApi(config)) {
            CompletableFuture<QueueState> firstEntry = first.queueStore().createOrFind(
                    MatchmakingQueue.SKYWARS_DUO, partyId, members);
            CompletableFuture<QueueState> secondEntry = second.queueStore().createOrFind(
                    MatchmakingQueue.SKYWARS_DUO, partyId, members);
            QueueState ticket = firstEntry.join();
            assertEquals(ticket.ticketId(), secondEntry.join().ticketId());

            QueueLease lease = first.queueStore().claimConfirmation(ticket.ticketId()).join().orElseThrow();
            assertTrue(second.queueStore().claimConfirmation(ticket.ticketId()).join().isEmpty());
            first.queueStore().confirm(lease, "cloud-entry", false).join();
            assertEquals("cloud-entry", second.queueStates().get(secondMember).join()
                    .orElseThrow().enderCloudEntryId());
            assertEquals(2, second.queueStates().getMany(members).join().size());

            QueueLease leaving = second.queueStore().claimLeave(secondMember).join().orElseThrow();
            assertEquals(QueueStatus.LEAVING,
                    first.queueStates().get(firstMember).join().orElseThrow().status());
            second.queueStore().finishLeave(leaving, false).join();
            assertEquals(QueueStatus.VERIFYING,
                    first.queueStates().get(firstMember).join().orElseThrow().status());
            QueueLease accepted = first.queueStore().claimLeave(firstMember).join().orElseThrow();
            first.queueStore().finishLeave(accepted, true).join();
            assertFalse(second.queueStates().get(secondMember).join().isPresent());
        }
    }

    @Test
    void retryAfterInterruptedConfirmationPreservesOrResetsWaitingTime() throws Exception {
        UUID playerId = UUID.randomUUID();
        try (CoreHydrogenApi first = new CoreHydrogenApi(config);
             CoreHydrogenApi second = new CoreHydrogenApi(config)) {
            JdbcQueueStateService original = first.queueStore();
            QueueState ticket = original.createOrFind(
                    MatchmakingQueue.SKYWARS_SOLO, playerId.toString(), List.of(playerId)).join();
            original.claimConfirmation(ticket.ticketId()).join().orElseThrow();

            expireLeaseAndConfirmation(ticket.ticketId());
            QueueLease retried = second.queueStore().claimConfirmation(ticket.ticketId()).join().orElseThrow();
            QueueState firstConfirmation = second.queueStore().confirm(retried, "first-entry", false).join();
            assertEquals(ticket.enteredAt(), firstConfirmation.enteredAt());

            expireLeaseAndConfirmation(ticket.ticketId());
            QueueLease sameTicket = original.claimConfirmation(ticket.ticketId()).join().orElseThrow();
            QueueState confirmedAgain = original.confirm(sameTicket, "first-entry", false).join();
            assertEquals(firstConfirmation.enteredAt(), confirmedAgain.enteredAt());

            expireLeaseAndConfirmation(ticket.ticketId());
            QueueLease replacement = second.queueStore().claimConfirmation(ticket.ticketId()).join().orElseThrow();
            QueueState replaced = second.queueStore().confirm(replacement, "new-entry", false).join();
            assertNotEquals(firstConfirmation.enteredAt(), replaced.enteredAt());
            assertEquals("new-entry", first.queueStates().get(playerId).join()
                    .orElseThrow().enderCloudEntryId());
            second.queueStore().markFound(ticket.ticketId(), "new-entry").join();
            assertTrue(first.queueStore().claimFoundNotice(ticket.ticketId(), playerId).join());
            assertFalse(second.queueStore().claimFoundNotice(ticket.ticketId(), playerId).join());
            second.queueStore().clearOnDisconnect(playerId).join();
        }
    }

    @Test
    void oldProxySessionCannotClaimTheNewSessionDisconnect() {
        UUID playerId = UUID.randomUUID();
        UUID oldSession = UUID.randomUUID();
        UUID newSession = UUID.randomUUID();
        try (CoreHydrogenApi first = new CoreHydrogenApi(config)) {
            first.presence().claim(playerId, "proxy-a", "hub", oldSession).join();
            first.presence().claim(playerId, "proxy-b", "hub", newSession).join();
            assertFalse(first.events().clearPresenceIfOwned(playerId, oldSession).join());
            assertTrue(first.events().clearPresenceIfOwned(playerId, newSession).join());
        }
    }

    private static void expireLeaseAndConfirmation(UUID ticketId) throws Exception {
        try (Connection connection = TestDatabaseConnection.open(config);
             PreparedStatement update = connection.prepareStatement(
                     "UPDATE hydrogen_queue_ticket SET lease_until = now() - interval '1 second', "
                             + "confirmation_due_at = now() - interval '1 second' WHERE ticket_id = ?")) {
            update.setObject(1, ticketId);
            update.executeUpdate();
        }
    }
}
