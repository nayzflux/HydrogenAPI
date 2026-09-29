package fr.nayz.hydrogen.core.redis;

import fr.nayz.hydrogen.api.HydrogenErrorReason;
import fr.nayz.hydrogen.api.HydrogenException;
import fr.nayz.hydrogen.core.HydrogenConfig;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import io.lettuce.core.RedisClient;
import io.lettuce.core.ScriptOutputType;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class RedisCoordinator implements AutoCloseable {
    private static final String CHANNEL = "hydrogen:events";
    private static final Logger LOGGER = Logger.getLogger(RedisCoordinator.class.getName());
    private final RedisClient client;
    private final StatefulRedisConnection<String, String> commands;
    private final StatefulRedisPubSubConnection<String, String> subscription;
    private final ExecutorService executor;
    private final List<Consumer<String>> rankListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<SocialEvent>> socialListeners = new CopyOnWriteArrayList<>();
    private final List<Consumer<UUID>> queueListeners = new CopyOnWriteArrayList<>();

    public RedisCoordinator(HydrogenConfig config) {
        client = RedisClient.create(config.redisUri());
        StatefulRedisConnection<String, String> commandConnection = null;
        StatefulRedisPubSubConnection<String, String> eventConnection = null;
        try {
            commandConnection = client.connect();
            eventConnection = client.connectPubSub();
        } catch (RuntimeException exception) {
            if (eventConnection != null) {
                eventConnection.close();
            }
            if (commandConnection != null) {
                commandConnection.close();
            }
            client.shutdown();
            throw exception;
        }
        commands = commandConnection;
        subscription = eventConnection;
        subscription.addListener(new RankEventSubscriber(CHANNEL, rankListeners));
        subscription.addListener(new SocialEventSubscriber(CHANNEL, socialListeners));
        subscription.addListener(new QueueEventSubscriber(CHANNEL, queueListeners));
        subscription.sync().subscribe(CHANNEL);
        executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "hydrogen-redis");
            thread.setDaemon(true);
            return thread;
        });
    }

    public void onRankChange(Consumer<String> listener) {
        rankListeners.add(listener);
    }

    public void onSocialEvent(Consumer<SocialEvent> listener) {
        socialListeners.add(listener);
    }

    public void onQueueChange(Consumer<UUID> listener) {
        queueListeners.add(listener);
    }

    public void publishSocial(String kind, UUID target, UUID actor) {
        publish("social", kind + "|" + target + "|" + actor);
    }

    public void publish(String kind, String id) {
        run(() -> {
            commands.sync().publish(CHANNEL, kind + "|" + id);
            return null;
        }).exceptionally(error -> {
            LOGGER.log(Level.WARNING, "Hydrogen event publication failed", error);
            return null;
        });
    }

    public <T> CompletableFuture<T> run(Supplier<T> action) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return action.get();
            } catch (RuntimeException exception) {
                throw new HydrogenException(HydrogenErrorReason.UNAVAILABLE,
                        "Redis operation failed", exception);
            }
        }, executor);
    }

    public String get(String key) {
        return commands.sync().get(key);
    }

    public void set(String key, String value) {
        commands.sync().setex(key, 45, value);
    }

    public void updateIfOwned(String key, String sessionId, String value) {
        String script = "local old = redis.call('GET', KEYS[1]) "
                + "if old and string.sub(old, 1, 36) == ARGV[1] then "
                + "redis.call('SETEX', KEYS[1], 45, ARGV[2]) return 1 end return 0";
        commands.sync().eval(script, ScriptOutputType.INTEGER, new String[]{key}, sessionId, value);
    }

    public boolean deleteIfOwned(String key, String sessionId) {
        String script = "local old = redis.call('GET', KEYS[1]) "
                + "if old and string.sub(old, 1, 36) == ARGV[1] then "
                + "return redis.call('DEL', KEYS[1]) end return 0";
        Long removed = commands.sync().eval(
                script, ScriptOutputType.INTEGER, new String[]{key}, sessionId);
        return removed != null && removed == 1;
    }

    public CompletableFuture<Boolean> clearPresenceIfOwned(UUID playerId, UUID sessionId) {
        return run(() -> deleteIfOwned(
                "hydrogen:presence:" + playerId,
                sessionId.toString()));
    }

    public void claimName(String name, UUID playerId, UUID sessionId) {
        commands.sync().setex(nameKey(name), 45, sessionId + "|" + playerId);
    }

    public void renewName(String name, UUID playerId, UUID sessionId) {
        updateIfOwned(nameKey(name), sessionId.toString(), sessionId + "|" + playerId);
    }

    public void clearName(String name, UUID playerId, UUID sessionId) {
        deleteIfOwned(nameKey(name), sessionId.toString());
    }

    public Optional<UUID> onlineName(String name) {
        String value = get(nameKey(name));
        if (value == null) {
            return Optional.empty();
        }
        String[] fields = value.split("\\|", 2);
        if (fields.length != 2) {
            return Optional.empty();
        }
        UUID playerId = UUID.fromString(fields[1]);
        String presence = get("hydrogen:presence:" + playerId);
        if (presence == null || !presence.startsWith(fields[0] + "|")) {
            return Optional.empty();
        }
        return Optional.of(playerId);
    }

    private static String nameKey(String name) {
        return "hydrogen:name:" + name;
    }

    @Override
    public void close() {
        executor.shutdown();
        subscription.close();
        commands.close();
        client.shutdown();
    }
}
