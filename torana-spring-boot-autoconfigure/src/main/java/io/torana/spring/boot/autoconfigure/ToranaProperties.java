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
    private ResilienceProperties resilience = new ResilienceProperties();

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
    public ResilienceProperties getResilience() { return resilience; }
    public void setResilience(ResilienceProperties resilience) { this.resilience = resilience; }

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

    /** Resilience configuration for error recovery patterns. */
    public static class ResilienceProperties {

        private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();
        private RetryProperties retry = new RetryProperties();
        private FallbackProperties fallback = new FallbackProperties();

        public CircuitBreakerProperties getCircuitBreaker() { return circuitBreaker; }
        public void setCircuitBreaker(CircuitBreakerProperties cb) { this.circuitBreaker = cb; }
        public RetryProperties getRetry() { return retry; }
        public void setRetry(RetryProperties retry) { this.retry = retry; }
        public FallbackProperties getFallback() { return fallback; }
        public void setFallback(FallbackProperties fallback) { this.fallback = fallback; }

        /** Circuit breaker configuration. */
        public static class CircuitBreakerProperties {
            private boolean enabled = false;
            private int failureRateThreshold = 50;
            private int minimumNumberOfCalls = 10;
            private int waitDurationInOpenStateSeconds = 60;
            private int permittedNumberOfCallsInHalfOpenState = 5;
            private int slidingWindowSize = 100;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public int getFailureRateThreshold() { return failureRateThreshold; }
            public void setFailureRateThreshold(int t) { this.failureRateThreshold = t; }
            public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
            public void setMinimumNumberOfCalls(int n) { this.minimumNumberOfCalls = n; }
            public int getWaitDurationInOpenStateSeconds() { return waitDurationInOpenStateSeconds; }
            public void setWaitDurationInOpenStateSeconds(int s) { this.waitDurationInOpenStateSeconds = s; }
            public int getPermittedNumberOfCallsInHalfOpenState() { return permittedNumberOfCallsInHalfOpenState; }
            public void setPermittedNumberOfCallsInHalfOpenState(int n) { this.permittedNumberOfCallsInHalfOpenState = n; }
            public int getSlidingWindowSize() { return slidingWindowSize; }
            public void setSlidingWindowSize(int s) { this.slidingWindowSize = s; }
        }

        /** Retry configuration. */
        public static class RetryProperties {
            private boolean enabled = false;
            private int maxAttempts = 3;
            private long waitDurationMillis = 1000;
            private double exponentialBackoffMultiplier = 2.0;

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public int getMaxAttempts() { return maxAttempts; }
            public void setMaxAttempts(int n) { this.maxAttempts = n; }
            public long getWaitDurationMillis() { return waitDurationMillis; }
            public void setWaitDurationMillis(long ms) { this.waitDurationMillis = ms; }
            public double getExponentialBackoffMultiplier() { return exponentialBackoffMultiplier; }
            public void setExponentialBackoffMultiplier(double m) { this.exponentialBackoffMultiplier = m; }
        }

        /** Fallback configuration. */
        public static class FallbackProperties {
            private boolean enabled = false;
            private FallbackType type = FallbackType.LOGGING;
            private String fileBasedDirectory = "/var/lib/torana/fallback";

            public boolean isEnabled() { return enabled; }
            public void setEnabled(boolean enabled) { this.enabled = enabled; }
            public FallbackType getType() { return type; }
            public void setType(FallbackType type) { this.type = type; }
            public String getFileBasedDirectory() { return fileBasedDirectory; }
            public void setFileBasedDirectory(String dir) { this.fileBasedDirectory = dir; }

            public enum FallbackType { LOGGING, FILE_BASED, CUSTOM }
        }
    }
}
