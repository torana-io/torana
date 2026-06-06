package io.torana.resilience;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.torana.api.AuditEntry;
import io.torana.spi.FallbackAuditWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Fallback writer that writes audit entries to local JSON files.
 *
 * <p>This implementation provides durable fallback storage for audit entries when the primary
 * database is unavailable. Each entry is written as a separate JSON file, which can be replayed by
 * the {@link AuditRecoveryService} when the database recovers.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Durable storage survives application restarts
 *   <li>One file per entry for reliable atomic writes
 *   <li>Timestamped filenames for easy sorting and debugging
 *   <li>Automatic directory creation
 *   <li>Never throws exceptions (safe fallback of last resort)
 * </ul>
 *
 * <p>File naming convention:
 *
 * <pre>
 * audit-{timestamp}-{uuid}.json
 *
 * Example:
 * audit-20260606T103045.123Z-550e8400-e29b-41d4-a716-446655440000.json
 * </pre>
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * torana:
 *   resilience:
 *     fallback:
 *       enabled: true
 *       type: file_based
 *       file-based-directory: /var/lib/torana/fallback
 * }</pre>
 *
 * <p><strong>Important:</strong> The fallback directory must be:
 *
 * <ul>
 *   <li>Writable by the application user
 *   <li>On a filesystem with sufficient space (monitor disk usage)
 *   <li>Not on a temporary filesystem (survives reboots)
 *   <li>Backed up regularly (contains audit data)
 * </ul>
 */
public class FileBasedFallbackWriter implements FallbackAuditWriter {

    private static final Logger log = LoggerFactory.getLogger(FileBasedFallbackWriter.class);

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss.SSS'Z'").withZone(ZoneOffset.UTC);

    private final Path fallbackDirectory;
    private final ObjectMapper objectMapper;

    /**
     * Creates a file-based fallback writer.
     *
     * @param fallbackDirectory the directory path for storing fallback files
     */
    public FileBasedFallbackWriter(String fallbackDirectory) {
        this(Paths.get(fallbackDirectory));
    }

    /**
     * Creates a file-based fallback writer.
     *
     * @param fallbackDirectory the directory path for storing fallback files
     */
    public FileBasedFallbackWriter(Path fallbackDirectory) {
        this.fallbackDirectory = fallbackDirectory;
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .enable(SerializationFeature.INDENT_OUTPUT);

        // Ensure directory exists
        ensureDirectoryExists();
    }

    /**
     * Creates a file-based fallback writer with custom JSON serialization.
     *
     * @param fallbackDirectory the directory path
     * @param objectMapper custom ObjectMapper
     */
    public FileBasedFallbackWriter(Path fallbackDirectory, ObjectMapper objectMapper) {
        this.fallbackDirectory = fallbackDirectory;
        this.objectMapper = objectMapper;
        ensureDirectoryExists();
    }

    @Override
    public void writeFallback(AuditEntry entry) {
        try {
            // Generate filename: audit-{timestamp}-{uuid}.json
            String timestamp = TIMESTAMP_FORMAT.format(entry.getOccurredAt());
            String filename = String.format("audit-%s-%s.json", timestamp, entry.getId());
            Path filePath = fallbackDirectory.resolve(filename);

            // Serialize entry to JSON
            String json = objectMapper.writeValueAsString(new FallbackEntry(entry));

            // Write to file (atomic write)
            Files.writeString(
                    filePath, json, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

            log.info(
                    "Wrote fallback audit entry to file: {} (action={}, id={})",
                    filename,
                    entry.getAction(),
                    entry.getId());

        } catch (IOException e) {
            // Never throw from fallback - log error and continue
            log.error(
                    "Failed to write fallback file for audit entry (id={}, action={}): {}",
                    entry.getId(),
                    entry.getAction(),
                    e.getMessage(),
                    e);
        } catch (Exception e) {
            // Catch all exceptions to ensure fallback never fails the business operation
            log.error(
                    "Unexpected error writing fallback for audit entry (id={}, action={}): {}",
                    entry.getId(),
                    entry.getAction(),
                    e.getMessage(),
                    e);
        }
    }

    @Override
    public void writeFallbackBatch(List<AuditEntry> entries) {
        log.info("Writing batch of {} fallback entries", entries.size());
        for (AuditEntry entry : entries) {
            writeFallback(entry);
        }
    }

    @Override
    public String getFallbackType() {
        return "file_based";
    }

    /**
     * Gets the fallback directory path.
     *
     * @return the directory path
     */
    public Path getFallbackDirectory() {
        return fallbackDirectory;
    }

    /**
     * Lists all pending fallback files.
     *
     * <p>Returns files sorted by filename (which includes timestamp), oldest first.
     *
     * @return list of fallback file paths
     * @throws IOException if listing fails
     */
    public List<Path> listFallbackFiles() throws IOException {
        ensureDirectoryExists();

        try (var stream = Files.list(fallbackDirectory)) {
            return stream.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith("audit-"))
                    .filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * Counts pending fallback files.
     *
     * @return number of pending files
     */
    public long countFallbackFiles() {
        try {
            return listFallbackFiles().size();
        } catch (IOException e) {
            log.error("Failed to count fallback files: {}", e.getMessage());
            return 0;
        }
    }

    /**
     * Reads a fallback entry from a file.
     *
     * @param filePath the file path
     * @return the fallback entry
     * @throws IOException if reading fails
     */
    public FallbackEntry readFallbackFile(Path filePath) throws IOException {
        String json = Files.readString(filePath);
        return objectMapper.readValue(json, FallbackEntry.class);
    }

    /**
     * Deletes a fallback file.
     *
     * <p>Called by the recovery service after successfully replaying the entry.
     *
     * @param filePath the file path to delete
     * @return true if deleted successfully
     */
    public boolean deleteFallbackFile(Path filePath) {
        try {
            Files.delete(filePath);
            log.debug("Deleted fallback file: {}", filePath.getFileName());
            return true;
        } catch (IOException e) {
            log.error(
                    "Failed to delete fallback file {}: {}",
                    filePath.getFileName(),
                    e.getMessage());
            return false;
        }
    }

    /**
     * Ensures the fallback directory exists, creating it if necessary.
     */
    private void ensureDirectoryExists() {
        try {
            if (!Files.exists(fallbackDirectory)) {
                Files.createDirectories(fallbackDirectory);
                log.info("Created fallback directory: {}", fallbackDirectory);
            }
        } catch (IOException e) {
            log.error(
                    "Failed to create fallback directory {}: {}",
                    fallbackDirectory,
                    e.getMessage(),
                    e);
        }
    }

    /**
     * Serializable representation of an audit entry for fallback storage.
     *
     * <p>This record is designed for JSON serialization and includes all fields needed to
     * reconstruct the audit entry during recovery.
     */
    public record FallbackEntry(
            String id,
            String action,
            Instant occurredAt,
            String outcome,
            String actorId,
            String actorType,
            String actorName,
            String tenantId,
            String tenantName,
            String targetType,
            String targetId,
            String targetDisplayName,
            String requestId,
            String requestMethod,
            String requestPath,
            String clientIp,
            String userAgent,
            String traceId,
            String spanId,
            String parentSpanId,
            String metadata,
            String beforeSnapshot,
            String afterSnapshot,
            String errorMessage,
            Integer schemaVersion,
            Instant writtenAt) {

        /**
         * Creates a fallback entry from an audit entry.
         *
         * @param entry the audit entry
         */
        public FallbackEntry(AuditEntry entry) {
            this(
                    entry.getId().toString(),
                    entry.getAction(),
                    entry.getOccurredAt(),
                    entry.getOutcome(),
                    entry.getActorId(),
                    entry.getActorType(),
                    entry.getActorName(),
                    entry.getTenantId(),
                    entry.getTenantName(),
                    entry.getTargetType(),
                    entry.getTargetId(),
                    entry.getTargetDisplayName(),
                    entry.getRequestId(),
                    entry.getRequestMethod(),
                    entry.getRequestPath(),
                    entry.getClientIp(),
                    entry.getUserAgent(),
                    entry.getTraceId(),
                    entry.getSpanId(),
                    entry.getParentSpanId(),
                    entry.getMetadata(),
                    entry.getBeforeSnapshot(),
                    entry.getAfterSnapshot(),
                    entry.getErrorMessage(),
                    entry.getSchemaVersion(),
                    Instant.now());
        }
    }
}
