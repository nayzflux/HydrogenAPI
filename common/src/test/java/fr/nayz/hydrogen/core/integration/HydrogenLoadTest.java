package fr.nayz.hydrogen.core.integration;

import fr.nayz.hydrogen.api.account.Account;
import fr.nayz.hydrogen.api.presence.Presence;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import fr.nayz.hydrogen.core.database.MigrationRunner;
import fr.nayz.hydrogen.core.HydrogenConfig;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class HydrogenLoadTest {
    @Test
    void twoThousandPlayersAcrossOneHundredSixtySevenServers() throws Exception {
        assumeTrue("true".equals(System.getenv("HYDROGEN_LOAD_TEST")));
        HydrogenConfig config = HydrogenConfig.from(System.getenv(), "HYDROGEN_TEST_");
        MigrationRunner.migrate(config);
        List<UUID> players = new ArrayList<>();
        List<CompletableFuture<?>> writes = new ArrayList<>();
        long started = System.nanoTime();
        try (CoreHydrogenApi first = new CoreHydrogenApi(config);
             CoreHydrogenApi second = new CoreHydrogenApi(config)) {
            for (int index = 0; index < 2_000; index++) {
                UUID player = UUID.randomUUID();
                players.add(player);
                String server = "arena-" + (index % 167);
                CoreHydrogenApi owner = index % 2 == 0 ? first : second;
                writes.add(owner.accounts().getOrCreate(player));
                writes.add(owner.presence().claim(player, "proxy-" + (index % 2), server,
                        UUID.randomUUID()));
            }
            CompletableFuture.allOf(writes.toArray(CompletableFuture[]::new)).join();
            List<CompletableFuture<?>> reads = new ArrayList<>();
            for (UUID player : players) {
                reads.add(second.accounts().getOrCreate(player).thenAccept(account ->
                        assertEquals(player, account.playerId())));
                reads.add(first.presence().get(player).thenAccept(presence ->
                        assertEquals(player, presence.orElseThrow().playerId())));
            }
            CompletableFuture.allOf(reads.toArray(CompletableFuture[]::new)).join();
            long elapsedMillis = (System.nanoTime() - started) / 1_000_000;
            System.out.println("Hydrogen 2,000 player synthetic check completed in " + elapsedMillis + " ms");
        }
    }
}
