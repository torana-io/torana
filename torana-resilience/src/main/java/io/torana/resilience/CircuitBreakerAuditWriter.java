package io.torana.resilience;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.torana.api.AuditEntry;
import io.torana.spi.AuditWriter;
import io.torana.spi.FallbackAuditWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

/**
 * Decorator that adds circuit breaker pattern to audit writes with fallback support.
 *
 * <p>This implementation uses Resilience4j's CircuitBreaker to protect the audit system from
 * cascading failures. When the failure rate exceeds a threshold, the circuit opens and all writes
 * are redirected to a fallback writer (e.g., logging, file-based storage).
 *
 * <p>Circuit States:
 *
 * <ul>
 *   <li><strong>CLOSED:</strong> Normal operation, writes go to primary writer
 *   <li><strong>OPEN:</strong> Too many failures, writes go to fallback writer
 *   <li><strong>HALF_OPEN:</strong> Testing recovery, limited writes to primary
 * </ul>
 *
 * <p>Example configuration:
 *
 * <pre>{@code
 * CircuitBreakerConfig config = CircuitBreakerConfig.custom()
 *     .failureRateThreshold(50)  // Open circuit at 50% failure rate
 *     .minimumNumberOfCalls(10)  // Need at least 10 calls before evaluating
 *     .waitDurationInOpenState(Duration.ofSeconds(60))  // Wait 60s before testing recovery
 *     .build();
 *
 * CircuitBreaker circuitBreaker = CircuitBreaker.of("auditWriter", config);
 * FallbackAuditWriter fallback = new LoggingFallbackWriter();
 *
 * AuditWriter resilientWriter = new CircuitBreakerAuditWriter(
 *     primaryWriter,
 *     circuitBreaker,
 *     fallback
 * );
 * }</pre>
 *
 * <p>This writer ensures that audit failures never impact business operations - when the circuit
 * opens, audit entries are buffered to fallback storage for later recovery.
 */
public class CircuitBreakerAuditWriter implements AuditWriter {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerAuditWriter.class);

    private final AuditWriter delegate;
    private final CircuitBreaker circuitBreaker;
    private final FallbackAuditWriter fallback;

    /**
     * Creates a circuit breaker audit writer.
     *
     * @param delegate the underlying audit writer to protect
     * @param circuitBreaker the Resilience4j CircuitBreaker instance
     * @param fallback the fallback writer to use when circuit is open
     */
    public CircuitBreakerAuditWriter(
            AuditWriter delegate, CircuitBreaker circuitBreaker, FallbackAuditWriter fallback) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.fallback = fallback;

        // Register event listeners for monitoring
        circuitBreaker
                .getEventPublisher()
                .onStateTransition(
                        event ->
                                log.warn(
                                        "Circuit breaker state transition: {} -> {} (failure rate: {}%)",
                                        event.getStateTransition().getFromState(),
                                        event.getStateTransition().getToState(),
                                        circuitBreaker.getMetrics().getFailureRate()))
                .onSuccess(
                        event ->
                                log.debug(
                                        "Circuit breaker call succeeded (duration: {}ms)",
                                        event.getElapsedDuration().toMillis()))
                .onError(
                        event ->
                                log.warn(
                                        "Circuit breaker call failed: {} (duration: {}ms)",
                                        event.getThrowable().getMessage(),
                                        event.getElapsedDuration().toMillis()))
                .onCallNotPermitted(
                        event ->
                                log.debug(
                                        "Circuit breaker OPEN - call not permitted, using fallback"));
    }

    /**
     * Creates a circuit breaker audit writer with default configuration.
     *
     * <p>Default configuration:
     *
     * <ul>
     *   <li>Failure rate threshold: 50%
     *   <li>Minimum number of calls: 10
     *   <li>Wait duration in open state: 60 seconds
     *   <li>Permitted calls in half-open state: 5
     *   <li>Sliding window size: 100
     * </ul>
     *
     * @param delegate the underlying audit writer to protect
     * @param fallback the fallback writer to use when circuit is open
     * @return configured circuit breaker writer
     */
    public static CircuitBreakerAuditWriter withDefaults(
            AuditWriter delegate, FallbackAuditWriter fallback) {
        CircuitBreakerConfig config =
                CircuitBreakerConfig.custom()
                        .failureRateThreshold(50)
                        .minimumNumberOfCalls(10)
                        .waitDurationInOpenState(Duration.ofSeconds(60))
                        .permittedNumberOfCallsInHalfOpenState(5)
                        .slidingWindowSize(100)
                        .build();

        CircuitBreaker circuitBreaker = CircuitBreaker.of("auditWriter", config);
        return new CircuitBreakerAuditWriter(delegate, circuitBreaker, fallback);
    }

    @Override
    public void write(AuditEntry entry) {
        executeWithCircuitBreaker(
                () -> {
                    delegate.write(entry);
                    return null;
                },
                () -> {
                    fallback.writeFallback(entry);
                    return null;
                });
    }

    @Override
    public void writeBatch(List<AuditEntry> entries) {
        executeWithCircuitBreaker(
                () -> {
                    delegate.writeBatch(entries);
                    return null;
                },
                () -> {
                    fallback.writeFallbackBatch(entries);
                    return null;
                });
    }

    /**
     * Executes an operation with circuit breaker protection.
     *
     * <p>If the circuit is OPEN, the fallback operation is executed instead. If the circuit is
     * CLOSED or HALF_OPEN, the primary operation is attempted.
     *
     * @param primaryOperation the primary operation to execute
     * @param fallbackOperation the fallback operation if circuit is open
     * @param <T> return type
     * @return operation result
     */
    private <T> T executeWithCircuitBreaker(
            Supplier<T> primaryOperation, Supplier<T> fallbackOperation) {

        // Decorate the primary operation with circuit breaker
        Supplier<T> decoratedSupplier =
                CircuitBreaker.decorateSupplier(circuitBreaker, primaryOperation);

        try {
            return decoratedSupplier.get();
        } catch (io.github.resilience4j.circuitbreaker.CallNotPermittedException e) {
            // Circuit is OPEN - use fallback
            log.debug(
                    "Circuit breaker is OPEN, using fallback writer: {}",
                    fallback.getFallbackType());
            return fallbackOperation.get();
        }
    }

    /**
     * Gets the current circuit breaker state.
     *
     * @return current state (CLOSED, OPEN, HALF_OPEN, DISABLED, FORCED_OPEN)
     */
    public CircuitBreaker.State getState() {
        return circuitBreaker.getState();
    }

    /**
     * Gets the current failure rate as a percentage.
     *
     * @return failure rate (0.0 to 100.0)
     */
    public float getFailureRate() {
        return circuitBreaker.getMetrics().getFailureRate();
    }

    /**
     * Manually transitions the circuit breaker to CLOSED state.
     *
     * <p>Use this to reset the circuit breaker after manual intervention.
     */
    public void reset() {
        log.info("Manually resetting circuit breaker to CLOSED state");
        circuitBreaker.reset();
    }

    /**
     * Manually transitions the circuit breaker to OPEN state.
     *
     * <p>Use this to force fallback mode during maintenance.
     */
    public void transitionToOpenState() {
        log.warn("Manually opening circuit breaker");
        circuitBreaker.transitionToOpenState();
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
     * Gets the Resilience4j CircuitBreaker instance.
     *
     * @return the circuit breaker instance
     */
    public CircuitBreaker getCircuitBreaker() {
        return circuitBreaker;
    }

    /**
     * Gets the fallback writer.
     *
     * @return the fallback audit writer
     */
    public FallbackAuditWriter getFallback() {
        return fallback;
    }
}
