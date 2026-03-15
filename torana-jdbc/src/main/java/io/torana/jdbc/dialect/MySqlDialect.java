package io.torana.jdbc.dialect;

/** SQL dialect for MySQL database. */
public class MySqlDialect implements SqlDialect {

    @Override
    public String getInsertSql(String tableName) {
        return """
            INSERT INTO %s (
                id, action, occurred_at, outcome,
                actor_id, actor_type, actor_name,
                tenant_id, tenant_name,
                target_type, target_id, target_name,
                request_id, request_method, request_path, client_ip, user_agent,
                trace_id, span_id, parent_span_id,
                metadata, changes, error_message, schema_version
            ) VALUES (
                ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?,
                ?, ?, ?,
                ?, ?, ?, ?, ?,
                ?, ?, ?,
                ?, ?, ?, ?
            )
            """
                .formatted(tableName);
    }

    @Override
    public String getSelectByIdSql(String tableName) {
        return "SELECT * FROM %s WHERE id = ?".formatted(tableName);
    }

    @Override
    public String getQueryBaseSql(String tableName) {
        return "SELECT * FROM %s WHERE 1=1".formatted(tableName);
    }

    @Override
    public String getCountSql(String tableName) {
        return "SELECT COUNT(*) FROM %s WHERE 1=1".formatted(tableName);
    }

    @Override
    public Object toJsonValue(String json) {
        return json;
    }

    @Override
    public String name() {
        return "mysql";
    }

    @Override
    public String getCreateTableSql(String tableName) {
        return """
            CREATE TABLE IF NOT EXISTS %s (
                id             CHAR(36) PRIMARY KEY,
                action         VARCHAR(255) NOT NULL,
                occurred_at    DATETIME(6)  NOT NULL,
                outcome        VARCHAR(20)  NOT NULL,
                actor_id       VARCHAR(255),
                actor_type     VARCHAR(50),
                actor_name     VARCHAR(255),
                tenant_id      VARCHAR(255),
                tenant_name    VARCHAR(255),
                target_type    VARCHAR(255),
                target_id      VARCHAR(255),
                target_name    VARCHAR(255),
                request_id     VARCHAR(255),
                request_method VARCHAR(10),
                request_path   VARCHAR(2048),
                client_ip      VARCHAR(45),
                user_agent     VARCHAR(512),
                trace_id       VARCHAR(64),
                span_id        VARCHAR(64),
                parent_span_id VARCHAR(64),
                metadata       JSON,
                changes        JSON,
                error_message  TEXT,
                schema_version INT NOT NULL DEFAULT 1,
                created_at     DATETIME(6) DEFAULT CURRENT_TIMESTAMP(6),
                INDEX idx_%s_action (action),
                INDEX idx_%s_occurred_at (occurred_at),
                INDEX idx_%s_actor_id (actor_id),
                INDEX idx_%s_tenant_id (tenant_id),
                INDEX idx_%s_target (target_type, target_id)
            )
            """.formatted(tableName,
                tableName, tableName, tableName, tableName, tableName);
    }

    @Override
    public String getDropTableSql(String tableName) {
        return "DROP TABLE IF EXISTS %s".formatted(tableName);
    }

    @Override
    public String getTableExistsSql(String tableName) {
        return "SELECT 1 FROM information_schema.tables WHERE table_name = '%s'".formatted(tableName);
    }
}
