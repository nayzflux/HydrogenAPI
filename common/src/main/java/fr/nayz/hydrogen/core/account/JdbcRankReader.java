package fr.nayz.hydrogen.core.account;

import fr.nayz.hydrogen.api.account.Rank;
import fr.nayz.hydrogen.core.database.Database;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class JdbcRankReader {
    private final Database database;

    public JdbcRankReader(Database database) {
        this.database = database;
    }

    public CompletableFuture<Map<UUID, Rank>> getMany(Collection<UUID> playerIds) {
        List<UUID> ids = playerIds.stream().distinct().toList();
        if (ids.isEmpty()) {
            return CompletableFuture.completedFuture(Map.of());
        }
        return database.read(connection -> {
            Map<UUID, Rank> ranks = new HashMap<>();
            Array playerArray = connection.createArrayOf("uuid", ids.toArray(UUID[]::new));
            try (PreparedStatement query = connection.prepareStatement(
                    "SELECT player_id, rank_id FROM hydrogen_accounts WHERE player_id = ANY (?)")) {
                query.setArray(1, playerArray);
                try (ResultSet rows = query.executeQuery()) {
                    while (rows.next()) {
                        UUID playerId = rows.getObject("player_id", UUID.class);
                        Rank rank = Rank.valueOf(rows.getString("rank_id"));
                        ranks.put(playerId, rank);
                    }
                }
            } finally {
                playerArray.free();
            }
            return Map.copyOf(ranks);
        });
    }
}
