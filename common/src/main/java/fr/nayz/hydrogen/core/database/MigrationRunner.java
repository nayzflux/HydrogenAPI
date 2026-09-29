package fr.nayz.hydrogen.core.database;

import fr.nayz.hydrogen.core.HydrogenConfig;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public final class MigrationRunner {
    private MigrationRunner() {
    }

    public static void main(String[] args) throws SQLException, IOException {
        migrate(HydrogenConfig.fromEnvironment());
    }

    public static void migrate(HydrogenConfig config) throws SQLException, IOException {
        try (Database database = new Database(config);
             Connection connection = database.migrationConnection()) {
            connection.setAutoCommit(false);
            try {
                connection.createStatement().execute("SELECT pg_advisory_xact_lock(41022026)");
                connection.createStatement().execute("CREATE TABLE IF NOT EXISTS hydrogen_schema_version (version INTEGER PRIMARY KEY)");
                apply(connection, 1, "/db/V1__initial.sql");
                apply(connection, 2, "/db/V2__social_commands.sql");
                apply(connection, 3, "/db/V3__matchmaking.sql");
                connection.commit();
            } catch (SQLException | IOException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static void apply(Connection connection, int version, String resource) throws SQLException, IOException {
        try (PreparedStatement query = connection.prepareStatement(
                "SELECT 1 FROM hydrogen_schema_version WHERE version = ?")) {
            query.setInt(1, version);
            try (ResultSet rows = query.executeQuery()) {
                if (rows.next()) {
                    return;
                }
            }
        }
        try (var stream = MigrationRunner.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IOException("Missing migration " + resource);
            }
            byte[] sql = stream.readAllBytes();
            connection.createStatement().execute(new String(sql, StandardCharsets.UTF_8));
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO hydrogen_schema_version(version) VALUES (?)")) {
            insert.setInt(1, version);
            insert.executeUpdate();
        }
    }
}
