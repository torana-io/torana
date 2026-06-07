package io.torana.jdbc;

import io.torana.jdbc.dialect.SqlDialect;

/**
 * Helper class for building database-specific JSON queries.
 *
 * <p>Different databases have different syntax for querying JSON columns. This helper abstracts
 * those differences and provides a consistent interface for JSON queries.
 *
 * <p>Supported databases:
 *
 * <ul>
 *   <li><strong>PostgreSQL:</strong> Uses {@code ->} and {@code ->>} operators
 *   <li><strong>MySQL:</strong> Uses {@code JSON_EXTRACT} and {@code JSON_UNQUOTE} functions
 *   <li><strong>H2:</strong> Uses CLOB substring search (best-effort, not performant)
 * </ul>
 */
public class JsonQueryHelper {

    /**
     * Builds a SQL expression to extract a JSON field value.
     *
     * <p>Returns SQL that extracts the value for the given key from the metadata JSON column.
     *
     * @param dialect the SQL dialect
     * @param columnName the JSON column name (typically "metadata")
     * @param jsonKey the JSON key to extract
     * @return SQL expression to extract the value
     */
    public static String buildJsonExtractExpression(
            SqlDialect dialect, String columnName, String jsonKey) {
        String dialectClass = dialect.getClass().getSimpleName();

        return switch (dialectClass) {
            case "PostgreSqlDialect" ->
                    // PostgreSQL: metadata->>'customerId'
                    String.format("%s->>'%s'", columnName, escapeJsonKey(jsonKey));
            case "MySqlDialect" ->
                    // MySQL: JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.customerId'))
                    String.format(
                            "JSON_UNQUOTE(JSON_EXTRACT(%s, '$.%s'))",
                            columnName, escapeJsonKey(jsonKey));
            case "H2Dialect" ->
                    // H2: Best-effort substring search (not ideal, but works for dev/test)
                    // Searches for: "customerId":"value" or "customerId": "value"
                    String.format(
                            "SUBSTRING(%s, LOCATE('\"%s\":', %s) + %d)",
                            columnName, escapeJsonKey(jsonKey), columnName, jsonKey.length() + 4);
            default ->
                    // Fallback: try PostgreSQL syntax (most common)
                    String.format("%s->>'%s'", columnName, escapeJsonKey(jsonKey));
        };
    }

    /**
     * Builds a SQL expression to check if a JSON key exists.
     *
     * <p>Returns SQL that checks whether the metadata JSON contains the specified key.
     *
     * @param dialect the SQL dialect
     * @param columnName the JSON column name (typically "metadata")
     * @param jsonKey the JSON key to check for
     * @return SQL expression to check for key existence
     */
    public static String buildJsonKeyExistsExpression(
            SqlDialect dialect, String columnName, String jsonKey) {
        String dialectClass = dialect.getClass().getSimpleName();

        return switch (dialectClass) {
            case "PostgreSqlDialect" ->
                    // PostgreSQL: metadata ? 'customerId'
                    String.format("%s ? '%s'", columnName, escapeJsonKey(jsonKey));
            case "MySqlDialect" ->
                    // MySQL: JSON_CONTAINS_PATH(metadata, 'one', '$.customerId')
                    String.format(
                            "JSON_CONTAINS_PATH(%s, 'one', '$.%s')",
                            columnName, escapeJsonKey(jsonKey));
            case "H2Dialect" ->
                    // H2: Search for key in JSON string
                    String.format("%s LIKE '%%\"%s\":%%'", columnName, escapeJsonKey(jsonKey));
            default ->
                    // Fallback: try PostgreSQL syntax
                    String.format("%s ? '%s'", columnName, escapeJsonKey(jsonKey));
        };
    }

    /**
     * Builds a SQL WHERE clause for metadata filtering.
     *
     * <p>Returns a complete WHERE condition that can be added to the query.
     *
     * @param dialect the SQL dialect
     * @param columnName the JSON column name (typically "metadata")
     * @param jsonKey the JSON key to filter by
     * @param value the value to match (will be bound as parameter)
     * @return SQL WHERE condition
     */
    public static String buildMetadataFilterCondition(
            SqlDialect dialect, String columnName, String jsonKey, Object value) {
        String extractExpression = buildJsonExtractExpression(dialect, columnName, jsonKey);

        if (value == null) {
            return String.format("(%s IS NULL OR %s = '')", extractExpression, extractExpression);
        }

        if (value instanceof Number) {
            String dialectClass = dialect.getClass().getSimpleName();
            if ("PostgreSqlDialect".equals(dialectClass)) {
                return String.format("(%s)::numeric = ?", extractExpression);
            } else if ("MySqlDialect".equals(dialectClass)) {
                return String.format("CAST(%s AS DECIMAL) = ?", extractExpression);
            }
        }

        return String.format("%s = ?", extractExpression);
    }

    /**
     * Builds a SQL WHERE clause for checking metadata key existence.
     *
     * @param dialect the SQL dialect
     * @param columnName the JSON column name (typically "metadata")
     * @param jsonKey the JSON key to check for
     * @return SQL WHERE condition
     */
    public static String buildMetadataKeyExistsCondition(
            SqlDialect dialect, String columnName, String jsonKey) {
        return buildJsonKeyExistsExpression(dialect, columnName, jsonKey);
    }

    /**
     * Escapes a JSON key for safe use in SQL.
     *
     * <p>Prevents SQL injection by validating and escaping the key.
     *
     * @param key the JSON key
     * @return escaped key
     * @throws IllegalArgumentException if key contains invalid characters
     */
    private static String escapeJsonKey(String key) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("JSON key cannot be null or empty");
        }

        if (!key.matches("^[a-zA-Z0-9_.-]+$")) {
            throw new IllegalArgumentException(
                    "Invalid JSON key: "
                            + key
                            + ". Only alphanumeric, underscore, dash, and period characters are"
                            + " allowed");
        }

        return key.replace("'", "''");
    }

    /**
     * Validates that a field name is safe for SQL ORDER BY clause.
     *
     * @param field the field name
     * @throws IllegalArgumentException if field name is invalid
     */
    public static void validateOrderByField(String field) {
        if (field == null || field.isEmpty()) {
            throw new IllegalArgumentException("Order by field cannot be null or empty");
        }

        String[] allowedFields = {
            "occurred_at", "action", "actor_id", "tenant_id", "target_type", "target_id", "outcome"
        };

        boolean isAllowed = false;
        for (String allowedField : allowedFields) {
            if (allowedField.equals(field)) {
                isAllowed = true;
                break;
            }
        }

        if (!isAllowed) {
            throw new IllegalArgumentException(
                    "Invalid order by field: "
                            + field
                            + ". Allowed fields: occurred_at, action, actor_id, tenant_id,"
                            + " target_type, target_id, outcome");
        }
    }
}
