package io.torana.resilience;

import io.torana.api.model.AuditEntry;
import io.torana.spi.AuditWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Service that recovers fallback audit entries and replays them to the primary database.
 *
 * <p>This service runs periodically (default: every 5 minutes) to check for pending fallback
 * entries and attempt to write them to the primary database. It's designed to automatically recover
 * from database outages without manual intervention.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Automatic scheduled recovery (configurable interval)
 *   <li>Processes entries in chronological order
 *   <li>Batch processing for efficiency
 *   <li>Automatic retry on transient failures
 *   <li>Cleanup of successfully recovered entries
 * </ul>
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * torana:
 *   resilience:
 *     recovery:
 *       enabled: true
 *       initial-delay-seconds: 60
 *       fixed-delay-seconds: 300  # 5 minutes
 *       batch-size: 100
 * }</pre>
 *
 * <p>Recovery process:
 *
 * <pre>
 * 1. List fallback files (sorted chronologically)
 * 2. For each batch:
 *    a. Read entries from files
 *    b. Attempt to write to primary database
 *    c. Delete files for successfully written entries
 * 3. Log statistics (recovered, failed, pending)
 * </pre>
 */
public class AuditRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(AuditRecoveryService.class);

    private static final int DEFAULT_BATCH_SIZE = 100;

    private final FileBasedFallbackWriter fallbackWriter;
    private final AuditWriter primaryWriter;
    private final int batchSize;

    /**
     * Creates an audit recovery service with default batch size.
     *
     * @param fallbackWriter the file-based fallback writer
     * @param primaryWriter the primary audit writer (database)
     */
    public AuditRecoveryService(
            FileBasedFallbackWriter fallbackWriter, AuditWriter primaryWriter) {
        this(fallbackWriter, primaryWriter, DEFAULT_BATCH_SIZE);
    }

    /**
     * Creates an audit recovery service with custom batch size.
     *
     * @param fallbackWriter the file-based fallback writer
     * @param primaryWriter the primary audit writer (database)
     * @param batchSize the number of entries to process per batch
     */
    public AuditRecoveryService(
            FileBasedFallbackWriter fallbackWriter, AuditWriter primaryWriter, int batchSize) {
        this.fallbackWriter = fallbackWriter;
        this.primaryWriter = primaryWriter;
        this.batchSize = Math.max(1, batchSize);
    }

    /**
     * Scheduled recovery task.
     *
     * <p>Runs periodically to check for pending fallback entries and replay them.
     *
     * <p>Default schedule: Fixed delay of 5 minutes (300 seconds) with initial delay of 1 minute.
     * Configure via Spring scheduling properties.
     */
    @Scheduled(
            initialDelayString =
                    "${torana.resilience.recovery.initial-delay-seconds:60000}",
            fixedDelayString = "${torana.resilience.recovery.fixed-delay-seconds:300000}",
            timeUnit = TimeUnit.MILLISECONDS)
    public void recoverPendingEntries() {
        try {
            List<Path> fallbackFiles = fallbackWriter.listFallbackFiles();

            if (fallbackFiles.isEmpty()) {
                log.debug("No pending fallback entries to recover");
                return;
            }

            log.info("Starting audit recovery: {} entries pending", fallbackFiles.size());

            int recovered = 0;
            int failed = 0;

            // Process in batches
            for (int i = 0; i < fallbackFiles.size(); i += batchSize) {
                int end = Math.min(i + batchSize, fallbackFiles.size());
                List<Path> batch = fallbackFiles.subList(i, end);

                log.debug("Processing recovery batch: files {} to {}", i, end - 1);

                RecoveryResult result = processBatch(batch);
                recovered += result.recovered;
                failed += result.failed;
            }

            long stillPending = fallbackWriter.countFallbackFiles();

            log.info(
                    "Audit recovery complete: {} recovered, {} failed, {} still pending",
                    recovered,
                    failed,
                    stillPending);

        } catch (IOException e) {
            log.error("Failed to list fallback files during recovery: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during audit recovery: {}", e.getMessage(), e);
        }
    }

    /**
     * Processes a batch of fallback files.
     *
     * @param filePaths the files to process
     * @return recovery statistics
     */
    private RecoveryResult processBatch(List<Path> filePaths) {
        int recovered = 0;
        int failed = 0;

        List<AuditEntry> entries = new ArrayList<>();
        List<Path> successfulFiles = new ArrayList<>();

        // Read all entries in the batch
        for (Path filePath : filePaths) {
            try {
                FileBasedFallbackWriter.FallbackEntry fallbackEntry =
                        fallbackWriter.readFallbackFile(filePath);
                AuditEntry auditEntry = convertToAuditEntry(fallbackEntry);
                entries.add(auditEntry);
                successfulFiles.add(filePath);

            } catch (IOException e) {
                log.error(
                        "Failed to read fallback file {}: {}",
                        filePath.getFileName(),
                        e.getMessage());
                failed++;
            } catch (Exception e) {
                log.error(
                        "Unexpected error reading fallback file {}: {}",
                        filePath.getFileName(),
                        e.getMessage(),
                        e);
                failed++;
            }
        }

        // Attempt to write batch to primary database
        if (!entries.isEmpty()) {
            try {
                primaryWriter.writeBatch(entries);

                // Success - delete fallback files
                for (Path filePath : successfulFiles) {
                    if (fallbackWriter.deleteFallbackFile(filePath)) {
                        recovered++;
                    } else {
                        // Deletion failed, but entry was written - log warning
                        log.warn(
                                "Entry recovered but failed to delete fallback file: {}",
                                filePath.getFileName());
                        recovered++;
                    }
                }

                log.info("Successfully recovered batch of {} entries", recovered);

            } catch (Exception e) {
                log.error(
                        "Failed to write batch of {} entries to primary database: {}",
                        entries.size(),
                        e.getMessage(),
                        e);
                // Leave files in place for next retry
                failed += entries.size();
            }
        }

        return new RecoveryResult(recovered, failed);
    }

    /**
     * Converts a fallback entry to an audit entry.
     *
     * @param fallbackEntry the fallback entry from file
     * @return the reconstructed audit entry
     */
    private AuditEntry convertToAuditEntry(FileBasedFallbackWriter.FallbackEntry fallbackEntry) {
        return fallbackEntry.toAuditEntry();
    }

    /**
     * Manually triggers recovery.
     *
     * <p>Useful for testing or manual recovery after resolving database issues.
     */
    public void triggerManualRecovery() {
        log.info("Manual audit recovery triggered");
        recoverPendingEntries();
    }

    /**
     * Gets the number of pending fallback entries.
     *
     * @return count of pending entries
     */
    public long getPendingCount() {
        return fallbackWriter.countFallbackFiles();
    }

    /**
     * Result of a recovery batch operation.
     *
     * @param recovered number of successfully recovered entries
     * @param failed number of failed recovery attempts
     */
    private record RecoveryResult(int recovered, int failed) {}
}
