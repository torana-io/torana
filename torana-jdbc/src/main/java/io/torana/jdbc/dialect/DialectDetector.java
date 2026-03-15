package io.torana.jdbc.dialect;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

/** Detects the appropriate SQL dialect from the DataSource. */
public final class DialectDetector {

    private DialectDetector() {
    }

    /**
     * Detects the SQL dialect from the given DataSource.
     *
     * @param dataSource the data source
     * @return the detected dialect
     * @throws RuntimeException if detection fails
     */
    public static SqlDialect detect(DataSource dataSource) {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            String productName = metaData.getDatabaseProductName().toLowerCase();

            if (productName.contains("postgresql")) {
                return new PostgreSqlDialect();
            } else if (productName.contains("mysql") || productName.contains("mariadb")) {
                return new MySqlDialect();
            } else if (productName.contains("h2")) {
                return new H2Dialect();
            } else {
                // Default to H2-compatible SQL
                return new H2Dialect();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to detect database dialect", e);
        }
    }

    /**
     * Returns a dialect for the given database name.
     *
     * @param databaseName the database product name
     * @return the dialect
     */
    public static SqlDialect forDatabase(String databaseName) {
        String name = databaseName.toLowerCase();
        if (name.contains("postgresql") || name.contains("postgres")) {
            return new PostgreSqlDialect();
        } else if (name.contains("mysql") || name.contains("mariadb")) {
            return new MySqlDialect();
        } else {
            return new H2Dialect();
        }
    }
}
