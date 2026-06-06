package io.torana.api;

import io.torana.api.model.AuditOutcome;

import java.time.Instant;
import java.util.List;

/**
 * Query interface for retrieving audit entries.
 *
 * <p>Build a query using the fluent API, then execute it:
 *
 * <pre>{@code
 * List<AuditEntryView> entries = auditTrail.query()
 *     .action("order.cancelled")
 *     .actor("alice")
 *     .from(Instant.now().minus(Duration.ofDays(7)))
 *     .limit(100)
 *     .execute();
 * }</pre>
 */
public interface AuditQuery {

    /**
     * Filters by exact action name.
     *
     * @param action the action name (e.g., "order.cancelled")
     * @return this query for chaining
     */
    AuditQuery action(String action);

    /**
     * Filters by action name prefix.
     *
     * <p>Example: "order." matches "order.cancelled", "order.created", etc.
     *
     * @param prefix the action prefix
     * @return this query for chaining
     */
    AuditQuery actionPrefix(String prefix);

    /**
     * Filters by actor ID.
     *
     * @param actorId the actor identifier
     * @return this query for chaining
     */
    AuditQuery actor(String actorId);

    /**
     * Filters by tenant ID.
     *
     * @param tenantId the tenant identifier
     * @return this query for chaining
     */
    AuditQuery tenant(String tenantId);

    /**
     * Filters by target type and ID.
     *
     * @param type the target type
     * @param id the target identifier
     * @return this query for chaining
     */
    AuditQuery target(String type, String id);

    /**
     * Filters by target type only.
     *
     * @param type the target type
     * @return this query for chaining
     */
    AuditQuery targetType(String type);

    /**
     * Filters entries after the given time (inclusive).
     *
     * @param from the start time
     * @return this query for chaining
     */
    AuditQuery from(Instant from);

    /**
     * Filters entries before the given time (inclusive).
     *
     * @param to the end time
     * @return this query for chaining
     */
    AuditQuery to(Instant to);

    /**
     * Filters by outcome.
     *
     * @param outcome the outcome to filter by
     * @return this query for chaining
     */
    AuditQuery outcome(AuditOutcome outcome);

    /**
     * Filters by request ID.
     *
     * @param requestId the request identifier
     * @return this query for chaining
     */
    AuditQuery requestId(String requestId);

    /**
     * Filters by trace ID.
     *
     * @param traceId the trace identifier
     * @return this query for chaining
     */
    AuditQuery traceId(String traceId);

    /**
     * Filters by metadata key-value pair.
     *
     * <p>This filters entries where the metadata JSON contains the specified key with the specified
     * value.
     *
     * <p>Example:
     *
     * <pre>{@code
     * query.metadata("customerId", "12345")
     *      .metadata("region", "US-WEST")
     * }</pre>
     *
     * <p><strong>Performance Note:</strong> Metadata filtering requires JSON query support and may
     * be slower than indexed field queries. Consider creating database indexes on frequently queried
     * metadata fields.
     *
     * @param key the metadata key
     * @param value the metadata value
     * @return this query instance for chaining
     */
    AuditQuery metadata(String key, Object value);

    /**
     * Filters entries that have a specific metadata key (regardless of value).
     *
     * <p>Example:
     *
     * <pre>{@code
     * query.hasMetadata("errorCode")  // Find all entries with an errorCode field
     * }</pre>
     *
     * @param key the metadata key to check for
     * @return this query instance for chaining
     */
    AuditQuery hasMetadata(String key);

    /**
     * Orders results by the specified field and direction.
     *
     * <p>Default ordering is by {@code occurred_at DESC} (most recent first).
     *
     * <p>Supported fields:
     *
     * <ul>
     *   <li>occurred_at (default)
     *   <li>action
     *   <li>actor_id
     *   <li>target_type
     *   <li>outcome
     * </ul>
     *
     * @param field the field to order by
     * @param direction the order direction (ASC or DESC)
     * @return this query instance for chaining
     */
    AuditQuery orderBy(String field, OrderDirection direction);

    /**
     * Sets the maximum number of results to return.
     *
     * @param limit the maximum number of results
     * @return this query for chaining
     */
    AuditQuery limit(int limit);

    /**
     * Sets the offset for pagination.
     *
     * @param offset the number of results to skip
     * @return this query for chaining
     */
    AuditQuery offset(int offset);

    /**
     * Executes the query and returns matching entries.
     *
     * @return a list of matching audit entries
     */
    List<AuditEntryView> execute();

    /**
     * Executes the query with pagination support.
     *
     * <p>Returns a result object that includes the entries, total count, and pagination metadata.
     *
     * @return paginated query result
     */
    AuditQueryResult executeWithPagination();

    /**
     * Counts the total number of matching entries.
     *
     * <p>This ignores limit and offset settings.
     *
     * @return the total count
     */
    long count();

    /** Order direction for query results. */
    enum OrderDirection {
        /** Ascending order (A-Z, 0-9, oldest-newest). */
        ASC,
        /** Descending order (Z-A, 9-0, newest-oldest). */
        DESC
    }
}
