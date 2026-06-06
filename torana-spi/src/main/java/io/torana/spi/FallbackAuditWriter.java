package io.torana.spi;

import io.torana.api.AuditEntry;

import java.util.List;

/**
 * SPI interface for fallback audit writers.
 *
 * <p>Fallback writers are used when the primary audit writer fails, typically activated by a
 * circuit breaker. They provide alternative storage mechanisms that can buffer audit entries until
 * the primary writer recovers.
 *
 * <p>Common implementations:
 *
 * <ul>
 *   <li><strong>Logging:</strong> Write audit entries to structured logs
 *   <li><strong>File-based:</strong> Write entries to local files for later replay
 *   <li><strong>In-memory:</strong> Buffer entries in memory (with size limits)
 *   <li><strong>Message Queue:</strong> Send to a reliable message queue
 * </ul>
 *
 * <p>Example usage with circuit breaker:
 *
 * <pre>{@code
 * CircuitBreaker circuitBreaker = CircuitBreaker.of("auditWriter", config);
 * FallbackAuditWriter fallback = new LoggingFallbackWriter();
 *
 * AuditWriter resilientWriter = new CircuitBreakerAuditWriter(
 *     primaryWriter,
 *     circuitBreaker,
 *     fallback
 * );
 * }</pre>
 */
public interface FallbackAuditWriter {

    /**
     * Writes a single audit entry to the fallback storage.
     *
     * <p>This method should never throw exceptions - fallback is the last resort. If the fallback
     * fails, it should log the error but not propagate exceptions.
     *
     * @param entry the audit entry to write
     */
    void writeFallback(AuditEntry entry);

    /**
     * Writes multiple audit entries to the fallback storage.
     *
     * <p>Implementations should attempt to write all entries, even if some fail. This method should
     * never throw exceptions.
     *
     * @param entries the audit entries to write
     */
    default void writeFallbackBatch(List<AuditEntry> entries) {
        for (AuditEntry entry : entries) {
            writeFallback(entry);
        }
    }

    /**
     * Returns the type/name of this fallback mechanism.
     *
     * <p>Used for logging and monitoring purposes.
     *
     * @return fallback type (e.g., "logging", "file-based", "in-memory")
     */
    default String getFallbackType() {
        return getClass().getSimpleName();
    }
}
