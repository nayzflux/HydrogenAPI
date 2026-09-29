package fr.nayz.hydrogen.core.directory;

import fr.nayz.hydrogen.api.HydrogenErrorReason;
import fr.nayz.hydrogen.core.database.Database;
import fr.nayz.hydrogen.core.database.Sql;
import fr.nayz.hydrogen.core.redis.RedisCoordinator;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PlayerDirectory {
    private final Database database;
    private final RedisCoordinator redis;

    public PlayerDirectory(Database database, RedisCoordinator redis) {
        this.database = database;
        this.redis = redis;
    }

    public CompletableFuture<Void> remember(UUID playerId, String name) {
        validate(name);
        return database.transaction(connection -> {
            Sql.ensureAccount(connection, playerId);
            try (PreparedStatement statement = Sql.prepare(connection,
                    "UPDATE hydrogen_accounts SET last_name = ? WHERE player_id = ?", name, playerId)) {
                statement.executeUpdate();
            }
            return null;
        });
    }

    public CompletableFuture<Map<UUID, String>> names(Collection<UUID> playerIds) {
        if (playerIds.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        UUID[] ids = playerIds.toArray(UUID[]::new);
        return database.read(connection -> {
            Map<UUID, String> names = new HashMap<>();
            try (PreparedStatement statement = Sql.prepare(connection,
                    "SELECT player_id, last_name FROM hydrogen_accounts WHERE player_id = ANY (?)",
                    connection.createArrayOf("uuid", ids));
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    String name = rows.getString("last_name");
                    if (name != null) {
                        names.put(Sql.uuid(rows, "player_id"), name);
                    }
                }
            }
            return Map.copyOf(names);
        });
    }

    public CompletableFuture<List<UUID>> knownByName(String name) {
        validate(name);
        return database.read(connection -> {
            ArrayList<UUID> matches = new ArrayList<>();
            try (PreparedStatement statement = Sql.prepare(connection,
                    "SELECT player_id FROM hydrogen_accounts WHERE lower(last_name) = ? ORDER BY player_id",
                    name.toLowerCase(java.util.Locale.ROOT));
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    matches.add(Sql.uuid(rows, "player_id"));
                }
            }
            return List.copyOf(matches);
        });
    }

    public CompletableFuture<Void> claim(UUID playerId, String name, UUID sessionId) {
        validate(name);
        return redis.run(() -> {
            redis.claimName(name.toLowerCase(java.util.Locale.ROOT), playerId, sessionId);
            return null;
        });
    }

    public CompletableFuture<Void> renew(UUID playerId, String name, UUID sessionId) {
        return redis.run(() -> {
            redis.renewName(name.toLowerCase(java.util.Locale.ROOT), playerId, sessionId);
            return null;
        });
    }

    public CompletableFuture<Void> clear(UUID playerId, String name, UUID sessionId) {
        return redis.run(() -> {
            redis.clearName(name.toLowerCase(java.util.Locale.ROOT), playerId, sessionId);
            return null;
        });
    }

    public CompletableFuture<Optional<UUID>> online(String name) {
        validate(name);
        return redis.run(() -> redis.onlineName(name.toLowerCase(java.util.Locale.ROOT)));
    }

    private static void validate(String name) {
        Sql.require(name != null && name.matches("[A-Za-z0-9_]{3,16}"),
                HydrogenErrorReason.INVALID_INPUT, "Invalid Minecraft name");
    }
}
