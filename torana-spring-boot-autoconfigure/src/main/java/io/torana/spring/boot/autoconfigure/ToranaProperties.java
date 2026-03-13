package io.torana.spring.boot.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for Torana audit trail. */
@ConfigurationProperties(prefix = "torana")
public class ToranaProperties {

    /** Whether to enable Torana audit trail. */
    private boolean enabled = true;

    /** The database table name for storing audit entries. */
    private String tableName = "audit_entries";

    /** Redaction configuration. */
    private RedactionProperties redaction = new RedactionProperties();

    /** Snapshot configuration for change tracking. */
    private SnapshotProperties snapshot = new SnapshotProperties();

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
}
