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
     * Counts the total number of matching entries.
     *
     * <p>This ignores limit and offset settings.
     *
     * @return the total count
     */
    long count();
}
