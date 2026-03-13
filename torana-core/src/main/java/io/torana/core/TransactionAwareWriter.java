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

    /** Write policy options. */
    enum WritePolicy {
        /** Write the entry immediately. */
        IMMEDIATE,

        /** Write the entry after transaction commit. */
        AFTER_COMMIT,

        /** Skip writing the entry. */
        SKIP
    }
}
