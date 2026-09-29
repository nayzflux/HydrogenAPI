package fr.nayz.hydrogen.core.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class EntitlementRowOperations {
    private EntitlementRowOperations() {
    }

    public static int add(Connection connection, UUID playerId, String gameId, String itemId, int quantity)
            throws SQLException {
        try (PreparedStatement statement = Sql.prepare(connection,
                "INSERT INTO hydrogen_entitlements(player_id, game_id, item_id, quantity) "
                        + "VALUES (?, ?, ?, ?) ON CONFLICT (player_id, game_id, item_id) "
                        + "DO UPDATE SET quantity = hydrogen_entitlements.quantity + EXCLUDED.quantity "
                        + "RETURNING quantity", playerId, gameId, itemId, quantity);
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getInt(1);
        }
    }
}
