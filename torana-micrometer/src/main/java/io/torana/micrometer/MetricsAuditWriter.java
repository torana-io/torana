package io.torana.micrometer;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.torana.api.model.AuditEntry;
import io.torana.spi.AuditWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Decorator for {@link AuditWriter} that adds Micrometer metrics instrumentation.
 *
 * <p>This implementation wraps an existing audit writer and tracks:
 *
 * <ul>
 *   <li><strong>Write latency</strong> - Time taken to write audit entries (p50, p95, p99)
 *   <li><strong>Write throughput</strong> - Number of successful writes per second
 *   <li><strong>Error rate</strong> - Number of failed write attempts
 * </ul>
 *
 * <p>Metrics naming convention:
 *
 * <ul>
 *   <li>{@code torana.audit.write.latency} - Timer for write operations
 *   <li>{@code torana.audit.write.success} - Counter for successful writes
 *   <li>{@code torana.audit.write.error} - Counter for failed writes
 * </ul>
 *
 * <p>Tags can be optionally included for more detailed tracking:
 *
 * <ul>
 *   <li>{@code outcome} - SUCCESS or FAILURE (if detailed tags enabled)
 *   <li>{@code action} - Action name (if detailed tags enabled, may cause high cardinality)
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * AuditWriter delegate = new JdbcAuditWriter(jdbcTemplate, dialect, tableName);
 * AuditWriter metricsWriter = new MetricsAuditWriter(
 *     delegate,
 *     meterRegistry,
 *     false // includeDetailedTags
 * );
 * }</pre>
 */
public class MetricsAuditWriter implements AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(MetricsAuditWriter.class);

    private static final String METRIC_WRITE_LATENCY = "torana.audit.write.latency";
    private static final String METRIC_WRITE_SUCCESS = "torana.audit.write.success";
    private static final String METRIC_WRITE_ERROR = "torana.audit.write.error";
    private static final String METRIC_BATCH_WRITE_LATENCY = "torana.audit.batch.write.latency";
    private static final String METRIC_BATCH_WRITE_SUCCESS = "torana.audit.batch.write.success";
    private static final String METRIC_BATCH_WRITE_ERROR = "torana.audit.batch.write.error";

    private final AuditWriter delegate;
    private final MeterRegistry meterRegistry;
    private final boolean includeDetailedTags;

    private final Timer writeTimer;
    private final Timer batchWriteTimer;
    private final Counter writeSuccessCounter;
    private final Counter writeErrorCounter;
    private final Counter batchWriteSuccessCounter;
    private final Counter batchWriteErrorCounter;

    /**
     * Creates a new MetricsAuditWriter.
     *
     * @param delegate the underlying writer to delegate to
     * @param meterRegistry the meter registry for recording metrics
     * @param includeDetailedTags whether to include detailed tags (action, outcome) which may
     *     increase cardinality
     */
    public MetricsAuditWriter(
            AuditWriter delegate, MeterRegistry meterRegistry, boolean includeDetailedTags) {
        this.delegate = delegate;
        this.meterRegistry = meterRegistry;
        this.includeDetailedTags = includeDetailedTags;

        // Initialize meters
        this.writeTimer =
                Timer.builder(METRIC_WRITE_LATENCY)
                        .description("Time taken to write a single audit entry")
                        .tag("operation", "single")
                        .register(meterRegistry);

        this.batchWriteTimer =
                Timer.builder(METRIC_BATCH_WRITE_LATENCY)
                        .description("Time taken to write a batch of audit entries")
                        .tag("operation", "batch")
                        .register(meterRegistry);

        this.writeSuccessCounter =
                Counter.builder(METRIC_WRITE_SUCCESS)
                        .description("Number of successful audit entry writes")
                        .tag("operation", "single")
                        .register(meterRegistry);

        this.writeErrorCounter =
                Counter.builder(METRIC_WRITE_ERROR)
                        .description("Number of failed audit entry writes")
                        .tag("operation", "single")
                        .register(meterRegistry);

        this.batchWriteSuccessCounter =
                Counter.builder(METRIC_BATCH_WRITE_SUCCESS)
                        .description("Number of successful batch writes")
                        .tag("operation", "batch")
                        .register(meterRegistry);

        this.batchWriteErrorCounter =
                Counter.builder(METRIC_BATCH_WRITE_ERROR)
                        .description("Number of failed batch writes")
                        .tag("operation", "batch")
                        .register(meterRegistry);
    }

    @Override
    public void write(AuditEntry entry) {
        writeTimer.record(
                () -> {
                    try {
                        delegate.write(entry);
                        writeSuccessCounter.increment();

                        if (includeDetailedTags) {
                            recordDetailedMetrics(entry, true);
                        }
                    } catch (Exception e) {
                        writeErrorCounter.increment();

                        if (includeDetailedTags) {
                            recordDetailedMetrics(entry, false);
                        }

                        log.debug(
                                "Audit write failed for action={}, error={}",
                                entry.action().name(),
                                e.getMessage());
                        throw e;
                    }
                });
    }

    @Override
    public void writeBatch(List<AuditEntry> entries) {
        batchWriteTimer.record(
                () -> {
                    try {
                        delegate.writeBatch(entries);
                        batchWriteSuccessCounter.increment();

                        // Also increment individual success counter by batch size
                        writeSuccessCounter.increment(entries.size());

                        if (includeDetailedTags) {
                            entries.forEach(entry -> recordDetailedMetrics(entry, true));
                        }
                    } catch (Exception e) {
                        batchWriteErrorCounter.increment();

                        // Also increment individual error counter by batch size
                        writeErrorCounter.increment(entries.size());

                        if (includeDetailedTags && !entries.isEmpty()) {
                            // Record metrics for first entry in batch
                            recordDetailedMetrics(entries.get(0), false);
                        }

                        log.debug(
                                "Batch audit write failed for {} entries, error={}",
                                entries.size(),
                                e.getMessage());
                        throw e;
                    }
                });
    }

    /**
     * Records detailed metrics with additional tags (action, outcome).
     *
     * <p>This method is called only when {@code includeDetailedTags} is true. It creates
     * additional counters with action and outcome tags for more granular tracking.
     *
     * @param entry the audit entry
     * @param success whether the write was successful
     */
    private void recordDetailedMetrics(AuditEntry entry, boolean success) {
        String actionName = entry.action().name();
        String outcome = success ? "success" : "error";

        Counter.builder(success ? METRIC_WRITE_SUCCESS : METRIC_WRITE_ERROR)
                .description(
                        success
                                ? "Number of successful audit writes by action"
                                : "Number of failed audit writes by action")
                .tag("action", actionName)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }
}
