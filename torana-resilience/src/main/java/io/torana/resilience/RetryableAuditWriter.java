package io.torana.resilience;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.torana.api.AuditEntry;
import io.torana.spi.AuditWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Decorator that adds retry logic with exponential backoff to audit writes.
 *
 * <p>This implementation uses Resilience4j's Retry pattern to automatically retry failed write
 * operations. It's useful for handling transient failures like temporary network issues or database
 * connection timeouts.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>Configurable maximum retry attempts
 *   <li>Exponential backoff between retries
 *   <li>Selective retry based on exception types
 *   <li>Metrics and event publishing via Resilience4j
 * </ul>
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * RetryConfig config = RetryConfig.custom()
 *     .maxAttempts(3)
 *     .waitDuration(Duration.ofMillis(1000))
 *     .exponentialBackoffMultiplier(2.0)
 *     .retryExceptions(SQLException.class, DataAccessException.class)
 *     .build();
 *
 * Retry retry = Retry.of("auditWriter", config);
 * AuditWriter retryableWriter = new RetryableAuditWriter(delegate, retry);
 * }</pre>
 *
 * <p>Retry sequence example (exponentialBackoffMultiplier=2.0, waitDuration=1000ms):
 *
 * <pre>
 * Attempt 1: Immediate
 * Attempt 2: After 1000ms
 * Attempt 3: After 2000ms
 * </pre>
 */
public class RetryableAuditWriter implements AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(RetryableAuditWriter.class);

    private final AuditWriter delegate;
    private final Retry retry;

    /**
     * Creates a retryable audit writer.
     *
     * @param delegate the underlying audit writer to decorate
     * @param retry the Resilience4j Retry instance
     */
    public RetryableAuditWriter(AuditWriter delegate, Retry retry) {
        this.delegate = delegate;
        this.retry = retry;

        // Register event listeners for logging
        retry.getEventPublisher()
                .onRetry(
                        event ->
                                log.warn(
                                        "Retry attempt {} for audit write after {} ms (last exception: {})",
                                        event.getNumberOfRetryAttempts(),
                                        event.getWaitInterval().toMillis(),
                                        event.getLastThrowable().getMessage()))
                .onSuccess(
                        event -> {
                            if (event.getNumberOfRetryAttempts() > 0) {
                                log.info(
                                        "Audit write succeeded after {} retries",
                                        event.getNumberOfRetryAttempts());
                            }
                        })
                .onError(
                        event ->
                                log.error(
                                        "Audit write failed after {} retries: {}",
                                        event.getNumberOfRetryAttempts(),
                                        event.getLastThrowable().getMessage(),
                                        event.getLastThrowable()));
    }

    /**
     * Creates a retryable audit writer with default configuration.
     *
     * <p>Default configuration:
     *
     * <ul>
     *   <li>Max attempts: 3
     *   <li>Wait duration: 1 second
     *   <li>Exponential backoff: 2.0
     * </ul>
     *
     * @param delegate the underlying audit writer to decorate
     * @return configured retryable writer
     */
    public static RetryableAuditWriter withDefaults(AuditWriter delegate) {
        RetryConfig config =
                RetryConfig.custom()
                        .maxAttempts(3)
                        .waitDuration(Duration.ofMillis(1000))
                        .exponentialBackoffMultiplier(2.0)
                        .build();

        Retry retry = Retry.of("auditWriter", config);
        return new RetryableAuditWriter(delegate, retry);
    }

    @Override
    public void write(AuditEntry entry) {
        executeWithRetry(() -> {
            delegate.write(entry);
            return null;
        });
    }

    @Override
    public void writeBatch(List<AuditEntry> entries) {
        executeWithRetry(() -> {
            delegate.writeBatch(entries);
            return null;
        });
    }

    /**
     * Executes an operation with retry logic.
     *
     * @param operation the operation to execute
     * @param <T> return type
     * @return operation result
     */
    private <T> T executeWithRetry(Supplier<T> operation) {
        Supplier<T> decoratedSupplier = Retry.decorateSupplier(retry, operation);
        return decoratedSupplier.get();
    }

    /**
     * Gets the underlying delegate writer.
     *
     * @return the delegate audit writer
     */
    public AuditWriter getDelegate() {
        return delegate;
    }

    /**
     * Gets the Resilience4j Retry instance.
     *
     * @return the retry instance
     */
    public Retry getRetry() {
        return retry;
    }
}
