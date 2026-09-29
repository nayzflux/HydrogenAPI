package fr.nayz.hydrogen.core.database;

import fr.nayz.hydrogen.api.HydrogenErrorReason;
import fr.nayz.hydrogen.api.HydrogenException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public final class Sql {
    private Sql() {
    }

    public static PreparedStatement prepare(Connection connection, String query, Object... arguments) throws SQLException {
        PreparedStatement statement = connection.prepareStatement(query);
        for (int index = 0; index < arguments.length; index++) {
            statement.setObject(index + 1, arguments[index]);
        }
        return statement;
    }

    public static void ensureAccount(Connection connection, UUID playerId) throws SQLException {
        try (PreparedStatement statement = prepare(connection,
                "INSERT INTO hydrogen_accounts(player_id) VALUES (?) ON CONFLICT DO NOTHING", playerId)) {
            statement.executeUpdate();
        }
    }

    public static void require(boolean condition, HydrogenErrorReason reason, String message) {
        if (!condition) {
            throw new HydrogenException(reason, message);
        }
    }

    public static UUID uuid(ResultSet rows, String column) throws SQLException {
        return rows.getObject(column, UUID.class);
    }
}
