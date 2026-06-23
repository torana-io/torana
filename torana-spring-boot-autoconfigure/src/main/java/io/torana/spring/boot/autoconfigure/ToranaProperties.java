package io.torana.spring.boot.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration properties for Torana audit trail. */
@ConfigurationProperties(prefix = "torana")
public class ToranaProperties {

    private boolean enabled = true;
    private String tableName = "audit_entries";
    private SchemaMode schemaMode = SchemaMode.NONE;
    private RedactionProperties redaction = new RedactionProperties();
    private SnapshotProperties snapshot = new SnapshotProperties();
    private MetricsProperties metrics = new MetricsProperties();

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
    public MetricsProperties getMetrics() { return metrics; }
    public void setMetrics(MetricsProperties metrics) { this.metrics = metrics; }

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

    /** Metrics configuration for monitoring audit operations. */
    public static class MetricsProperties {

        /** Whether to enable Micrometer metrics collection for audit operations. */
        private boolean enabled = true;

        /**
         * Whether to include detailed tags (action name, outcome) in metrics.
         *
         * <p>Enabling this provides more granular metrics but may increase cardinality
         * significantly, especially if you have many unique action names.
         */
        private boolean includeDetailedTags = false;

        /**
         * Time window in seconds for health check statistics.
         *
         * <p>The health indicator tracks error rates within this window.
         */
        private int healthCheckWindowSeconds = 60;

        /**
         * Error rate threshold (0.0 to 1.0) that triggers a health check warning.
         *
         * <p>Default: 0.1 (10% error rate)
         */
        private double errorRateThreshold = 0.1;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isIncludeDetailedTags() { return includeDetailedTags; }
        public void setIncludeDetailedTags(boolean includeDetailedTags) { this.includeDetailedTags = includeDetailedTags; }
        public int getHealthCheckWindowSeconds() { return healthCheckWindowSeconds; }
        public void setHealthCheckWindowSeconds(int s) { this.healthCheckWindowSeconds = s; }
        public double getErrorRateThreshold() { return errorRateThreshold; }
        public void setErrorRateThreshold(double t) { this.errorRateThreshold = t; }
    }
}
