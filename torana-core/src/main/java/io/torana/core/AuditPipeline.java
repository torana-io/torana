package io.torana.core;

import io.torana.api.model.AuditEntry;
import io.torana.spi.RedactionPolicy;

/**
 * The main audit processing pipeline.
 *
 * <p>This pipeline orchestrates the entire audit lifecycle:
 *
 * <ol>
 *   <li>Collect context from resolvers
 *   <li>Create the audit entry
 *   <li>Apply redaction
 *   <li>Persist the entry
 * </ol>
 */
public class AuditPipeline {

    private final ContextCollector contextCollector;
    private final AuditEntryFactory entryFactory;
    private final RedactionPolicy redactionPolicy;
    private final TransactionAwareWriter transactionAwareWriter;

    public AuditPipeline(
            ContextCollector contextCollector,
            AuditEntryFactory entryFactory,
            RedactionPolicy redactionPolicy,
            TransactionAwareWriter transactionAwareWriter) {
        this.contextCollector = contextCollector;
        this.entryFactory = entryFactory;
        this.redactionPolicy = redactionPolicy;
        this.transactionAwareWriter = transactionAwareWriter;
    }

    /**
     * Processes an audit context through the pipeline.
     *
     * @param context the audit context to process
     */
    public void process(AuditContext context) {
        contextCollector.collect(context);
        context.markCompleted();
        AuditEntry entry = entryFactory.create(context);
        AuditEntry redactedEntry = redactionPolicy != null ? redactionPolicy.apply(entry) : entry;
        transactionAwareWriter.write(redactedEntry, context.getOutcome());
    }
}
