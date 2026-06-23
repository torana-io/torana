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
     *
     * <ul>
     *   <li>{@code none} - No schema initialization (default, use Flyway/Liquibase)
     *   <li>{@code create} - Create schema if it doesn't exist (development)
     *   <li>{@code create-drop} - Create schema on startup, drop on shutdown (testing)
     * </ul>
     */
    private SchemaMode schemaMode = SchemaMode.NONE;

    /** Redaction configuration. */
    private RedactionProperties redaction = new RedactionProperties();

    /** Snapshot configuration for change tracking. */
    private SnapshotProperties snapshot = new SnapshotProperties();

    /** Transaction configuration for audit writes. */
    private TransactionProperties transaction = new TransactionProperties();

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getTableName() { return tableName; }
    public void setTableName(String tableName) { this.tableName = tableName; }

    public SchemaMode getSchemaMode() { return schemaMode; }
    public void setSchemaMode(SchemaMode schemaMode) { this.schemaMode = schemaMode; }

    public RedactionProperties getRedaction() { return redaction; }
    public void setRedaction(RedactionProperties redaction) { this.redaction = redaction; }

    public SnapshotProperties getSnapshot() { return snapshot; }
    public void setSnapshot(SnapshotProperties snapshot) { this.snapshot = snapshot; }

    public TransactionProperties getTransaction() { return transaction; }
    public void setTransaction(TransactionProperties transaction) { this.transaction = transaction; }

    public enum SchemaMode { NONE, CREATE, CREATE_DROP }

    public static class RedactionProperties {
        private boolean enabled = true;
        private String[] patterns = {
            "(?i)password", "(?i)secret", "(?i)token", "(?i)credential",
            "(?i)ssn", "(?i)social.?security", "(?i)credit.?card"
        };
        private String placeholder = "[REDACTED]";
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String[] getPatterns() { return patterns; }
        public void setPatterns(String[] patterns) { this.patterns = patterns; }
        public String getPlaceholder() { return placeholder; }
        public void setPlaceholder(String placeholder) { this.placeholder = placeholder; }
    }

    public static class SnapshotProperties {
        private int maxDepth = 3;
        public int getMaxDepth() { return maxDepth; }
        public void setMaxDepth(int maxDepth) { this.maxDepth = maxDepth; }
    }

    public static class TransactionProperties {
        private WritePolicy successWritePolicy = WritePolicy.AFTER_COMMIT;
        private WritePolicy failureWritePolicy = WritePolicy.IMMEDIATE;
        private AuditErrorPolicy auditErrorPolicy = AuditErrorPolicy.LOG_AND_CONTINUE;

        public WritePolicy getSuccessWritePolicy() { return successWritePolicy; }
        public void setSuccessWritePolicy(WritePolicy p) { this.successWritePolicy = p; }
        public WritePolicy getFailureWritePolicy() { return failureWritePolicy; }
        public void setFailureWritePolicy(WritePolicy p) { this.failureWritePolicy = p; }
        public AuditErrorPolicy getAuditErrorPolicy() { return auditErrorPolicy; }
        public void setAuditErrorPolicy(AuditErrorPolicy p) { this.auditErrorPolicy = p; }
    }
}
