package io.torana.spi;

import io.torana.api.model.AuditEntry;

/**
 * SPI for handling audit processing errors.
 *
 * <p>Implementations of this interface can customize how audit errors are handled, such as logging
 * to external systems, reporting to monitoring, or deciding whether to fail the business
 * transaction.
 *
 * <p>This handler is only invoked when {@code AuditErrorPolicy.CALLBACK} is configured. If no
 * handler is registered, the policy falls back to {@code LOG_AND_CONTINUE} behavior.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * @Component
 * public class CustomAuditErrorHandler implements AuditErrorHandler {
 *     private final MetricRegistry metrics;
 *
 *     @Override
 *     public void handleError(AuditEntry entry, Exception error, ErrorPhase phase) {
 *         // Log to external monitoring
 *         metrics.counter("audit.errors", "phase", phase.name()).increment();
 *
 *         // Fail transaction for critical phases only
 *         if (phase == ErrorPhase.PERSISTENCE) {
 *             throw new AuditException("Critical audit failure", error);
 *         }
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface AuditErrorHandler {

    /**
     * Handles an error that occurred during audit processing.
     *
     * <p>The handler can:
     *
     * <ul>
     *   <li>Log the error to external systems
     *   <li>Send alerts or notifications
     *   <li>Decide whether to fail the transaction (by throwing an exception)
     *   <li>Implement custom retry logic
     * </ul>
     *
     * <p><strong>Important:</strong> If this method throws an exception, the business transaction
     * will be rolled back (if still active). To allow the business operation to continue, this
     * method should not throw.
     *
     * @param entry the audit entry (may be null if error occurred before entry creation)
     * @param error the exception that occurred during audit processing
     * @param phase the phase where the error occurred
     * @throws Exception if the business transaction should fail
     */
    void handleError(AuditEntry entry, Exception error, ErrorPhase phase) throws Exception;

    /**
     * Phase of audit processing where an error occurred.
     *
     * <p>This helps error handlers implement phase-specific logic, such as:
     *
     * <ul>
     *   <li>Treating persistence errors as critical but collection errors as warnings
     *   <li>Implementing different retry strategies per phase
     *   <li>Routing errors to different monitoring channels based on phase
     * </ul>
     */
    enum ErrorPhase {
        /**
         * Error during context collection.
         *
         * <p>Occurs when resolvers (actor, tenant, request context, trace) fail to collect
         * contextual information.
         *
         * <p>Common causes:
         *
         * <ul>
         *   <li>Spring Security context not available
         *   <li>Request context unavailable (async/background thread)
         *   <li>Custom resolver threw exception
         * </ul>
         */
        COLLECTION,

        /**
         * Error during audit entry creation.
         *
         * <p>Occurs when transforming the audit context into an immutable {@code AuditEntry} fails.
         *
         * <p>Common causes:
         *
         * <ul>
         *   <li>Snapshot provider threw exception
         *   <li>SpEL expression evaluation failed
         *   <li>Invalid configuration (e.g., missing required fields)
         * </ul>
         */
        CREATION,

        /**
         * Error during redaction.
         *
         * <p>Occurs when the redaction policy fails to process the audit entry.
         *
         * <p>Common causes:
         *
         * <ul>
         *   <li>JSON serialization/deserialization error
         *   <li>Regex pattern compilation error
         *   <li>Custom redaction policy bug
         * </ul>
         */
        REDACTION,

        /**
         * Error during persistence.
         *
         * <p>Occurs when writing the audit entry to storage fails.
         *
         * <p>Common causes:
         *
         * <ul>
         *   <li>Database connection unavailable
         *   <li>Table doesn't exist (schema not initialized)
         *   <li>Transaction timeout or deadlock
         *   <li>Disk full or write permission denied
         * </ul>
         */
        PERSISTENCE
    }
}
