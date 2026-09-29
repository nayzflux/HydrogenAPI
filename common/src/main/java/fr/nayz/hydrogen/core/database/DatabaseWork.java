package fr.nayz.hydrogen.core.database;

import java.sql.Connection;
import java.sql.SQLException;

@FunctionalInterface
public interface DatabaseWork<T> {
    T run(Connection connection) throws SQLException;
}
