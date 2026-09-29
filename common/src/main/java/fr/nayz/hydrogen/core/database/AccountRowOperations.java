package fr.nayz.hydrogen.core.database;

import fr.nayz.hydrogen.api.HydrogenErrorReason;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class AccountRowOperations {
    private AccountRowOperations() {
    }

    public static void lockAccount(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = Sql.prepare(connection,
                "SELECT player_id FROM hydrogen_accounts WHERE player_id = ? FOR UPDATE", playerId);
             ResultSet rows = statement.executeQuery()) {
            Sql.require(rows.next(), HydrogenErrorReason.NOT_FOUND, "Account not found");
        }
    }

    public static long balance(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = Sql.prepare(connection,
                "SELECT coins FROM hydrogen_accounts WHERE player_id = ?", playerId);
             ResultSet rows = statement.executeQuery()) {
            rows.next();
            return rows.getLong(1);
        }
    }
}
