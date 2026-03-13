package io.torana.api;

import io.torana.api.model.AuditOutcome;

/**
 * A scoped audit context that captures before/after state.
 *
 * <p>AuditScope implements {@link AutoCloseable} to be used with try-with-resources. The audit
 * entry is persisted when the scope is closed.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * try (AuditScope scope = auditTrail.beginScope("order.updated")) {
 *     scope.target("Order", orderId)
 *          .beforeSnapshot(order)
 *          .metadata("priority", "high");
 *
 *     // perform business logic
 *     order.updateStatus(newStatus);
 *
 *     scope.afterSnapshot(order);
 * } // audit entry is created on close
 * }</pre>
 *
 * <p>If an exception occurs, call {@link #error(Throwable)} before the scope closes to record the
 * failure.
 */
public interface AuditScope extends AutoCloseable {

    /**
     * Sets the target for this audit entry.
     *
     * @param type the target type (e.g., "Order")
     * @param id the target identifier
     * @return this scope for chaining
     */
    AuditScope target(String type, String id);

    /**
     * Sets the target with a display name.
     *
     * @param type the target type
     * @param id the target identifier
     * @param displayName the display name
     * @return this scope for chaining
     */
    AuditScope target(String type, String id, String displayName);

    /**
     * Adds a metadata entry.
     *
     * @param key the metadata key
     * @param value the metadata value
     * @return this scope for chaining
     */
    AuditScope metadata(String key, Object value);

    /**
     * Captures a before snapshot of the given object.
     *
     * <p>This should be called before the business logic executes.
     *
     * @param state the object to snapshot
     * @return this scope for chaining
     */
    AuditScope beforeSnapshot(Object state);

    /**
     * Captures an after snapshot of the given object.
     *
     * <p>This should be called after the business logic executes. The difference between before and
     * after snapshots will be computed and stored in the audit entry.
     *
     * @param state the object to snapshot
     * @return this scope for chaining
     */
    AuditScope afterSnapshot(Object state);

    /**
     * Sets the outcome of this audit entry.
     *
     * @param outcome the outcome
     * @return this scope for chaining
     */
    AuditScope outcome(AuditOutcome outcome);

    /**
     * Records an error that occurred during the scoped operation.
     *
     * <p>This sets the outcome to FAILURE and captures the error message.
     *
     * @param error the exception that occurred
     * @return this scope for chaining
     */
    AuditScope error(Throwable error);

    /**
     * Records an error message without an exception.
     *
     * @param errorMessage the error message
     * @return this scope for chaining
     */
    AuditScope error(String errorMessage);

    /**
     * Completes the scope and persists the audit entry.
     *
     * <p>This is called automatically when using try-with-resources.
     */
    @Override
    void close();
}
