package fr.nayz.hydrogen.core.redis;

import fr.nayz.hydrogen.api.presence.Presence;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import fr.nayz.hydrogen.core.HydrogenConfig;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class RedisReconnectIntegrationTest {
    @Test
    void subscriptionAndPresenceRecoverAfterRedisRestart() throws Exception {
        assumeTrue("true".equals(System.getenv("HYDROGEN_RECONNECT_TEST")));
        String container = System.getenv("HYDROGEN_REDIS_CONTAINER");
        assumeTrue(container != null && !container.isBlank());
        HydrogenConfig config = HydrogenConfig.from(System.getenv(), "HYDROGEN_TEST_");
        UUID player = UUID.randomUUID();
        UUID session = UUID.randomUUID();
        try (CoreHydrogenApi first = new CoreHydrogenApi(config);
             CoreHydrogenApi second = new CoreHydrogenApi(config)) {
            CountDownLatch event = new CountDownLatch(1);
            CountDownLatch socialEvent = new CountDownLatch(1);
            UUID friend = UUID.randomUUID();
            second.events().onRankChange(value -> {
                if (player.toString().equals(value)) {
                    event.countDown();
                }
            });
            second.events().onSocialEvent(value -> {
                if (value.kind().equals("friend-request") && value.target().equals(friend)) {
                    socialEvent.countDown();
                }
            });
            Process restart = new ProcessBuilder("docker", "restart", container)
                    .redirectErrorStream(true)
                    .start();
            assertEquals(0, restart.waitFor());

            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            boolean recovered = false;
            while (System.nanoTime() < deadline) {
                try {
                    first.presence().claim(player, "proxy-a", "hub", session).join();
                    second.presence().get(player).join().orElseThrow();
                    recovered = true;
                    break;
                } catch (RuntimeException exception) {
                    Thread.sleep(250);
                }
            }
            assertTrue(recovered, "Redis did not reconnect");
            while (event.getCount() > 0 && System.nanoTime() < deadline) {
                first.accounts().setRank(player, fr.nayz.hydrogen.api.account.Rank.VIP).join();
                event.await(500, TimeUnit.MILLISECONDS);
            }
            assertEquals(0, event.getCount(), "Rank subscription did not recover");
            first.friends().request(player, friend).join();
            assertTrue(socialEvent.await(5, TimeUnit.SECONDS), "Social subscription did not recover");
            assertEquals(player, second.friends().pending(friend).join().getFirst().sender());
        }
    }
}
