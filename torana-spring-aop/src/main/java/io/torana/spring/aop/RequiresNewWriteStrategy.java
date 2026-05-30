package io.torana.spring.aop;

import io.torana.api.model.AuditEntry;
import io.torana.spi.AuditWriter;

import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

/**
 * Writes audit entries in a new transaction using REQUIRES_NEW propagation.
 *
 * <p>This ensures that audit entries persist even if the business transaction rolls back. The
 * audit write runs in a completely separate transaction, which is:
 *
 * <ul>
 *   <li>Independent of the parent transaction state
 *   <li>Committed immediately after the write completes
 *   <li>Not affected by parent transaction rollback
 * </ul>
 *
 * <p>Use cases:
 *
 * <ul>
 *   <li>Compliance scenarios where audit trails must survive rollbacks
 *   <li>Failure audit entries that must be persisted even when business operation fails
 *   <li>Debugging scenarios where you need to capture attempt data
 * </ul>
 *
 * <p><strong>Performance Note:</strong> Creating new transactions has overhead. Use this strategy
 * judiciously, and consider using {@code AFTER_COMMIT} for success cases if rollback protection
 * isn't required.
 */
class RequiresNewWriteStrategy {

    private final AuditWriter delegate;
    private final PlatformTransactionManager transactionManager;

    /**
     * Creates a new REQUIRES_NEW write strategy.
     *
     * @param delegate the underlying audit writer
     * @param transactionManager the Spring transaction manager
     */
    RequiresNewWriteStrategy(AuditWriter delegate, PlatformTransactionManager transactionManager) {
        this.delegate = delegate;
        this.transactionManager = transactionManager;
    }

    /**
     * Writes the audit entry in a new transaction.
     *
     * <p>If the current thread has an active transaction, it is suspended. A new transaction is
     * created specifically for the audit write. After the write completes (successfully or with
     * error), the new transaction is committed or rolled back accordingly, and the parent
     * transaction (if any) is resumed.
     *
     * <p>Transaction behavior:
     *
     * <ul>
     *   <li>Propagation: REQUIRES_NEW (suspend parent, create new)
     *   <li>Name: "torana-audit-write" (for monitoring and debugging)
     *   <li>Read-only: false
     *   <li>Timeout: Inherits from transaction manager defaults
     * </ul>
     *
     * @param entry the audit entry to write
     * @throws Exception if the write or transaction management fails
     */
    void write(AuditEntry entry) throws Exception {
        DefaultTransactionDefinition txDef = new DefaultTransactionDefinition();
        txDef.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        txDef.setName("torana-audit-write");
        txDef.setReadOnly(false);

        TransactionStatus status = transactionManager.getTransaction(txDef);
        try {
            delegate.write(entry);
            transactionManager.commit(status);
        } catch (Exception e) {
            transactionManager.rollback(status);
            throw e;
        }
    }
}
