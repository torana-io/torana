package io.torana.api;

import io.torana.api.model.Actor;
import io.torana.api.model.AuditOutcome;
import io.torana.api.model.ChangeSet;
import io.torana.api.model.RequestContext;
import io.torana.api.model.Target;
import io.torana.api.model.Tenant;
import io.torana.api.model.TraceContext;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only view of an audit entry for query results.
 *
 * <p>This interface provides access to all fields of a persisted audit entry without exposing
 * modification capabilities.
 */
public interface AuditEntryView {

    /**
     * Returns the unique identifier of this entry.
     *
     * @return the entry ID
     */
    UUID id();

    /**
     * Returns the action name.
     *
     * @return the action name (e.g., "order.cancelled")
     */
    String action();

    /**
     * Returns when the action occurred.
     *
     * @return the timestamp
     */
    Instant occurredAt();

    /**
     * Returns the outcome of the action.
     *
     * @return the outcome
     */
    AuditOutcome outcome();

    /**
     * Returns the actor who caused the action.
     *
     * @return the actor, or null if not captured
     */
    Actor actor();

    /**
     * Returns the tenant context.
     *
     * @return the tenant, or null if not captured
     */
    Tenant tenant();

    /**
     * Returns the target of the action.
     *
     * @return the target, or null if not captured
     */
    Target target();

    /**
     * Returns the HTTP request context.
     *
     * @return the request context
     */
    RequestContext requestContext();

    /**
     * Returns the distributed tracing context.
     *
     * @return the trace context
     */
    TraceContext traceContext();

    /**
     * Returns additional metadata.
     *
     * @return the metadata map (never null)
     */
    Map<String, Object> metadata();

    /**
     * Returns the captured changes.
     *
     * @return the change set (never null)
     */
    ChangeSet changes();

    /**
     * Returns the error message if the action failed.
     *
     * @return the error message, or null if no error
     */
    String errorMessage();

    /**
     * Returns the schema version of this entry.
     *
     * @return the schema version
     */
    int schemaVersion();

    /**
     * Checks if this entry represents a successful action.
     *
     * @return true if outcome is SUCCESS
     */
    default boolean isSuccess() {
        return outcome() == AuditOutcome.SUCCESS;
    }

    /**
     * Checks if this entry represents a failed action.
     *
     * @return true if outcome is FAILURE
     */
    default boolean isFailure() {
        return outcome() == AuditOutcome.FAILURE;
    }

    /**
     * Checks if this entry has changes recorded.
     *
     * @return true if changes is not empty
     */
    default boolean hasChanges() {
        return changes() != null && !changes().isEmpty();
    }

    /**
     * Checks if this entry has an error message.
     *
     * @return true if errorMessage is not null
     */
    default boolean hasError() {
        return errorMessage() != null;
    }
}
