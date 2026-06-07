package io.torana.core;

import io.torana.api.model.AuditEntry;
import io.torana.api.model.AuditOutcome;

/**
 * Handles transaction semantics for audit writes.
 *
 * <p>This interface abstracts the transaction-aware writing logic, allowing different
 * implementations for different frameworks (plain Java, Spring, etc.).
 */
public interface TransactionAwareWriter {

    /**
     * Writes an audit entry with transaction awareness.
     *
     * <p>Depending on the configuration and outcome:
     *
     * <ul>
     *   <li>Success entries may be written after transaction commit
     *   <li>Failure entries may be written immediately
     * </ul>
     *
     * @param entry the audit entry to write
     * @param outcome the outcome of the action
     */
    void write(AuditEntry entry, AuditOutcome outcome);

    /** Write policy options for controlling when and how audit entries are persisted. */
    enum WritePolicy {
        /** Write the entry immediately, regardless of transaction state. */
        IMMEDIATE,

        /**
         * Write the entry after the current transaction commits.
         *
         * <p>If the transaction rolls back, the entry is not written. If no transaction is active,
         * falls back to {@code IMMEDIATE}.
         */
        AFTER_COMMIT,

        /**
         * Write the entry in a new transaction using REQUIRES_NEW propagation.
         *
         * <p>This ensures the audit entry persists even if the parent transaction rolls back. The
         * audit write runs in a completely separate transaction.
         *
         * <p><strong>Note:</strong> This policy requires a {@code PlatformTransactionManager}
         * (Spring only). Non-Spring implementations will throw {@code
         * UnsupportedOperationException}.
         */
        REQUIRES_NEW,

        /** Skip writing the entry (useful for testing or selective audit disabling). */
        SKIP
    }
}
