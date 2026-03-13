package io.torana.spring.aop;

import io.torana.api.model.AuditEntry;
import io.torana.api.model.AuditOutcome;
import io.torana.core.TransactionAwareWriter;
import io.torana.spi.AuditWriter;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Spring-aware transaction writer that persists audit entries after successful transaction commit.
 *
 * <p>This ensures that audit entries are only written when the business transaction commits
 * successfully, preventing orphaned audit records for rolled-back transactions.
 *
 * <p>For failed operations (FAILURE outcome), entries are written immediately since there's no
 * transaction to wait for.
 */
public class SpringTransactionAwareWriter implements TransactionAwareWriter {

    private final AuditWriter delegate;

    public SpringTransactionAwareWriter(AuditWriter delegate) {
        this.delegate = delegate;
    }

    @Override
    public void write(AuditEntry entry, AuditOutcome outcome) {
        if (outcome == AuditOutcome.FAILURE) {
            // Write failures immediately
            delegate.write(entry);
            return;
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Register to write after commit
            TransactionSynchronizationManager.registerSynchronization(new AfterCommitWriter(entry));
        } else {
            // No active transaction, write immediately
            delegate.write(entry);
        }
    }

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
