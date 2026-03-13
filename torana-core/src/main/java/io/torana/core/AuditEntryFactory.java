package io.torana.core;

import io.torana.api.model.AuditEntry;
import io.torana.api.model.ChangeSet;
import io.torana.spi.DiffEngine;

import java.util.UUID;

/**
 * Factory for creating AuditEntry instances from AuditContext.
 *
 * <p>This factory converts the mutable AuditContext into an immutable AuditEntry, computing changes
 * if snapshots are present.
 */
public class AuditEntryFactory {

    private final DiffEngine diffEngine;

    public AuditEntryFactory(DiffEngine diffEngine) {
        this.diffEngine = diffEngine;
    }

    /**
     * Creates an AuditEntry from the given context.
     *
     * @param context the audit context
     * @return a new immutable AuditEntry
     */
    public AuditEntry create(AuditContext context) {
        ChangeSet changes = computeChanges(context);

        return new AuditEntry(
                UUID.randomUUID(),
                context.getAction(),
                context.getOccurredAt(),
                context.getOutcome(),
                context.getActor(),
                context.getTenant(),
                context.getTarget(),
                context.getRequestContext(),
                context.getTraceContext(),
                context.getMetadata(),
                changes,
                context.getErrorMessage(),
                AuditEntry.CURRENT_SCHEMA_VERSION);
    }

    private ChangeSet computeChanges(AuditContext context) {
        if (!context.hasSnapshots()) {
            return ChangeSet.empty();
        }
        if (diffEngine == null) {
            return ChangeSet.empty();
        }
        return diffEngine.diff(context.getBeforeSnapshot(), context.getAfterSnapshot());
    }
}
