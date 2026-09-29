package fr.nayz.hydrogen.velocity.matchmaking;

import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.velocitypowered.api.scheduler.ScheduledTask;
import com.velocitypowered.api.scheduler.Scheduler;
import fr.nayz.hydrogen.api.matchmaking.MatchmakingQueue;
import fr.nayz.hydrogen.core.CoreHydrogenApi;
import fr.nayz.hydrogen.core.HydrogenConfig;
import fr.nayz.hydrogen.core.database.MigrationRunner;
import fr.nayz.hydrogen.core.matchmaking.QueueLease;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

final class VelocityQueueMessengerIntegrationTest {
    private static HydrogenConfig config;

    @BeforeAll
    static void prepare() throws Exception {
        assumeTrue(System.getenv("HYDROGEN_TEST_DB_HOST") != null
                && System.getenv("HYDROGEN_TEST_REDIS_HOST") != null);
        config = HydrogenConfig.from(System.getenv(), "HYDROGEN_TEST_");
        MigrationRunner.migrate(config);
    }

    @Test
    void delayedServerCheckSeesFoundMatchEvenWithoutRedisNotification() throws Exception {
        UUID playerId = UUID.randomUUID();
        ConcurrentLinkedQueue<Runnable> scheduled = new ConcurrentLinkedQueue<>();
        List<String> messages = new CopyOnWriteArrayList<>();

        try (CoreHydrogenApi core = new CoreHydrogenApi(config)) {
            createQueuedTicket(core, playerId);
            VelocityQueueMessenger messenger = new VelocityQueueMessenger(
                    proxy(scheduled), core, LoggerFactory.getLogger(getClass()), new Object());
            messenger.serverConnected(player(playerId, messages, "skywars-1"), "skywars-1");

            UUID ticketId = core.queueStates().get(playerId).join().orElseThrow().ticketId();
            core.queueStore().markFound(ticketId, "ender-ticket").join();
            scheduled.remove().run();

            awaitMessage(messages, "Partie trouvée");
            assertFalse(messages.stream().anyMatch(message -> message.contains("toujours dans la file")));
            messenger.close();
            core.queueStore().clearOnDisconnect(playerId).join();
        }
    }

    @Test
    void delayedServerCheckKeepsNoticeForARealQueue() throws Exception {
        UUID playerId = UUID.randomUUID();
        ConcurrentLinkedQueue<Runnable> scheduled = new ConcurrentLinkedQueue<>();
        List<String> messages = new CopyOnWriteArrayList<>();

        try (CoreHydrogenApi core = new CoreHydrogenApi(config)) {
            createQueuedTicket(core, playerId);
            VelocityQueueMessenger messenger = new VelocityQueueMessenger(
                    proxy(scheduled), core, LoggerFactory.getLogger(getClass()), new Object());
            messenger.serverConnected(player(playerId, messages, "hub-2"), "hub-2");
            scheduled.remove().run();

            awaitMessage(messages, "toujours dans la file");
            assertEquals(1, messages.size());
            messenger.close();
            core.queueStore().clearOnDisconnect(playerId).join();
        }
    }

    @Test
    void supersededServerCheckDoesNotSendAnOldNotice() throws Exception {
        UUID playerId = UUID.randomUUID();
        ConcurrentLinkedQueue<Runnable> scheduled = new ConcurrentLinkedQueue<>();
        List<String> messages = new CopyOnWriteArrayList<>();

        try (CoreHydrogenApi core = new CoreHydrogenApi(config)) {
            createQueuedTicket(core, playerId);
            VelocityQueueMessenger messenger = new VelocityQueueMessenger(
                    proxy(scheduled), core, LoggerFactory.getLogger(getClass()), new Object());
            Player player = player(playerId, messages, "hub-2");
            messenger.serverConnected(player, "hub-2");
            messenger.serverConnected(player, "hub-2");

            scheduled.remove().run();
            assertTrue(messages.isEmpty());
            scheduled.remove().run();
            awaitMessage(messages, "toujours dans la file");

            assertEquals(1, messages.size());
            messenger.close();
            core.queueStore().clearOnDisconnect(playerId).join();
        }
    }

    private void createQueuedTicket(CoreHydrogenApi core, UUID playerId) {
        UUID ticketId = core.queueStore().createOrFind(
                MatchmakingQueue.SKYWARS_SOLO, playerId.toString(), List.of(playerId))
                .join().ticketId();
        QueueLease lease = core.queueStore().claimConfirmation(ticketId).join().orElseThrow();
        core.queueStore().confirm(lease, "ender-ticket", false).join();
    }

    private ProxyServer proxy(ConcurrentLinkedQueue<Runnable> scheduled) {
        Scheduler scheduler = fake(Scheduler.class, (instance, method, arguments) -> {
            if (method.getName().equals("buildTask")) {
                Runnable task = (Runnable) arguments[1];
                return fake(Scheduler.TaskBuilder.class, (builder, operation, values) -> {
                    if (operation.getName().equals("schedule")) {
                        scheduled.add(task);
                        return fake(ScheduledTask.class, (scheduledTask, call, input) -> null);
                    }
                    return builder;
                });
            }
            throw new UnsupportedOperationException(method.getName());
        });
        return fake(ProxyServer.class, (instance, method, arguments) -> {
            if (method.getName().equals("getScheduler")) {
                return scheduler;
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private Player player(UUID playerId, List<String> messages, String serverName) {
        ServerInfo info = new ServerInfo(serverName, new InetSocketAddress("localhost", 25565));
        RegisteredServer server = fake(RegisteredServer.class, (instance, method, arguments) -> {
            if (method.getName().equals("getServerInfo")) {
                return info;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        ServerConnection connection = fake(ServerConnection.class, (instance, method, arguments) -> {
            if (method.getName().equals("getServer")) {
                return server;
            }
            throw new UnsupportedOperationException(method.getName());
        });
        return fake(Player.class, (instance, method, arguments) -> {
            return switch (method.getName()) {
                case "getUniqueId" -> playerId;
                case "isActive" -> true;
                case "getCurrentServer" -> Optional.of(connection);
                case "sendMessage" -> {
                    Component message = (Component) arguments[arguments.length - 1];
                    messages.add(PlainTextComponentSerializer.plainText().serialize(message));
                    yield null;
                }
                default -> throw new UnsupportedOperationException(method.getName());
            };
        });
    }

    private void awaitMessage(List<String> messages, String expected) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (System.nanoTime() < deadline) {
            if (messages.stream().anyMatch(message -> message.contains(expected))) {
                return;
            }
            Thread.sleep(20);
        }
        assertTrue(messages.stream().anyMatch(message -> message.contains(expected)), messages.toString());
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, InvocationHandler handler) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
