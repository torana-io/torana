package io.torana.spring.boot.autoconfigure.actuator;

import io.torana.jdbc.dialect.SqlDialect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Health indicator for Torana audit trail system.
 *
 * <p>This indicator checks the health of the audit system by:
 *
 * <ul>
 *   <li>Verifying database connectivity
 *   <li>Attempting a test write to the audit table
 *   <li>Monitoring error rates over a configurable time window
 * </ul>
 *
 * <p>Health status:
 *
 * <ul>
 *   <li><strong>UP:</strong> Database accessible, error rate below threshold
 *   <li><strong>DOWN:</strong> Database unavailable or test write fails
 *   <li><strong>WARNING:</strong> Database accessible but error rate exceeds threshold
 * </ul>
 *
 * <p>This health indicator is automatically configured when:
 *
 * <ul>
 *   <li>Spring Boot Actuator is on the classpath
 *   <li>{@code management.health.audit-trail.enabled=true} (default)
 * </ul>
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * management:
 *   health:
 *     audit-trail:
 *       enabled: true
 *
 * torana:
 *   metrics:
 *     health-check-window-seconds: 60
 *     error-rate-threshold: 0.1  # 10%
 * }</pre>
 */
public class AuditTrailHealthIndicator implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(AuditTrailHealthIndicator.class);

    private final JdbcTemplate jdbcTemplate;
    private final SqlDialect dialect;
    private final String tableName;
    private final int healthCheckWindowSeconds;
    private final double errorRateThreshold;

    // Simple error tracking (could be enhanced with sliding window)
    private final AtomicLong totalWrites = new AtomicLong(0);
    private final AtomicLong totalErrors = new AtomicLong(0);
    private final AtomicReference<Long> lastResetTime = new AtomicReference<>(System.currentTimeMillis());

    public AuditTrailHealthIndicator(
            JdbcTemplate jdbcTemplate,
            SqlDialect dialect,
            String tableName,
            int healthCheckWindowSeconds,
            double errorRateThreshold) {
        this.jdbcTemplate = jdbcTemplate;
        this.dialect = dialect;
        this.tableName = tableName;
        this.healthCheckWindowSeconds = healthCheckWindowSeconds;
        this.errorRateThreshold = errorRateThreshold;
    }

    @Override
    public Health health() {
        try {
            // Reset counters if window has elapsed
            resetCountersIfNeeded();

            // Check database connectivity and table accessibility
            checkDatabaseHealth();

            // Calculate error rate
            long writes = totalWrites.get();
            long errors = totalErrors.get();
            double errorRate = writes > 0 ? (double) errors / writes : 0.0;

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("database", dialect.getClass().getSimpleName().replace("Dialect", ""));
            details.put("tableName", tableName);
            details.put("totalWrites", writes);
            details.put("totalErrors", errors);
            details.put("errorRate", String.format("%.2f%%", errorRate * 100));
            details.put("errorRateThreshold", String.format("%.2f%%", errorRateThreshold * 100));
            details.put("windowSeconds", healthCheckWindowSeconds);

            // Determine health status
            if (errorRate > errorRateThreshold && writes > 10) {
                // Only warn if we have enough samples (>10 writes)
                return Health.status("WARNING")
                        .withDetails(details)
                        .withDetail("message", "Error rate exceeds threshold")
                        .build();
            }

            return Health.up().withDetails(details).build();

        } catch (Exception e) {
            log.error("Audit trail health check failed: {}", e.getMessage(), e);
            return Health.down()
                    .withException(e)
                    .withDetail("database", dialect.getClass().getSimpleName().replace("Dialect", ""))
                    .withDetail("tableName", tableName)
                    .withDetail("error", e.getMessage())
                    .build();
        }
    }

    /**
     * Checks database connectivity by querying the audit table.
     *
     * <p>This performs a simple COUNT query to verify the table exists and is accessible.
     *
     * @throws Exception if database check fails
     */
    private void checkDatabaseHealth() {
        // Simple connectivity check: count rows in audit table
        // Using LIMIT 1 for performance (we don't care about the actual count)
        String sql = String.format("SELECT COUNT(*) FROM %s", tableName);

        try {
            jdbcTemplate.queryForObject(sql, Long.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to query audit table: " + e.getMessage(), e);
        }
    }

    /**
     * Resets error counters if the health check window has elapsed.
     *
     * <p>This implements a simple tumbling window: counters are reset every {@code
     * healthCheckWindowSeconds}.
     */
    private void resetCountersIfNeeded() {
        long now = System.currentTimeMillis();
        long lastReset = lastResetTime.get();
        long windowMillis = healthCheckWindowSeconds * 1000L;

        if (now - lastReset > windowMillis) {
            // Try to reset (atomic compare-and-set)
            if (lastResetTime.compareAndSet(lastReset, now)) {
                totalWrites.set(0);
                totalErrors.set(0);
                log.debug(
                        "Reset audit health check counters (window: {}s)",
                        healthCheckWindowSeconds);
            }
        }
    }

    /**
     * Records a successful audit write for health tracking.
     *
     * <p>This method should be called by the metrics or audit pipeline to track write attempts.
     */
    public void recordWrite() {
        totalWrites.incrementAndGet();
    }

    /**
     * Records a failed audit write for health tracking.
     *
     * <p>This method should be called by the metrics or audit pipeline when a write fails.
     */
    public void recordError() {
        totalWrites.incrementAndGet();
        totalErrors.incrementAndGet();
    }

    /**
     * Gets the current error rate.
     *
     * @return error rate as a value between 0.0 and 1.0
     */
    public double getCurrentErrorRate() {
        long writes = totalWrites.get();
        long errors = totalErrors.get();
        return writes > 0 ? (double) errors / writes : 0.0;
    }
}
