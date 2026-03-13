package io.torana.jdbc.dialect;

/**
 * Database dialect abstraction for SQL variations.
 *
 * <p>Different databases have different SQL syntax, especially for JSON handling. This interface
 * abstracts those differences.
 */
public interface SqlDialect {

    /**
     * Returns the INSERT SQL for the audit entries table.
     *
     * @param tableName the table name
     * @return the INSERT SQL statement
     */
    String getInsertSql(String tableName);

    /**
     * Returns the SELECT by ID SQL.
     *
     * @param tableName the table name
     * @return the SELECT SQL statement
     */
    String getSelectByIdSql(String tableName);

    /**
     * Returns the base SELECT SQL for queries.
     *
     * @param tableName the table name
     * @return the SELECT SQL with WHERE 1=1
     */
    String getQueryBaseSql(String tableName);

    /**
     * Returns the COUNT SQL for queries.
     *
     * @param tableName the table name
     * @return the COUNT SQL with WHERE 1=1
     */
    String getCountSql(String tableName);

    /**
     * Converts a JSON string to the appropriate database type.
     *
     * @param json the JSON string
     * @return the database-specific JSON value
     */
    Object toJsonValue(String json);

    /**
     * Returns the name of this dialect.
     *
     * @return the dialect name
     */
    String name();
}
