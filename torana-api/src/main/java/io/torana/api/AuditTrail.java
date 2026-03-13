package io.torana.api;

/**
 * Main programmatic entry point for recording audit events.
 *
 * <p>Use this interface when you need explicit control over auditing, rather than the
 * annotation-based approach with {@link AuditedAction}.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * auditTrail.record(
 *     AuditRecord.builder()
 *         .action("invoice.approved")
 *         .targetType("Invoice")
 *         .targetId(invoiceId)
 *         .metadata("reason", reason)
 *         .build()
 * );
 * }</pre>
 *
 * <p>For scoped auditing with before/after snapshots:
 *
 * <pre>{@code
 * try (AuditScope scope = auditTrail.beginScope("order.updated")) {
 *     scope.target("Order", orderId)
 *          .beforeSnapshot(order);
 *     // perform business logic
 *     scope.afterSnapshot(order);
 * } // audit entry is created on close
 * }</pre>
 */
public interface AuditTrail {

    /**
     * Records an audit event built programmatically.
     *
     * <p>The record will be enriched with context information (actor, tenant, request, trace) from
     * registered resolvers.
     *
     * @param record the audit record to persist
     */
    void record(AuditRecord record);

    /**
     * Creates a scoped context for auditing within a code block.
     *
     * <p>The scope implements {@link AutoCloseable}, so it can be used with try-with-resources. The
     * audit entry is persisted when the scope is closed.
     *
     * @param action the action name (e.g., "order.cancelled")
     * @return a new AuditScope
     */
    AuditScope beginScope(String action);

    /**
     * Creates a query for retrieving audit entries.
     *
     * @return a new AuditQuery
     */
    AuditQuery query();
}
