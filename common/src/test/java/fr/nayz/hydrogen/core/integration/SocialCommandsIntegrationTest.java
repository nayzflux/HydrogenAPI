package fr.nayz.hydrogen.core.integration;

import fr.nayz.hydrogen.api.party.Party;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import fr.nayz.hydrogen.core.HydrogenConfig;
import fr.nayz.hydrogen.core.database.MigrationRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class SocialCommandsIntegrationTest {
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
    void namesAndNotificationsWorkAcrossTwoApiInstances() throws Exception {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        UUID aliceSession = UUID.randomUUID();
        UUID bobSession = UUID.randomUUID();
        try (CoreHydrogenApi first = new CoreHydrogenApi(config);
             CoreHydrogenApi second = new CoreHydrogenApi(config)) {
            CountDownLatch friendEvent = new CountDownLatch(1);
            CountDownLatch partyEvent = new CountDownLatch(1);
            second.events().onSocialEvent(event -> {
                if (event.kind().equals("friend-request") && event.target().equals(bob)) {
                    friendEvent.countDown();
                }
                if (event.kind().equals("party-invite") && event.target().equals(bob)) {
                    partyEvent.countDown();
                }
            });
            first.directory().remember(alice, "Alice_One").join();
            second.directory().remember(bob, "Bob_Two").join();
            first.presence().claim(alice, "proxy-a", "hub", aliceSession).join();
            second.presence().claim(bob, "proxy-b", "hub", bobSession).join();
            first.directory().claim(alice, "Alice_One", aliceSession).join();
            second.directory().claim(bob, "Bob_Two", bobSession).join();

            assertEquals(bob, first.directory().online("bOb_TwO").join().orElseThrow());
            assertEquals("Alice_One", second.directory().names(java.util.List.of(alice))
                    .join().get(alice));
            first.friends().request(alice, bob).join();
            assertTrue(friendEvent.await(5, TimeUnit.SECONDS));
            first.parties().create(alice).join();
            first.parties().invite(alice, bob).join();
            assertTrue(partyEvent.await(5, TimeUnit.SECONDS));
            assertEquals(alice, second.parties().invitations(bob).join().getFirst().inviter());

            UUID newSession = UUID.randomUUID();
            second.presence().claim(bob, "proxy-b", "hub", newSession).join();
            second.directory().claim(bob, "Bob_Two", newSession).join();
            first.directory().clear(bob, "Bob_Two", bobSession).join();
            assertEquals(bob, first.directory().online("Bob_Two").join().orElseThrow());
        }
    }

    @Test
    void memberPermissionDeclineAndInviterDepartureRemainConsistent() {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        UUID invitee = UUID.randomUUID();
        try (CoreHydrogenApi first = new CoreHydrogenApi(config);
             CoreHydrogenApi second = new CoreHydrogenApi(config)) {
            Party party = first.parties().create(leader).join();
            first.parties().invite(leader, member).join();
            second.parties().accept(member, party.id(), leader).join();
            assertFalse(first.parties().setMembersCanInvite(leader, false).join().membersCanInvite());
            assertThrows(Exception.class, () -> second.parties().invite(member, invitee).join());
            assertTrue(first.parties().setMembersCanInvite(leader, true).join().membersCanInvite());
            second.parties().invite(member, invitee).join();
            assertEquals(member, first.parties().invitations(invitee).join().getFirst().inviter());
            second.parties().leave(member).join();
            assertEquals(party.id(), first.parties().invitations(invitee).join().getFirst().partyId());
            first.parties().decline(invitee, party.id(), member).join();
            assertTrue(first.parties().invitations(invitee).join().isEmpty());
        }
    }

    @Test
    void invitationCooldownProgressesOnlyAfterCommittedInvitations() throws Exception {
        UUID leader = UUID.randomUUID();
        UUID invitee = UUID.randomUUID();
        try (CoreHydrogenApi first = new CoreHydrogenApi(config);
             CoreHydrogenApi second = new CoreHydrogenApi(config)) {
            first.parties().create(leader).join();
            first.parties().invite(leader, invitee).join();
            assertThrows(Exception.class, () -> second.parties().invite(leader, invitee).join());
            shiftLastSent(leader, invitee, 3);
            second.parties().invite(leader, invitee).join();
            assertEquals(1, first.parties().invitations(invitee).join().size());
            shiftLastSent(leader, invitee, 3);
            assertThrows(Exception.class, () -> first.parties().invite(leader, invitee).join());
            shiftLastSent(leader, invitee, 6);
            first.parties().invite(leader, invitee).join();
            shiftLastSent(leader, invitee, 6);
            assertThrows(Exception.class, () -> second.parties().invite(leader, invitee).join());
            shiftLastSent(leader, invitee, 11);
            second.parties().invite(leader, invitee).join();
            shiftLastSent(leader, invitee, 11);
            assertThrows(Exception.class, () -> first.parties().invite(leader, invitee).join());
            shiftLastSent(leader, invitee, 31);
            first.parties().invite(leader, invitee).join();
            shiftLastSent(leader, invitee, 601);
            first.parties().invite(leader, invitee).join();
            assertEquals(1, first.parties().invitations(invitee).join().size());
        }
    }

    @Test
    void concurrentSocialWritesKeepOnePendingRequestAndInvitation() {
        UUID leader = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        try (CoreHydrogenApi first = new CoreHydrogenApi(config);
             CoreHydrogenApi second = new CoreHydrogenApi(config)) {
            CompletableFuture<Void> firstRequest = first.friends().request(leader, target);
            CompletableFuture<Void> secondRequest = second.friends().request(target, leader);
            int successfulRequests = 0;
            for (CompletableFuture<Void> request : java.util.List.of(firstRequest, secondRequest)) {
                try {
                    request.join();
                    successfulRequests++;
                } catch (RuntimeException ignored) {
                    // The unique friendship rejects the competing request.
                }
            }
            assertEquals(1, successfulRequests);
            assertEquals(1, first.friends().pending(leader).join().size());

            first.parties().create(leader).join();
            CompletableFuture<?> firstInvite = first.parties().invite(leader, target);
            CompletableFuture<?> secondInvite = second.parties().invite(leader, target);
            int successfulInvites = 0;
            for (CompletableFuture<?> invite : java.util.List.of(firstInvite, secondInvite)) {
                try {
                    invite.join();
                    successfulInvites++;
                } catch (RuntimeException ignored) {
                    // The cooldown rejects the competing invitation.
                }
            }
            assertEquals(1, successfulInvites);
            assertEquals(1, second.parties().invitations(target).join().size());
        }
    }

    private static void shiftLastSent(UUID inviter, UUID invitee, int seconds) throws Exception {
        try (Connection connection = TestDatabaseConnection.open(config);
             PreparedStatement statement = connection.prepareStatement(
                     "UPDATE hydrogen_party_invite_limits SET last_sent_at = now() - (? * interval '1 second') "
                             + "WHERE inviter_id = ? AND invitee_id = ?")) {
            statement.setInt(1, seconds);
            statement.setObject(2, inviter);
            statement.setObject(3, invitee);
            assertEquals(1, statement.executeUpdate());
        }
    }
}
