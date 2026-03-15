package io.torana.jdbc.dialect;

/** SQL dialect for H2 database. */
public class H2Dialect implements SqlDialect {

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
        return "h2";
    }
}
