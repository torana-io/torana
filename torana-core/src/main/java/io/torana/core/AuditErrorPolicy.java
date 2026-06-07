package io.torana.core;

/**
 * Defines how errors during audit processing should be handled.
 *
 * <p>When audit processing fails (during entry creation or persistence), this policy determines
 * whether the business operation should be affected.
 *
 * <p>Error phases include:
 *
 * <ul>
 *   <li>COLLECTION - Error during context collection (resolvers)
 *   <li>CREATION - Error during audit entry creation
 *   <li>REDACTION - Error during sensitive data redaction
 *   <li>PERSISTENCE - Error during audit write to storage
 * </ul>
 */
public enum AuditErrorPolicy {

    /**
     * Log the error and allow the business operation to continue.
     *
     * <p>This is the default and safest option - audit failures don't affect business operations.
     * Errors are logged at ERROR level for operational visibility.
     *
     * <p>Use when:
     *
     * <ul>
     *   <li>Audit is important for observability but not critical for correctness
     *   <li>Business operations must complete regardless of audit system health
     *   <li>Audit failures are monitored through log aggregation
     * </ul>
     */
    LOG_AND_CONTINUE,

    /**
     * Fail the business transaction when audit processing fails.
     *
     * <p>The audit error is propagated, causing the business transaction to roll back. This ensures
     * that no business operation completes without a corresponding audit entry.
     *
     * <p>Use when:
     *
     * <ul>
     *   <li>Audit is mandatory for compliance or regulatory requirements
     *   <li>Business operations without audit trails are considered invalid
     *   <li>Audit integrity is more important than availability
     * </ul>
     *
     * <p><strong>Warning:</strong> When using {@code AFTER_COMMIT} write policy, errors that occur
     * after the business transaction has already committed cannot be rolled back. Consider using
     * {@code REQUIRES_NEW} write policy for critical audit scenarios.
     */
    FAIL_TRANSACTION,

    /**
     * Invoke a custom callback handler to decide what to do.
     *
     * <p>Allows application-specific error handling logic through an {@code AuditErrorHandler}
     * implementation. The callback can:
     *
     * <ul>
     *   <li>Log to external monitoring systems
     *   <li>Implement custom retry logic
     *   <li>Decide whether to fail the transaction based on error type
     *   <li>Send alerts or notifications
     * </ul>
     *
     * <p>If no {@code AuditErrorHandler} is configured, this policy falls back to {@code
     * LOG_AND_CONTINUE} behavior.
     *
     * <p>Use when:
     *
     * <ul>
     *   <li>Custom error handling logic is required
     *   <li>Different errors should be handled differently
     *   <li>Integration with external monitoring or alerting systems is needed
     * </ul>
     */
    CALLBACK
}
