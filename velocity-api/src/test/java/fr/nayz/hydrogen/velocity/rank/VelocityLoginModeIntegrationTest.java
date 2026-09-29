package fr.nayz.hydrogen.velocity.rank;

import com.velocitypowered.api.event.Continuation;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.connection.LoginEvent;
import com.velocitypowered.api.proxy.Player;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import fr.nayz.hydrogen.core.HydrogenConfig;
import fr.nayz.hydrogen.core.database.MigrationRunner;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class VelocityLoginModeIntegrationTest {
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
    void acceptsTheUuidFromVelocityInBothModes() throws Exception {
        try (CoreHydrogenApi core = new CoreHydrogenApi(config)) {
            VelocityRankManager ranks = new VelocityRankManager(
                    null, core, LoggerFactory.getLogger(getClass()));
            verifyLogin(ranks, core, false);
            verifyLogin(ranks, core, true);
        }
    }

    private void verifyLogin(VelocityRankManager ranks, CoreHydrogenApi core, boolean onlineMode)
            throws Exception {
        UUID playerId = UUID.randomUUID();
        Player player = (Player) Proxy.newProxyInstance(
                Player.class.getClassLoader(),
                new Class<?>[]{Player.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getUniqueId" -> playerId;
                    case "isOnlineMode" -> onlineMode;
                    default -> throw new UnsupportedOperationException(method.getName());
                }
        );
        LoginEvent event = new LoginEvent(player, "test-server");
        EventTask task = ranks.checkLogin(event);
        CompletableFuture<Void> completed = new CompletableFuture<>();
        task.execute(new Continuation() {
            @Override
            public void resume() {
                completed.complete(null);
            }

            @Override
            public void resumeWithException(Throwable error) {
                completed.completeExceptionally(error);
            }
        });

        completed.get(5, TimeUnit.SECONDS);
        assertTrue(event.getResult().isAllowed());
        assertTrue(core.accounts().find(playerId).join().isPresent());
    }
}
