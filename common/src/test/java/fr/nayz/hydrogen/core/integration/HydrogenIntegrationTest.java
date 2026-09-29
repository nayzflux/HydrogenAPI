package fr.nayz.hydrogen.core.integration;

import fr.nayz.hydrogen.api.account.Account;
import fr.nayz.hydrogen.api.account.Rank;
import fr.nayz.hydrogen.api.HydrogenException;
import fr.nayz.hydrogen.api.party.Party;
import fr.nayz.hydrogen.api.presence.Presence;
import fr.nayz.hydrogen.api.shop.Offer;
import fr.nayz.hydrogen.api.shop.Purchase;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import fr.nayz.hydrogen.core.database.MigrationRunner;
import fr.nayz.hydrogen.core.HydrogenConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class HydrogenIntegrationTest {
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
    void concurrentPurchasesDebitOnceAndGrantOnce() {
        UUID player = UUID.randomUUID();
        UUID credit = UUID.randomUUID();
        try (CoreHydrogenApi first = new CoreHydrogenApi(config);
             CoreHydrogenApi second = new CoreHydrogenApi(config)) {
            Account funded = first.accounts().changeCoins(player, 100, credit).join();
            assertEquals(100, funded.coins());
            assertEquals(100, second.accounts().changeCoins(player, 100, credit).join().coins());

            Offer offer = new Offer("arena", "kit", 80, "kit-gold", 1, false);
            first.shop().register(offer);
            second.shop().register(offer);
            UUID operationA = UUID.randomUUID();
            UUID operationB = UUID.randomUUID();
            CompletableFuture<Purchase> purchaseA = first.shop().purchase(
                    player, "arena", "kit", operationA);
            CompletableFuture<Purchase> purchaseB = second.shop().purchase(
                    player, "arena", "kit", operationB);
            int successes = 0;
            Purchase completed = null;
            for (CompletableFuture<Purchase> future : List.of(purchaseA, purchaseB)) {
                try {
                    completed = future.join();
                    successes++;
                } catch (Exception exception) {
                    assertTrue(exception.getCause() instanceof HydrogenException);
                }
            }
            assertEquals(1, successes);
            assertEquals(20, second.shop().purchase(player, "arena", "kit",
                    completed.operationId()).join().balanceAfter());
            assertEquals(20, first.accounts().getOrCreate(player).join().coins());
            assertEquals(1, first.inventory().list(player, "arena").join().getFirst().quantity());
        }
    }

    @Test
    void friendshipAndPartySurviveAnotherProcessButExpire() throws Exception {
        UUID leader = UUID.randomUUID();
        UUID member = UUID.randomUUID();
        try (CoreHydrogenApi first = new CoreHydrogenApi(config);
             CoreHydrogenApi second = new CoreHydrogenApi(config)) {
            first.friends().request(leader, member).join();
            assertThrows(Exception.class, () -> second.friends().request(member, leader).join());
            second.friends().accept(member, leader).join();
            assertEquals(member, first.friends().list(leader).join().getFirst());

            Party party = first.parties().create(leader).join();
            first.parties().invite(leader, member).join();
            assertEquals(party.id(), second.parties().invitations(member).join().getFirst().partyId());
            assertEquals(2, second.parties().accept(member, party.id()).join().members().size());
            assertEquals(party.id(), second.parties().getForPlayer(leader).join().orElseThrow().id());

            try (Connection connection = TestDatabaseConnection.open(config);
                 PreparedStatement statement = connection.prepareStatement(
                         "UPDATE hydrogen_parties SET expires_at = now() - interval '1 second' WHERE party_id = ?")) {
                statement.setObject(1, party.id());
                statement.executeUpdate();
            }
            assertTrue(first.parties().getForPlayer(leader).join().isEmpty());
            assertEquals(1, second.parties().create(leader).join().members().size());
        }
    }

    @Test
    void presenceOwnerAndRankEventsWorkAcrossInstances() throws Exception {
        UUID player = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        try (CoreHydrogenApi first = new CoreHydrogenApi(config);
             CoreHydrogenApi second = new CoreHydrogenApi(config)) {
            CountDownLatch rankEvent = new CountDownLatch(1);
            second.events().onRankChange(value -> {
                if (player.toString().equals(value)) {
                    rankEvent.countDown();
                }
            });
            first.presence().claim(player, "proxy-a", "hub", session).join();
            first.presence().update(player, "proxy-a", "arena-1", session).join();
            second.presence().clear(player, UUID.randomUUID()).join();
            assertEquals("arena-1", second.presence().get(player).join().orElseThrow().serverId());
            first.accounts().setRank(player, Rank.VIP).join();
            assertTrue(rankEvent.await(5, TimeUnit.SECONDS));
            assertEquals(Rank.VIP, second.accounts().getOrCreate(player).join().rank());
            assertEquals(Rank.VIP, second.rankReader().getMany(List.of(player)).join().get(player));
            second.presence().clear(player, session).join();
            assertFalse(first.presence().get(player).join().isPresent());
        }
    }

    @Test
    void adminInspectionDoesNotCreateAnAccount() {
        UUID unknown = UUID.randomUUID();
        UUID firstPlayer = UUID.randomUUID();
        UUID secondPlayer = UUID.randomUUID();
        String name = "Debug" + UUID.randomUUID().toString().substring(0, 8);
        try (CoreHydrogenApi first = new CoreHydrogenApi(config);
             CoreHydrogenApi second = new CoreHydrogenApi(config)) {
            assertTrue(first.accounts().find(unknown).join().isEmpty());
            assertTrue(second.accounts().find(unknown).join().isEmpty());

            first.directory().remember(firstPlayer, name).join();
            second.directory().remember(secondPlayer, name.toLowerCase(java.util.Locale.ROOT)).join();
            assertEquals(2, first.directory().knownByName(
                    name.toUpperCase(java.util.Locale.ROOT)).join().size());

            second.accounts().setRank(firstPlayer, Rank.ADMIN).join();
            assertEquals(Rank.ADMIN, first.accounts().find(firstPlayer).join().orElseThrow().rank());
            assertEquals(Rank.PLAYER, first.accounts().find(secondPlayer).join().orElseThrow().rank());
        }
    }
}
