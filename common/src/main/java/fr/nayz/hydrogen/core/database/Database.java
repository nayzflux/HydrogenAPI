package fr.nayz.hydrogen.core.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import fr.nayz.hydrogen.api.HydrogenErrorReason;
import fr.nayz.hydrogen.api.HydrogenException;
import fr.nayz.hydrogen.core.HydrogenConfig;
import org.postgresql.ds.PGSimpleDataSource;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

public final class Database implements AutoCloseable {
    private final HikariDataSource pool;
    private final ExecutorService executor;

    public Database(HydrogenConfig config) {
        HikariConfig options = new HikariConfig();
        PGSimpleDataSource postgres = new PGSimpleDataSource();
        postgres.setServerNames(new String[]{config.databaseHost()});
        postgres.setPortNumbers(new int[]{config.databasePort()});
        postgres.setDatabaseName(config.databaseName());
        postgres.setUser(config.databaseUser());
        postgres.setPassword(config.databasePassword());
        postgres.setPrepareThreshold(0);
        options.setDataSource(postgres);
        options.setMaximumPoolSize(2);
        options.setMinimumIdle(0);
        options.setConnectionTimeout(5_000);
        options.setPoolName("hydrogen-database");
        pool = new HikariDataSource(options);
        executor = Executors.newFixedThreadPool(2, task -> {
            Thread thread = new Thread(task, "hydrogen-database");
            thread.setDaemon(true);
            return thread;
        });
    }

    public <T> CompletableFuture<T> read(DatabaseWork<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = pool.getConnection()) {
                return work.run(connection);
            } catch (SQLException exception) {
                throw unavailable(exception);
            }
        }, executor);
    }

    public <T> CompletableFuture<T> transaction(DatabaseWork<T> work) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection connection = pool.getConnection()) {
                connection.setAutoCommit(false);
                try {
                    T result = work.run(connection);
                    connection.commit();
                    return result;
                } catch (SQLException | RuntimeException exception) {
                    connection.rollback();
                    throw exception;
                }
            } catch (SQLException exception) {
                throw unavailable(exception);
            }
        }, executor);
    }

    public Connection migrationConnection() throws SQLException {
        return pool.getConnection();
    }

    private static HydrogenException unavailable(SQLException exception) {
        if ("23505".equals(exception.getSQLState())) {
            return new HydrogenException(HydrogenErrorReason.CONFLICT, "A unique value already exists", exception);
        }
        return new HydrogenException(HydrogenErrorReason.UNAVAILABLE, "Database operation failed", exception);
    }

    @Override
    public void close() {
        executor.shutdown();
        pool.close();
    }
}
