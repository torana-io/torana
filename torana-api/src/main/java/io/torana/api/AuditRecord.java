package io.torana.api;

import io.torana.api.model.AuditOutcome;
import io.torana.api.model.ChangeSet;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Builder for programmatic audit recording.
 *
 * <p>Use this class to construct an audit record for the programmatic API.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * AuditRecord record = AuditRecord.builder()
 *     .action("invoice.approved")
 *     .targetType("Invoice")
 *     .targetId(invoiceId)
 *     .metadata("reason", reason)
 *     .build();
 *
 * auditTrail.record(record);
 * }</pre>
 */
public final class AuditRecord {

    private final String action;
    private final String targetType;
    private final String targetId;
    private final String targetDisplayName;
    private final AuditOutcome outcome;
    private final Map<String, Object> metadata;
    private final ChangeSet changes;
    private final String errorMessage;

    private AuditRecord(Builder builder) {
        this.action = Objects.requireNonNull(builder.action, "Action must not be null");
        this.targetType = builder.targetType;
        this.targetId = builder.targetId;
        this.targetDisplayName = builder.targetDisplayName;
        this.outcome = builder.outcome;
        this.metadata = Map.copyOf(builder.metadata);
        this.changes = builder.changes;
        this.errorMessage = builder.errorMessage;
    }

    /**
     * Creates a new builder.
     *
     * @return a new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getAction() {
        return action;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getTargetDisplayName() {
        return targetDisplayName;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public ChangeSet getChanges() {
        return changes;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public boolean hasTarget() {
        return targetType != null && targetId != null;
    }

    public boolean hasChanges() {
        return changes != null && !changes.isEmpty();
    }

    /** Builder for AuditRecord. */
    public static class Builder {
        private final Map<String, Object> metadata = new HashMap<>();
        private String action;
        private String targetType;
        private String targetId;
        private String targetDisplayName;
        private AuditOutcome outcome = AuditOutcome.SUCCESS;
        private ChangeSet changes;
        private String errorMessage;

        /**
         * Sets the action name.
         *
         * @param action the action name (e.g., "order.cancelled")
         * @return this builder
         */
        public Builder action(String action) {
            this.action = action;
            return this;
        }

        /**
         * Sets the target type.
         *
         * @param targetType the target type (e.g., "Order")
         * @return this builder
         */
        public Builder targetType(String targetType) {
            this.targetType = targetType;
            return this;
        }

        /**
         * Sets the target ID.
         *
         * @param targetId the target identifier
         * @return this builder
         */
        public Builder targetId(String targetId) {
            this.targetId = targetId;
            return this;
        }

        /**
         * Sets the target ID from an object (converted to string).
         *
         * @param targetId the target identifier
         * @return this builder
         */
        public Builder targetId(Object targetId) {
            this.targetId = targetId != null ? targetId.toString() : null;
            return this;
        }

        /**
         * Sets both target type and ID.
         *
         * @param type the target type
         * @param id the target identifier
         * @return this builder
         */
        public Builder target(String type, String id) {
            this.targetType = type;
            this.targetId = id;
            return this;
        }

        /**
         * Sets target type, ID, and display name.
         *
         * @param type the target type
         * @param id the target identifier
         * @param displayName the display name
         * @return this builder
         */
        public Builder target(String type, String id, String displayName) {
            this.targetType = type;
            this.targetId = id;
            this.targetDisplayName = displayName;
            return this;
        }

        /**
         * Sets the target display name.
         *
         * @param displayName the display name
         * @return this builder
         */
        public Builder targetDisplayName(String displayName) {
            this.targetDisplayName = displayName;
            return this;
        }

        /**
         * Sets the outcome.
         *
         * @param outcome the audit outcome
         * @return this builder
         */
        public Builder outcome(AuditOutcome outcome) {
            this.outcome = outcome;
            return this;
        }

        /**
         * Marks the outcome as success.
         *
         * @return this builder
         */
        public Builder success() {
            this.outcome = AuditOutcome.SUCCESS;
            return this;
        }

        /**
         * Marks the outcome as failure.
         *
         * @return this builder
         */
        public Builder failure() {
            this.outcome = AuditOutcome.FAILURE;
            return this;
        }

        /**
         * Marks the outcome as failure with an error message.
         *
         * @param errorMessage the error message
         * @return this builder
         */
        public Builder failure(String errorMessage) {
            this.outcome = AuditOutcome.FAILURE;
            this.errorMessage = errorMessage;
            return this;
        }

        /**
         * Adds a metadata entry.
         *
         * @param key the metadata key
         * @param value the metadata value
         * @return this builder
         */
        public Builder metadata(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * Adds multiple metadata entries.
         *
         * @param metadata the metadata map
         * @return this builder
         */
        public Builder metadata(Map<String, Object> metadata) {
            this.metadata.putAll(metadata);
            return this;
        }

        /**
         * Sets the change set.
         *
         * @param changes the changes
         * @return this builder
         */
        public Builder changes(ChangeSet changes) {
            this.changes = changes;
            return this;
        }

        /**
         * Sets the error message.
         *
         * @param errorMessage the error message
         * @return this builder
         */
        public Builder errorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
            return this;
        }

        /**
         * Sets the error from an exception.
         *
         * @param error the exception
         * @return this builder
         */
        public Builder error(Throwable error) {
            this.outcome = AuditOutcome.FAILURE;
            this.errorMessage = error.getMessage();
            return this;
        }

        /**
         * Builds the AuditRecord.
         *
         * @return a new AuditRecord
         */
        public AuditRecord build() {
            return new AuditRecord(this);
        }
    }
}
