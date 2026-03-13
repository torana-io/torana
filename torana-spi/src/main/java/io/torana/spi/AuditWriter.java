package io.torana.spi;

import io.torana.api.model.AuditEntry;

import java.util.List;

/**
 * SPI for persisting audit entries.
 *
 * <p>Implementations write audit entries to their backing store (database, file system, message
 * queue, etc.).
 *
 * <p>Audit entries should be stored in an append-only manner - existing entries should never be
 * modified or deleted during normal operation.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class JdbcAuditWriter implements AuditWriter {
 *     private final JdbcTemplate jdbcTemplate;
 *
 *     @Override
 *     public void write(AuditEntry entry) {
 *         jdbcTemplate.update(INSERT_SQL,
 *             entry.id(), entry.action().name(),
 *             // ... other fields
 *         );
 *     }
 *
 *     @Override
 *     public void writeBatch(List<AuditEntry> entries) {
 *         jdbcTemplate.batchUpdate(INSERT_SQL, entries, entries.size(),
 *             (ps, entry) -> {
 *                 // set parameters
 *             });
 *     }
 * }
 * }</pre>
 */
public interface AuditWriter {

    /**
     * Writes a single audit entry.
     *
     * @param entry the entry to persist
     */
    void write(AuditEntry entry);

    /**
     * Writes multiple audit entries in batch.
     *
     * <p>The default implementation writes entries one by one, but implementations should override
     * this for better performance.
     *
     * @param entries the entries to persist
     */
    default void writeBatch(List<AuditEntry> entries) {
        entries.forEach(this::write);
    }
}
