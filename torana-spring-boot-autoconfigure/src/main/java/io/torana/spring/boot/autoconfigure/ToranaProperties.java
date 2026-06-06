package io.torana.spring.boot.autoconfigure;

import io.torana.core.AuditErrorPolicy;
import io.torana.core.TransactionAwareWriter.WritePolicy;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for Torana audit trail. */
@ConfigurationProperties(prefix = "torana")
public class ToranaProperties {

    /** Whether to enable Torana audit trail. */
    private boolean enabled = true;

    /** The database table name for storing audit entries. */
    private String tableName = "audit_entries";

    /**
     * Schema initialization mode.
     * <ul>
     *   <li>{@code none} - No schema initialization (default, use Flyway/Liquibase)</li>
     *   <li>{@code create} - Create schema if it doesn't exist (development)</li>
     *   <li>{@code create-drop} - Create schema on startup, drop on shutdown (testing)</li>
     * </ul>
     */
    private SchemaMode schemaMode = SchemaMode.NONE;

    /** Redaction configuration. */
    private RedactionProperties redaction = new RedactionProperties();

    /** Snapshot configuration for change tracking. */
    private SnapshotProperties snapshot = new SnapshotProperties();

    /** Transaction configuration for audit writes. */
    private TransactionProperties transaction = new TransactionProperties();

    /** Metrics configuration for monitoring audit operations. */
    private MetricsProperties metrics = new MetricsProperties();

    /** Schema initialization modes. */
    public enum SchemaMode {
        /** No automatic schema creation. */
        NONE,
        /** Create schema if it doesn't exist. */
        CREATE,
        /** Create schema on startup, drop on shutdown. */
        CREATE_DROP
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTableName() {
        return tableName;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public SchemaMode getSchemaMode() {
        return schemaMode;
    }

    public void setSchemaMode(SchemaMode schemaMode) {
        this.schemaMode = schemaMode;
    }

    public RedactionProperties getRedaction() {
        return redaction;
    }

    public void setRedaction(RedactionProperties redaction) {
        this.redaction = redaction;
    }

    public SnapshotProperties getSnapshot() {
        return snapshot;
    }

    public void setSnapshot(SnapshotProperties snapshot) {
        this.snapshot = snapshot;
    }

    public TransactionProperties getTransaction() {
        return transaction;
    }

    public void setTransaction(TransactionProperties transaction) {
        this.transaction = transaction;
    }

    public MetricsProperties getMetrics() {
        return metrics;
    }

    public void setMetrics(MetricsProperties metrics) {
        this.metrics = metrics;
    }

    /** Redaction configuration. */
    public static class RedactionProperties {

        /** Whether to enable sensitive data redaction. */
        private boolean enabled = true;

        /** Patterns for field names to redact (regex). */
        private String[] patterns = {
            "(?i)password",
            "(?i)secret",
            "(?i)token",
            "(?i)credential",
            "(?i)ssn",
            "(?i)social.?security",
            "(?i)credit.?card"
        };

        /** The placeholder text for redacted values. */
        private String placeholder = "[REDACTED]";

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String[] getPatterns() {
            return patterns;
        }

        public void setPatterns(String[] patterns) {
            this.patterns = patterns;
        }

        public String getPlaceholder() {
            return placeholder;
        }

        public void setPlaceholder(String placeholder) {
            this.placeholder = placeholder;
        }
    }

    /** Snapshot configuration. */
    public static class SnapshotProperties {

        /** Maximum depth for object traversal during snapshots. */
        private int maxDepth = 3;

        public int getMaxDepth() {
            return maxDepth;
        }

        public void setMaxDepth(int maxDepth) {
            this.maxDepth = maxDepth;
        }
    }

    /** Transaction configuration for controlling audit write behavior. */
    public static class TransactionProperties {

        /**
         * Write policy for successful operations.
         *
         * <p>Controls when audit entries for successful operations are persisted:
         *
         * <ul>
         *   <li>{@code after_commit} (default) - Write after transaction commits, preventing
         *       orphaned audit records
         *   <li>{@code immediate} - Write immediately, even if transaction later rolls back
         *   <li>{@code requires_new} - Write in separate transaction, survives parent rollback
         * </ul>
         */
        private WritePolicy successWritePolicy = WritePolicy.AFTER_COMMIT;

        /**
         * Write policy for failed operations.
         *
         * <p>Controls when audit entries for failed operations are persisted:
         *
         * <ul>
         *   <li>{@code immediate} (default) - Write immediately to capture the attempt
         *   <li>{@code after_commit} - Write only if transaction commits (unusual for failures)
         *   <li>{@code requires_new} - Write in separate transaction, survives parent rollback
         * </ul>
         */
        private WritePolicy failureWritePolicy = WritePolicy.IMMEDIATE;

        /**
         * Error handling policy for audit processing failures.
         *
         * <p>Determines what happens when audit processing fails:
         *
         * <ul>
         *   <li>{@code log_and_continue} (default) - Log error, allow business operation to proceed
         *   <li>{@code fail_transaction} - Throw exception to fail the business transaction
         *   <li>{@code callback} - Invoke custom {@code AuditErrorHandler} to decide
         * </ul>
         */
        private AuditErrorPolicy auditErrorPolicy = AuditErrorPolicy.LOG_AND_CONTINUE;

        public WritePolicy getSuccessWritePolicy() {
            return successWritePolicy;
        }

        public void setSuccessWritePolicy(WritePolicy successWritePolicy) {
            this.successWritePolicy = successWritePolicy;
        }

        public WritePolicy getFailureWritePolicy() {
            return failureWritePolicy;
        }

        public void setFailureWritePolicy(WritePolicy failureWritePolicy) {
            this.failureWritePolicy = failureWritePolicy;
        }

        public AuditErrorPolicy getAuditErrorPolicy() {
            return auditErrorPolicy;
        }

        public void setAuditErrorPolicy(AuditErrorPolicy auditErrorPolicy) {
            this.auditErrorPolicy = auditErrorPolicy;
        }
    }

    /** Metrics configuration for monitoring audit operations. */
    public static class MetricsProperties {

        /** Whether to enable Micrometer metrics collection for audit operations. */
        private boolean enabled = true;

        /**
         * Whether to include detailed tags (action name, outcome) in metrics.
         *
         * <p>Enabling this provides more granular metrics but may increase cardinality
         * significantly, especially if you have many unique action names.
         *
         * <p>Consider enabling only in development or staging environments, or when you have
         * a small, controlled set of action names.
         */
        private boolean includeDetailedTags = false;

        /**
         * Time window in seconds for health check statistics.
         *
         * <p>The health indicator tracks error rates within this window to determine health status.
         */
        private int healthCheckWindowSeconds = 60;

        /**
         * Error rate threshold (0.0 to 1.0) that triggers a health check warning.
         *
         * <p>When the error rate exceeds this threshold within the health check window,
         * the health indicator will report a WARNING status.
         *
         * <p>Default: 0.1 (10% error rate)
         */
        private double errorRateThreshold = 0.1;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public boolean isIncludeDetailedTags() {
            return includeDetailedTags;
        }

        public void setIncludeDetailedTags(boolean includeDetailedTags) {
            this.includeDetailedTags = includeDetailedTags;
        }

        public int getHealthCheckWindowSeconds() {
            return healthCheckWindowSeconds;
        }

        public void setHealthCheckWindowSeconds(int healthCheckWindowSeconds) {
            this.healthCheckWindowSeconds = healthCheckWindowSeconds;
        }

        public double getErrorRateThreshold() {
            return errorRateThreshold;
        }

        public void setErrorRateThreshold(double errorRateThreshold) {
            this.errorRateThreshold = errorRateThreshold;
        }
    }
}
