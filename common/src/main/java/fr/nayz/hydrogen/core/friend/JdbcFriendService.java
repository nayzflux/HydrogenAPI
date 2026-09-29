package fr.nayz.hydrogen.core.friend;

import fr.nayz.hydrogen.api.friend.FriendRequest;
import fr.nayz.hydrogen.api.friend.FriendService;
import fr.nayz.hydrogen.api.HydrogenErrorReason;
import fr.nayz.hydrogen.core.database.Database;
import fr.nayz.hydrogen.core.database.Sql;
import fr.nayz.hydrogen.core.redis.RedisCoordinator;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.List;
import java.util.UUID;

public final class JdbcFriendService implements FriendService {
    private final Database database;
    private final RedisCoordinator redis;

    public JdbcFriendService(Database database, RedisCoordinator redis) {
        this.database = database;
        this.redis = redis;
    }

    @Override
    public CompletableFuture<Void> request(UUID sender, UUID recipient) {
        Sql.require(!sender.equals(recipient), HydrogenErrorReason.INVALID_INPUT,
                "A player cannot friend themselves");
        UUID low = low(sender, recipient);
        UUID high = high(sender, recipient);
        return database.transaction(connection -> {
            Sql.ensureAccount(connection, sender);
            Sql.ensureAccount(connection, recipient);
            try (PreparedStatement statement = Sql.prepare(connection,
                    "INSERT INTO hydrogen_friendships(player_low, player_high, requested_by, status) "
                            + "VALUES (?, ?, ?, 'PENDING') ON CONFLICT DO NOTHING",
                    low, high, sender)) {
                Sql.require(statement.executeUpdate() == 1,
                        HydrogenErrorReason.CONFLICT, "Friendship or request already exists");
            }
            return null;
        }).thenRun(() -> redis.publishSocial("friend-request", recipient, sender));
    }

    @Override
    public CompletableFuture<Void> accept(UUID recipient, UUID sender) {
        return changePending(recipient, sender, true)
                .thenRun(() -> redis.publishSocial("friend-accepted", sender, recipient));
    }

    @Override
    public CompletableFuture<Void> decline(UUID recipient, UUID sender) {
        return changePending(recipient, sender, false);
    }

    @Override
    public CompletableFuture<Void> remove(UUID playerId, UUID friendId) {
        return database.transaction(connection -> {
            try (PreparedStatement statement = Sql.prepare(connection,
                    "DELETE FROM hydrogen_friendships WHERE player_low = ? AND player_high = ? "
                            + "AND status = 'ACCEPTED'", low(playerId, friendId), high(playerId, friendId))) {
                Sql.require(statement.executeUpdate() == 1,
                        HydrogenErrorReason.NOT_FOUND, "Friendship not found");
            }
            return null;
        }).thenRun(() -> redis.publishSocial("friend-removed", friendId, playerId));
    }

    @Override
    public CompletableFuture<List<UUID>> list(UUID playerId) {
        return database.read(connection -> {
            List<UUID> friends = new ArrayList<>();
            try (PreparedStatement statement = Sql.prepare(connection,
                    "SELECT player_low, player_high FROM hydrogen_friendships "
                            + "WHERE status = 'ACCEPTED' AND (player_low = ? OR player_high = ?) "
                            + "ORDER BY player_low, player_high",
                    playerId, playerId);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID low = Sql.uuid(rows, "player_low");
                    friends.add(playerId.equals(low) ? Sql.uuid(rows, "player_high") : low);
                }
            }
            return List.copyOf(friends);
        });
    }

    @Override
    public CompletableFuture<List<FriendRequest>> pending(UUID playerId) {
        return database.read(connection -> {
            List<FriendRequest> requests = new ArrayList<>();
            try (PreparedStatement statement = Sql.prepare(connection,
                    "SELECT player_low, player_high, requested_by, created_at "
                            + "FROM hydrogen_friendships WHERE status = 'PENDING' "
                            + "AND (player_low = ? OR player_high = ?) "
                            + "ORDER BY created_at, player_low, player_high", playerId, playerId);
                 ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    UUID sender = Sql.uuid(rows, "requested_by");
                    UUID low = Sql.uuid(rows, "player_low");
                    UUID high = Sql.uuid(rows, "player_high");
                    requests.add(new FriendRequest(sender, sender.equals(low) ? high : low,
                            rows.getTimestamp("created_at").toInstant()));
                }
            }
            return List.copyOf(requests);
        });
    }

    private CompletableFuture<Void> changePending(UUID recipient, UUID sender, boolean accept) {
        String query = accept
                ? "UPDATE hydrogen_friendships SET status = 'ACCEPTED' "
                        + "WHERE player_low = ? AND player_high = ? AND requested_by = ? AND status = 'PENDING'"
                : "DELETE FROM hydrogen_friendships "
                        + "WHERE player_low = ? AND player_high = ? AND requested_by = ? AND status = 'PENDING'";
        return database.transaction(connection -> {
            try (PreparedStatement statement = Sql.prepare(connection, query,
                    low(sender, recipient), high(sender, recipient), sender)) {
                Sql.require(statement.executeUpdate() == 1,
                        HydrogenErrorReason.NOT_FOUND, "Pending request not found");
            }
            return null;
        });
    }

    private static UUID low(UUID first, UUID second) {
        return first.toString().compareTo(second.toString()) < 0 ? first : second;
    }

    private static UUID high(UUID first, UUID second) {
        return first.toString().compareTo(second.toString()) < 0 ? second : first;
    }
}
