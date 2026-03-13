package io.torana.test;

import io.torana.api.model.AuditEntry;
import io.torana.spi.AuditWriter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/**
 * In-memory audit writer for testing.
 *
 * <p>Stores audit entries in memory for easy verification in tests. Thread-safe for use in
 * concurrent tests.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * InMemoryAuditWriter writer = new InMemoryAuditWriter();
 * // ... run code that creates audit entries
 *
 * assertThat(writer).hasRecorded(1);
 * assertThat(writer).hasRecordedAction("order.cancelled");
 *
 * AuditEntry entry = writer.findByAction("order.cancelled").orElseThrow();
 * assertThat(entry.target().id()).isEqualTo("order-123");
 * }</pre>
 */
public class InMemoryAuditWriter implements AuditWriter {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();

    @Override
    public void write(AuditEntry entry) {
        entries.add(entry);
    }

    @Override
    public void writeBatch(List<AuditEntry> entries) {
        this.entries.addAll(entries);
    }

    /**
     * Returns all recorded entries.
     *
     * @return an immutable list of entries
     */
    public List<AuditEntry> getEntries() {
        return List.copyOf(entries);
    }

    /**
     * Returns the last recorded entry.
     *
     * @return the last entry, or empty if none recorded
     */
    public Optional<AuditEntry> getLastEntry() {
        if (entries.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(entries.get(entries.size() - 1));
    }

    /**
     * Finds an entry by action name.
     *
     * @param action the action name
     * @return the first matching entry, or empty if not found
     */
    public Optional<AuditEntry> findByAction(String action) {
        return entries.stream().filter(e -> e.action().name().equals(action)).findFirst();
    }

    /**
     * Finds all entries by action name.
     *
     * @param action the action name
     * @return all matching entries
     */
    public List<AuditEntry> findAllByAction(String action) {
        return entries.stream().filter(e -> e.action().name().equals(action)).toList();
    }

    /**
     * Finds an entry by ID.
     *
     * @param id the entry ID
     * @return the entry, or empty if not found
     */
    public Optional<AuditEntry> findById(UUID id) {
        return entries.stream().filter(e -> e.id().equals(id)).findFirst();
    }

    /**
     * Finds entries matching a predicate.
     *
     * @param predicate the filter predicate
     * @return matching entries
     */
    public List<AuditEntry> findMatching(Predicate<AuditEntry> predicate) {
        return entries.stream().filter(predicate).toList();
    }

    /**
     * Returns the number of recorded entries.
     *
     * @return the entry count
     */
    public int size() {
        return entries.size();
    }

    /**
     * Checks if any entries have been recorded.
     *
     * @return true if no entries recorded
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Clears all recorded entries. */
    public void clear() {
        entries.clear();
    }
}
