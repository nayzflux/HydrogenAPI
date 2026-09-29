package fr.nayz.hydrogen.core.presence;

import fr.nayz.hydrogen.api.HydrogenErrorReason;
import fr.nayz.hydrogen.api.presence.Presence;
import fr.nayz.hydrogen.api.presence.PresenceService;
import fr.nayz.hydrogen.core.database.Sql;
import fr.nayz.hydrogen.core.redis.RedisCoordinator;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.Optional;
import java.util.UUID;

public final class RedisPresenceService implements PresenceService {
    private final RedisCoordinator redis;

    public RedisPresenceService(RedisCoordinator redis) {
        this.redis = redis;
    }

    @Override
    public CompletableFuture<Optional<Presence>> get(UUID playerId) {
        return redis.run(() -> {
            String value = redis.get(key(playerId));
            if (value == null) {
                return Optional.empty();
            }
            String[] fields = value.split("\\|", 4);
            return Optional.of(new Presence(playerId, fields[1], fields[2],
                    Instant.ofEpochMilli(Long.parseLong(fields[3]))));
        });
    }

    @Override
    public CompletableFuture<Void> claim(UUID playerId, String proxyId, String serverId, UUID sessionId) {
        validate(proxyId, serverId);
        return redis.run(() -> {
            redis.set(key(playerId), value(sessionId, proxyId, serverId));
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> update(UUID playerId, String proxyId, String serverId, UUID sessionId) {
        validate(proxyId, serverId);
        return redis.run(() -> {
            redis.updateIfOwned(key(playerId), sessionId.toString(), value(sessionId, proxyId, serverId));
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> clear(UUID playerId, UUID sessionId) {
        return redis.run(() -> {
            redis.deleteIfOwned(key(playerId), sessionId.toString());
            return null;
        });
    }

    private static String key(UUID playerId) {
        return "hydrogen:presence:" + playerId;
    }

    private static String value(UUID sessionId, String proxyId, String serverId) {
        return sessionId + "|" + proxyId + "|" + serverId + "|" + System.currentTimeMillis();
    }

    private static void validate(String proxyId, String serverId) {
        Sql.require(proxyId != null && !proxyId.isBlank() && !proxyId.contains("|"),
                HydrogenErrorReason.INVALID_INPUT, "Invalid proxyId");
        Sql.require(serverId != null && !serverId.isBlank() && !serverId.contains("|"),
                HydrogenErrorReason.INVALID_INPUT, "Invalid serverId");
    }
}
