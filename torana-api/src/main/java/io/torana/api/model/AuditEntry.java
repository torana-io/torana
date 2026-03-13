package io.torana.api.model;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The immutable audit record persisted by Torana.
 *
 * <p>An AuditEntry captures all information about a single business action: what happened, who did
 * it, to what, when, and with what outcome.
 *
 * <p>Once created and persisted, an AuditEntry should never be modified (append-only model).
 *
 * <p>This is an immutable value object.
 */
public record AuditEntry(
        UUID id,
        AuditAction action,
        Instant occurredAt,
        AuditOutcome outcome,
        Actor actor,
        Tenant tenant,
        Target target,
        RequestContext requestContext,
        TraceContext traceContext,
        Map<String, Object> metadata,
        ChangeSet changes,
        String errorMessage,
        int schemaVersion) {

    /** The current schema version for audit entries. */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    public AuditEntry {
        Objects.requireNonNull(id, "Audit entry id must not be null");
        Objects.requireNonNull(action, "Action must not be null");
        Objects.requireNonNull(occurredAt, "OccurredAt must not be null");
        Objects.requireNonNull(outcome, "Outcome must not be null");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        changes = changes == null ? ChangeSet.empty() : changes;
        requestContext = requestContext == null ? RequestContext.empty() : requestContext;
        traceContext = traceContext == null ? TraceContext.empty() : traceContext;
    }

    /**
     * Creates an AuditEntry builder.
     *
     * @return a new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Checks if this entry represents a successful action.
     *
     * @return true if outcome is SUCCESS
     */
    public boolean isSuccess() {
        return outcome == AuditOutcome.SUCCESS;
    }

    /**
     * Checks if this entry represents a failed action.
     *
     * @return true if outcome is FAILURE
     */
    public boolean isFailure() {
        return outcome == AuditOutcome.FAILURE;
    }

    /**
     * Checks if this entry has changes recorded.
     *
     * @return true if changes is not empty
     */
    public boolean hasChanges() {
        return changes != null && !changes.isEmpty();
    }

    /**
     * Checks if this entry has an error message.
     *
     * @return true if errorMessage is not null
     */
    public boolean hasError() {
        return errorMessage != null;
    }

    /** Builder for AuditEntry. */
    public static class Builder {
        private UUID id = UUID.randomUUID();
        private AuditAction action;
        private Instant occurredAt = Instant.now();
        private AuditOutcome outcome = AuditOutcome.SUCCESS;
        private Actor actor;
        private Tenant tenant;
        private Target target;
        private RequestContext requestContext;
        private TraceContext traceContext;
        private Map<String, Object> metadata = Map.of();
        private ChangeSet changes;
        private String errorMessage;
        private int schemaVersion = CURRENT_SCHEMA_VERSION;

        public Builder id(UUID id) {
            this.id = id;
            return this;
        }

        public Builder action(AuditAction action) {
            this.action = action;
            return this;
        }

        public Builder action(String actionName) {
            this.action = AuditAction.of(actionName);
            return this;
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Builder outcome(AuditOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        public Builder actor(Actor actor) {
            this.actor = actor;
            return this;
        }

        public Builder tenant(Tenant tenant) {
            this.tenant = tenant;
            return this;
        }

        public Builder target(Target target) {
            this.target = target;
            return this;
        }

        public Builder target(String type, String id) {
            this.target = Target.of(type, id);
            return this;
        }

        public Builder requestContext(RequestContext requestContext) {
            this.requestContext = requestContext;
            return this;
        }

        public Builder traceContext(TraceContext traceContext) {
            this.traceContext = traceContext;
            return this;
        }

        public Builder metadata(Map<String, Object> metadata) {
            this.metadata = metadata;
            return this;
        }

        public Builder changes(ChangeSet changes) {
            this.changes = changes;
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Builder success() {
            this.outcome = AuditOutcome.SUCCESS;
            return this;
        }

        public Builder failure() {
            this.outcome = AuditOutcome.FAILURE;
            return this;
        }

        public Builder failure(String errorMessage) {
            this.outcome = AuditOutcome.FAILURE;
            this.errorMessage = errorMessage;
            return this;
        }

        public AuditEntry build() {
            return new AuditEntry(
                    id,
                    action,
                    occurredAt,
                    outcome,
                    actor,
                    tenant,
                    target,
                    requestContext,
                    traceContext,
                    metadata,
                    changes,
                    errorMessage,
                    schemaVersion);
        }
    }
}
