package io.torana.spring.aop;

import io.torana.api.model.AuditEntry;
import io.torana.api.model.AuditOutcome;
import io.torana.core.TransactionAwareWriter;
import io.torana.spi.AuditWriter;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Spring-aware transaction writer that persists audit entries based on configured policies.
 *
 * <p>This implementation supports three write policies:
 *
 * <ul>
 *   <li>{@code IMMEDIATE} - Write immediately, regardless of transaction state
 *   <li>{@code AFTER_COMMIT} - Write after transaction commits (default for success)
 *   <li>{@code REQUIRES_NEW} - Write in a new transaction (survives rollbacks)
 * </ul>
 *
 * <p>By default:
 *
 * <ul>
 *   <li>Success entries are written {@code AFTER_COMMIT} (prevents orphaned records)
 *   <li>Failure entries are written {@code IMMEDIATE} (captures the attempt)
 * </ul>
 *
 * <p>The {@code AFTER_COMMIT} policy ensures that audit entries are only written when the business
 * transaction commits successfully, preventing orphaned audit records for rolled-back
 * transactions.
 *
 * <p>The {@code REQUIRES_NEW} policy writes audit entries in a separate transaction, ensuring they
 * persist even if the parent transaction rolls back. This requires a {@code
 * PlatformTransactionManager}.
 */
public class SpringTransactionAwareWriter implements TransactionAwareWriter {

    private final AuditWriter delegate;
    private final WritePolicy successPolicy;
    private final WritePolicy failurePolicy;
    private final RequiresNewWriteStrategy requiresNewStrategy;

    /**
     * Creates a Spring transaction-aware writer with default policies.
     *
     * <p>Default policies:
     *
     * <ul>
     *   <li>Success: {@code AFTER_COMMIT}
     *   <li>Failure: {@code IMMEDIATE}
     * </ul>
     *
     * @param delegate the underlying audit writer
     */
    public SpringTransactionAwareWriter(AuditWriter delegate) {
        this(delegate, WritePolicy.AFTER_COMMIT, WritePolicy.IMMEDIATE, null);
    }

    /**
     * Creates a Spring transaction-aware writer with custom policies.
     *
     * @param delegate the underlying audit writer
     * @param successPolicy policy for writing success entries
     * @param failurePolicy policy for writing failure entries
     * @param transactionManager optional transaction manager (required for REQUIRES_NEW policy)
     */
    public SpringTransactionAwareWriter(
            AuditWriter delegate,
            WritePolicy successPolicy,
            WritePolicy failurePolicy,
            PlatformTransactionManager transactionManager) {
        this.delegate = delegate;
        this.successPolicy = successPolicy;
        this.failurePolicy = failurePolicy;
        this.requiresNewStrategy =
                transactionManager != null
                        ? new RequiresNewWriteStrategy(delegate, transactionManager)
                        : null;
    }

    @Override
    public void write(AuditEntry entry, AuditOutcome outcome) {
        WritePolicy policy = outcome == AuditOutcome.FAILURE ? failurePolicy : successPolicy;

        switch (policy) {
            case IMMEDIATE -> delegate.write(entry);
            case AFTER_COMMIT -> writeAfterCommit(entry);
            case REQUIRES_NEW -> writeRequiresNew(entry);
            case SKIP -> {
                // No-op: skip writing this entry
            }
        }
    }

    /**
     * Writes the audit entry after the current transaction commits.
     *
     * <p>If no transaction is active, falls back to immediate write.
     *
     * @param entry the audit entry to write
     */
    private void writeAfterCommit(AuditEntry entry) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new AfterCommitWriter(entry));
        } else {
            delegate.write(entry);
        }
    }

    /**
     * Writes the audit entry in a new transaction.
     *
     * @param entry the audit entry to write
     * @throws IllegalStateException if REQUIRES_NEW policy is used without a transaction manager
     */
    private void writeRequiresNew(AuditEntry entry) {
        if (requiresNewStrategy == null) {
            throw new IllegalStateException(
                    "REQUIRES_NEW write policy requires PlatformTransactionManager. "
                            + "Ensure a transaction manager bean is configured, or use IMMEDIATE "
                            + "or AFTER_COMMIT policy instead.");
        }

        try {
            requiresNewStrategy.write(entry);
        } catch (Exception e) {
            if (e instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new RuntimeException("Failed to write audit entry in new transaction", e);
        }
    }

    /**
     * Transaction synchronization that writes the audit entry after commit.
     *
     * <p>This inner class is registered with Spring's {@code TransactionSynchronizationManager} to
     * execute the audit write after the business transaction commits successfully.
     */
    private class AfterCommitWriter implements TransactionSynchronization {
        private final AuditEntry entry;

        AfterCommitWriter(AuditEntry entry) {
            this.entry = entry;
        }

        @Override
        public void afterCommit() {
            delegate.write(entry);
        }

        @Override
        public void afterCompletion(int status) {
            // Only write on commit (status == STATUS_COMMITTED)
            // afterCommit() is called before afterCompletion for committed transactions
        }
    }
}
