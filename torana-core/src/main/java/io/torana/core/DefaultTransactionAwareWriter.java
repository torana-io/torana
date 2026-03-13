package io.torana.core;

import io.torana.api.model.AuditEntry;
import io.torana.api.model.AuditOutcome;
import io.torana.spi.AuditWriter;

/**
 * Default implementation of TransactionAwareWriter without Spring support.
 *
 * <p>This implementation writes entries according to the configured policies, but does not support
 * after-commit semantics without Spring's transaction management.
 *
 * <p>For Spring applications, use SpringTransactionAwareWriter instead.
 */
public class DefaultTransactionAwareWriter implements TransactionAwareWriter {

    private final AuditWriter writer;
    private final WritePolicy successPolicy;
    private final WritePolicy failurePolicy;

    public DefaultTransactionAwareWriter(AuditWriter writer) {
        this(writer, WritePolicy.IMMEDIATE, WritePolicy.IMMEDIATE);
    }

    public DefaultTransactionAwareWriter(
            AuditWriter writer, WritePolicy successPolicy, WritePolicy failurePolicy) {
        this.writer = writer;
        this.successPolicy = successPolicy;
        this.failurePolicy = failurePolicy;
    }

    @Override
    public void write(AuditEntry entry, AuditOutcome outcome) {
        WritePolicy policy = outcome == AuditOutcome.FAILURE ? failurePolicy : successPolicy;

        switch (policy) {
            case IMMEDIATE -> writer.write(entry);
            case AFTER_COMMIT -> {
                // In plain Java, after-commit behaves like immediate
                // Spring integration provides proper after-commit support
                writer.write(entry);
            }
            case SKIP -> {
                // Do nothing
            }
        }
    }

    public WritePolicy getSuccessPolicy() {
        return successPolicy;
    }

    public WritePolicy getFailurePolicy() {
        return failurePolicy;
    }
}
