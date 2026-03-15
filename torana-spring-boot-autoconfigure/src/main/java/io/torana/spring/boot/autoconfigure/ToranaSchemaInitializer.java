package io.torana.spring.boot.autoconfigure;

import io.torana.jdbc.dialect.SqlDialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Initializes the Torana audit table schema based on configuration.
 *
 * <p>Supports three modes:
 * <ul>
 *   <li>{@code NONE} - No automatic schema creation</li>
 *   <li>{@code CREATE} - Create table if it doesn't exist</li>
 *   <li>{@code CREATE_DROP} - Create on startup, drop on shutdown</li>
 * </ul>
 */
public class ToranaSchemaInitializer implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(ToranaSchemaInitializer.class);

    private final JdbcTemplate jdbcTemplate;
    private final SqlDialect dialect;
    private final ToranaProperties properties;

    public ToranaSchemaInitializer(
            JdbcTemplate jdbcTemplate, SqlDialect dialect, ToranaProperties properties) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialect = dialect;
        this.properties = properties;
    }

    @Override
    public void afterPropertiesSet() {
        ToranaProperties.SchemaMode mode = properties.getSchemaMode();
        String tableName = properties.getTableName();

        if (mode == ToranaProperties.SchemaMode.NONE) {
            log.info("Torana schema initialization disabled (torana.schema-mode=none)");
            return;
        }

        if (tableExists(tableName)) {
            log.info("Torana audit table '{}' already exists", tableName);
            return;
        }

        log.info("Creating Torana audit table '{}' using {} dialect", tableName, dialect.name());
        createTable(tableName);
        log.info("Torana audit table '{}' created successfully", tableName);
    }

    @Override
    public void destroy() {
        if (properties.getSchemaMode() == ToranaProperties.SchemaMode.CREATE_DROP) {
            String tableName = properties.getTableName();
            log.info("Dropping Torana audit table '{}' (torana.schema-mode=create-drop)", tableName);
            dropTable(tableName);
        }
    }

    private boolean tableExists(String tableName) {
        try {
            String sql = dialect.getTableExistsSql(tableName);
            return Boolean.TRUE.equals(
                    jdbcTemplate.query(sql, (java.sql.ResultSet rs) -> rs.next()));
        } catch (Exception e) {
            log.debug("Table existence check failed, assuming table doesn't exist: {}", e.getMessage());
            return false;
        }
    }

    private void createTable(String tableName) {
        String sql = dialect.getCreateTableSql(tableName);
        // Split by semicolon for databases that don't support multiple statements
        String[] statements = sql.split(";");
        for (String statement : statements) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                jdbcTemplate.execute(trimmed);
            }
        }
    }

    private void dropTable(String tableName) {
        try {
            String sql = dialect.getDropTableSql(tableName);
            jdbcTemplate.execute(sql);
            log.info("Torana audit table '{}' dropped", tableName);
        } catch (Exception e) {
            log.warn("Failed to drop Torana audit table '{}': {}", tableName, e.getMessage());
        }
    }
}
