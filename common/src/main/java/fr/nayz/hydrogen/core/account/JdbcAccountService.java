package fr.nayz.hydrogen.core.account;

import fr.nayz.hydrogen.api.account.Account;
import fr.nayz.hydrogen.api.account.AccountService;
import fr.nayz.hydrogen.api.account.Rank;
import fr.nayz.hydrogen.api.HydrogenErrorReason;
import fr.nayz.hydrogen.api.HydrogenException;
import fr.nayz.hydrogen.core.database.AccountRowOperations;
import fr.nayz.hydrogen.core.database.Database;
import fr.nayz.hydrogen.core.database.Sql;
import fr.nayz.hydrogen.core.redis.RedisCoordinator;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class JdbcAccountService implements AccountService {
    private final Database database;
    private final RedisCoordinator redis;

    public JdbcAccountService(Database database, RedisCoordinator redis) {
        this.database = database;
        this.redis = redis;
    }

    @Override
    public CompletableFuture<Optional<Account>> find(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        return database.read(connection -> {
            try (PreparedStatement statement = Sql.prepare(connection,
                    "SELECT 1 FROM hydrogen_accounts WHERE player_id = ?", playerId);
                 ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    return Optional.empty();
                }
            }
            return Optional.of(load(connection, playerId));
        });
    }

    @Override
    public CompletableFuture<Account> getOrCreate(UUID playerId) {
        return database.transaction(connection -> {
            Sql.ensureAccount(connection, playerId);
            return load(connection, playerId);
        });
    }

    @Override
    public CompletableFuture<Account> setRank(UUID playerId, Rank rank) {
        Objects.requireNonNull(rank, "rank");
        return database.transaction(connection -> {
            Sql.ensureAccount(connection, playerId);
            try (PreparedStatement statement = Sql.prepare(connection,
                    "UPDATE hydrogen_accounts SET rank_id = ? WHERE player_id = ?", rank.name(), playerId)) {
                statement.executeUpdate();
            }
            return load(connection, playerId);
        }).thenApply(account -> {
            redis.publish("rank", playerId.toString());
            return account;
        });
    }

    @Override
    public CompletableFuture<Account> setLevel(UUID playerId, int level) {
        requireLevel(level);
        return database.transaction(connection -> {
            Sql.ensureAccount(connection, playerId);
            try (PreparedStatement statement = Sql.prepare(connection,
                    "UPDATE hydrogen_accounts SET global_level = ? WHERE player_id = ?", level, playerId)) {
                statement.executeUpdate();
            }
            return load(connection, playerId);
        });
    }

    @Override
    public CompletableFuture<Account> setGameLevel(UUID playerId, String gameId, int level) {
        requireLevel(level);
        Sql.require(gameId != null && !gameId.isBlank(), HydrogenErrorReason.INVALID_INPUT, "gameId is required");
        return database.transaction(connection -> {
            Sql.ensureAccount(connection, playerId);
            try (PreparedStatement statement = Sql.prepare(connection,
                    "INSERT INTO hydrogen_game_levels(player_id, game_id, level) VALUES (?, ?, ?) "
                            + "ON CONFLICT (player_id, game_id) DO UPDATE SET level = EXCLUDED.level",
                    playerId, gameId, level)) {
                statement.executeUpdate();
            }
            return load(connection, playerId);
        });
    }

    @Override
    public CompletableFuture<Account> changeCoins(UUID playerId, long delta, UUID operationId) {
        Sql.require(delta != 0, HydrogenErrorReason.INVALID_INPUT, "Coin delta must not be zero");
        return database.transaction(connection -> {
            Sql.ensureAccount(connection, playerId);
            AccountRowOperations.lockAccount(connection, playerId);
            try (PreparedStatement statement = Sql.prepare(connection,
                    "SELECT player_id, delta FROM hydrogen_wallet_operations WHERE operation_id = ?", operationId);
                 ResultSet rows = statement.executeQuery()) {
                if (rows.next()) {
                    Sql.require(playerId.equals(Sql.uuid(rows, "player_id")) && rows.getLong("delta") == delta,
                            HydrogenErrorReason.CONFLICT, "Operation ID was used for another change");
                    return load(connection, playerId);
                }
            }
            long current = AccountRowOperations.balance(connection, playerId);
            long updated;
            try {
                updated = Math.addExact(current, delta);
            } catch (ArithmeticException exception) {
                throw new HydrogenException(HydrogenErrorReason.INVALID_INPUT, "Coin balance overflow");
            }
            Sql.require(updated >= 0, HydrogenErrorReason.INSUFFICIENT_COINS, "Insufficient coins");
            try (PreparedStatement statement = Sql.prepare(connection,
                    "UPDATE hydrogen_accounts SET coins = ? WHERE player_id = ?", updated, playerId)) {
                statement.executeUpdate();
            }
            try (PreparedStatement statement = Sql.prepare(connection,
                    "INSERT INTO hydrogen_wallet_operations(operation_id, player_id, delta, balance_after) "
                            + "VALUES (?, ?, ?, ?)", operationId, playerId, delta, updated)) {
                statement.executeUpdate();
            }
            return load(connection, playerId);
        });
    }

    private static Account load(Connection connection, UUID playerId) throws SQLException {
        Rank rank;
        int level;
        long coins;
        try (PreparedStatement statement = Sql.prepare(connection,
                "SELECT rank_id, global_level, coins FROM hydrogen_accounts WHERE player_id = ?", playerId);
             ResultSet rows = statement.executeQuery()) {
            Sql.require(rows.next(), HydrogenErrorReason.NOT_FOUND, "Account not found");
            rank = Rank.valueOf(rows.getString("rank_id"));
            level = rows.getInt("global_level");
            coins = rows.getLong("coins");
        }
        Map<String, Integer> levels = new HashMap<>();
        try (PreparedStatement statement = Sql.prepare(connection,
                "SELECT game_id, level FROM hydrogen_game_levels WHERE player_id = ?", playerId);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                levels.put(rows.getString("game_id"), rows.getInt("level"));
            }
        }
        return new Account(playerId, rank, level, coins, Map.copyOf(levels));
    }

    private static void requireLevel(int level) {
        Sql.require(level >= 1, HydrogenErrorReason.INVALID_INPUT, "Level must be at least one");
    }
}
