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

    /** Metrics configuration for monitoring audit operations. */
    private MetricsProperties metrics = new MetricsProperties();

    /** Resilience configuration for error recovery patterns. */
    private ResilienceProperties resilience = new ResilienceProperties();

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

    public ResilienceProperties getResilience() {
        return resilience;
    }

    public void setResilience(ResilienceProperties resilience) {
        this.resilience = resilience;
    }

    /** Schema initialization modes. */
    public enum SchemaMode {
        /** No automatic schema creation. */
        NONE,
        /** Create schema if it doesn't exist. */
        CREATE,
        /** Create schema on startup, drop on shutdown. */
        CREATE_DROP
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
         * <p>Consider enabling only in development or staging environments, or when you have a
         * small, controlled set of action names.
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
         * <p>When the error rate exceeds this threshold within the health check window, the health
         * indicator will report a WARNING status.
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

    /** Resilience configuration for error recovery patterns. */
    public static class ResilienceProperties {

        /** Circuit breaker configuration. */
        private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

        /** Retry configuration. */
        private RetryProperties retry = new RetryProperties();

        /** Fallback configuration. */
        private FallbackProperties fallback = new FallbackProperties();

        public CircuitBreakerProperties getCircuitBreaker() {
            return circuitBreaker;
        }

        public void setCircuitBreaker(CircuitBreakerProperties circuitBreaker) {
            this.circuitBreaker = circuitBreaker;
        }

        public RetryProperties getRetry() {
            return retry;
        }

        public void setRetry(RetryProperties retry) {
            this.retry = retry;
        }

        public FallbackProperties getFallback() {
            return fallback;
        }

        public void setFallback(FallbackProperties fallback) {
            this.fallback = fallback;
        }

        /** Circuit breaker configuration. */
        public static class CircuitBreakerProperties {

            /** Whether to enable circuit breaker pattern. */
            private boolean enabled = false;

            /**
             * Failure rate threshold (percentage) that triggers circuit breaker to open.
             *
             * <p>When the failure rate exceeds this threshold, the circuit opens and subsequent
             * calls are redirected to the fallback writer without attempting the primary writer.
             *
             * <p>Default: 50 (50% failure rate)
             */
            private int failureRateThreshold = 50;

            /**
             * Minimum number of calls required before the circuit breaker can calculate the failure
             * rate.
             *
             * <p>The circuit breaker won't open until at least this many calls have been recorded.
             *
             * <p>Default: 10
             */
            private int minimumNumberOfCalls = 10;

            /**
             * Time in seconds to wait before transitioning from OPEN to HALF_OPEN state.
             *
             * <p>During this wait period, all calls use the fallback writer. After the wait period,
             * the circuit transitions to HALF_OPEN and permits a limited number of test calls.
             *
             * <p>Default: 60 seconds
             */
            private int waitDurationInOpenStateSeconds = 60;

            /**
             * Number of permitted calls when the circuit is HALF_OPEN.
             *
             * <p>These calls test whether the primary writer has recovered. If they succeed, the
             * circuit closes. If they fail, the circuit reopens.
             *
             * <p>Default: 5
             */
            private int permittedNumberOfCallsInHalfOpenState = 5;

            /**
             * Size of the sliding window used to record the outcome of calls.
             *
             * <p>The circuit breaker uses this window to calculate the failure rate.
             *
             * <p>Default: 100
             */
            private int slidingWindowSize = 100;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getFailureRateThreshold() {
                return failureRateThreshold;
            }

            public void setFailureRateThreshold(int failureRateThreshold) {
                this.failureRateThreshold = failureRateThreshold;
            }

            public int getMinimumNumberOfCalls() {
                return minimumNumberOfCalls;
            }

            public void setMinimumNumberOfCalls(int minimumNumberOfCalls) {
                this.minimumNumberOfCalls = minimumNumberOfCalls;
            }

            public int getWaitDurationInOpenStateSeconds() {
                return waitDurationInOpenStateSeconds;
            }

            public void setWaitDurationInOpenStateSeconds(int waitDurationInOpenStateSeconds) {
                this.waitDurationInOpenStateSeconds = waitDurationInOpenStateSeconds;
            }

            public int getPermittedNumberOfCallsInHalfOpenState() {
                return permittedNumberOfCallsInHalfOpenState;
            }

            public void setPermittedNumberOfCallsInHalfOpenState(
                    int permittedNumberOfCallsInHalfOpenState) {
                this.permittedNumberOfCallsInHalfOpenState = permittedNumberOfCallsInHalfOpenState;
            }

            public int getSlidingWindowSize() {
                return slidingWindowSize;
            }

            public void setSlidingWindowSize(int slidingWindowSize) {
                this.slidingWindowSize = slidingWindowSize;
            }
        }

        /** Retry configuration. */
        public static class RetryProperties {

            /** Whether to enable retry pattern. */
            private boolean enabled = false;

            /**
             * Maximum number of retry attempts.
             *
             * <p>Total attempts = maxAttempts (including initial attempt).
             *
             * <p>Default: 3 (initial + 2 retries)
             */
            private int maxAttempts = 3;

            /**
             * Wait duration in milliseconds before the first retry.
             *
             * <p>Subsequent retries use exponential backoff based on this duration and the
             * exponentialBackoffMultiplier.
             *
             * <p>Default: 1000ms (1 second)
             */
            private long waitDurationMillis = 1000;

            /**
             * Multiplier for exponential backoff.
             *
             * <p>Each retry waits exponentially longer:
             *
             * <ul>
             *   <li>Retry 1: waitDurationMillis
             *   <li>Retry 2: waitDurationMillis * multiplier
             *   <li>Retry 3: waitDurationMillis * multiplier^2
             * </ul>
             *
             * <p>Default: 2.0
             */
            private double exponentialBackoffMultiplier = 2.0;

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public int getMaxAttempts() {
                return maxAttempts;
            }

            public void setMaxAttempts(int maxAttempts) {
                this.maxAttempts = maxAttempts;
            }

            public long getWaitDurationMillis() {
                return waitDurationMillis;
            }

            public void setWaitDurationMillis(long waitDurationMillis) {
                this.waitDurationMillis = waitDurationMillis;
            }

            public double getExponentialBackoffMultiplier() {
                return exponentialBackoffMultiplier;
            }

            public void setExponentialBackoffMultiplier(double exponentialBackoffMultiplier) {
                this.exponentialBackoffMultiplier = exponentialBackoffMultiplier;
            }
        }

        /** Fallback configuration. */
        public static class FallbackProperties {

            /** Whether to enable fallback writer when circuit breaker is open. */
            private boolean enabled = false;

            /**
             * Type of fallback mechanism.
             *
             * <ul>
             *   <li>{@code logging} - Write to structured logs
             *   <li>{@code file_based} - Write to local files for replay
             *   <li>{@code custom} - Use custom FallbackAuditWriter bean
             * </ul>
             */
            private FallbackType type = FallbackType.LOGGING;

            /**
             * Directory path for file-based fallback storage.
             *
             * <p>Only used when type is {@code file_based}.
             *
             * <p>The directory must exist and be writable by the application.
             *
             * <p>Default: /var/lib/torana/fallback
             */
            private String fileBasedDirectory = "/var/lib/torana/fallback";

            public boolean isEnabled() {
                return enabled;
            }

            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            public FallbackType getType() {
                return type;
            }

            public void setType(FallbackType type) {
                this.type = type;
            }

            public String getFileBasedDirectory() {
                return fileBasedDirectory;
            }

            public void setFileBasedDirectory(String fileBasedDirectory) {
                this.fileBasedDirectory = fileBasedDirectory;
            }

            /** Fallback types. */
            public enum FallbackType {
                /** Write audit entries to structured logs. */
                LOGGING,
                /** Write audit entries to local files for replay. */
                FILE_BASED,
                /** Use a custom FallbackAuditWriter bean. */
                CUSTOM
            }
        }
    }
}
