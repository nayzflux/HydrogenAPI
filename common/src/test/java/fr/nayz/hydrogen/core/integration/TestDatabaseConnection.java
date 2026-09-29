package fr.nayz.hydrogen.core.integration;

import fr.nayz.hydrogen.core.HydrogenConfig;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.SQLException;

final class TestDatabaseConnection {
    private TestDatabaseConnection() {
    }

    static Connection open(HydrogenConfig config) throws SQLException {
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        postgres.setServerNames(new String[]{config.databaseHost()});
        postgres.setPortNumbers(new int[]{config.databasePort()});
        postgres.setDatabaseName(config.databaseName());
        postgres.setUser(config.databaseUser());
        postgres.setPassword(config.databasePassword());
        return postgres.getConnection();
    }
}
