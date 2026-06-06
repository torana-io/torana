package io.torana.spring.boot.autoconfigure;

import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.torana.resilience.AuditRecoveryService;
import io.torana.resilience.CircuitBreakerAuditWriter;
import io.torana.resilience.FileBasedFallbackWriter;
import io.torana.resilience.LoggingFallbackWriter;
import io.torana.resilience.RetryableAuditWriter;
import io.torana.spi.AuditWriter;
import io.torana.spi.FallbackAuditWriter;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Duration;

/**
 * Auto-configuration for Torana resilience patterns.
 *
 * <p>This configuration provides circuit breaker, retry, and fallback mechanisms for audit writes.
 * It's automatically activated when:
 *
 * <ul>
 *   <li>The {@code torana-resilience} module is on the classpath
 *   <li>Resilience4j is available ({@code CircuitBreaker} and {@code Retry} classes present)
 *   <li>Individual patterns are enabled via configuration properties
 * </ul>
 *
 * <p>Configuration example:
 *
 * <pre>{@code
 * torana:
 *   resilience:
 *     circuit-breaker:
 *       enabled: true
 *       failure-rate-threshold: 50
 *       wait-duration-in-open-state-seconds: 60
 *     retry:
 *       enabled: true
 *       max-attempts: 3
 *       wait-duration-millis: 1000
 *     fallback:
 *       enabled: true
 *       type: logging
 * }</pre>
 *
 * <p>Component wiring order (when all enabled):
 *
 * <pre>
 * RetryableAuditWriter
 *   → CircuitBreakerAuditWriter
 *     → Primary AuditWriter (JDBC/etc)
 *     → FallbackAuditWriter (on circuit open)
 * </pre>
 */
@AutoConfiguration(after = ToranaAutoConfiguration.class)
@ConditionalOnClass({CircuitBreaker.class, Retry.class})
@EnableConfigurationProperties(ToranaProperties.class)
public class ToranaResilienceAutoConfiguration {

    /**
     * Configuration for fallback writers.
     *
     * <p>Activated when {@code torana.resilience.fallback.enabled=true}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnProperty(
            prefix = "torana.resilience.fallback",
            name = "enabled",
            havingValue = "true")
    static class FallbackConfiguration {

        /**
         * Creates a logging fallback writer.
         *
         * <p>Activated when {@code torana.resilience.fallback.type=logging}.
         *
         * @return logging fallback writer
         */
        @Bean
        @ConditionalOnMissingBean(FallbackAuditWriter.class)
        @ConditionalOnProperty(
                prefix = "torana.resilience.fallback",
                name = "type",
                havingValue = "logging",
                matchIfMissing = true)
        public FallbackAuditWriter toranaLoggingFallbackWriter() {
            return new LoggingFallbackWriter();
        }

        /**
         * Creates a file-based fallback writer.
         *
         * <p>Activated when {@code torana.resilience.fallback.type=file_based}.
         *
         * @param properties Torana configuration properties
         * @return file-based fallback writer
         */
        @Bean
        @ConditionalOnMissingBean(FallbackAuditWriter.class)
        @ConditionalOnProperty(
                prefix = "torana.resilience.fallback",
                name = "type",
                havingValue = "file_based")
        public FileBasedFallbackWriter toranaFileBasedFallbackWriter(ToranaProperties properties) {
            String directory = properties.getResilience().getFallback().getFileBasedDirectory();
            return new FileBasedFallbackWriter(directory);
        }
    }

    /**
     * Configuration for audit recovery service.
     *
     * <p>Activated when file-based fallback is enabled and recovery is not explicitly disabled.
     */
    @Configuration(proxyBeanMethods = false)
    @EnableScheduling
    @ConditionalOnProperty(
            prefix = "torana.resilience.fallback",
            name = "type",
            havingValue = "file_based")
    static class RecoveryConfiguration {

        /**
         * Creates the audit recovery service.
         *
         * <p>This service automatically recovers fallback entries when the database is available.
         *
         * @param fallbackWriter the file-based fallback writer
         * @param primaryWriter the primary audit writer
         * @return audit recovery service
         */
        @Bean
        @ConditionalOnBean({FileBasedFallbackWriter.class, AuditWriter.class})
        public AuditRecoveryService toranaAuditRecoveryService(
                FileBasedFallbackWriter fallbackWriter,
                @Qualifier("delegateAuditWriter") AuditWriter primaryWriter) {
            return new AuditRecoveryService(fallbackWriter, primaryWriter);
        }
    }

    /**
     * Configuration for circuit breaker.
     *
     * <p>Activated when {@code torana.resilience.circuit-breaker.enabled=true}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(CircuitBreaker.class)
    @ConditionalOnProperty(
            prefix = "torana.resilience.circuit-breaker",
            name = "enabled",
            havingValue = "true")
    static class CircuitBreakerConfiguration {

        /**
         * Creates the Resilience4j CircuitBreaker instance.
         *
         * @param properties Torana configuration properties
         * @return configured circuit breaker
         */
        @Bean
        @ConditionalOnMissingBean(name = "toranaCircuitBreaker")
        public CircuitBreaker toranaCircuitBreaker(ToranaProperties properties) {
            ToranaProperties.ResilienceProperties.CircuitBreakerProperties cbProps =
                    properties.getResilience().getCircuitBreaker();

            CircuitBreakerConfig config =
                    CircuitBreakerConfig.custom()
                            .failureRateThreshold(cbProps.getFailureRateThreshold())
                            .minimumNumberOfCalls(cbProps.getMinimumNumberOfCalls())
                            .waitDurationInOpenState(
                                    Duration.ofSeconds(cbProps.getWaitDurationInOpenStateSeconds()))
                            .permittedNumberOfCallsInHalfOpenState(
                                    cbProps.getPermittedNumberOfCallsInHalfOpenState())
                            .slidingWindowSize(cbProps.getSlidingWindowSize())
                            .build();

            return CircuitBreaker.of("toranaAuditWriter", config);
        }

        /**
         * Creates the circuit breaker audit writer.
         *
         * <p>This becomes the primary {@code AuditWriter} bean when circuit breaker is enabled.
         *
         * @param delegate the underlying audit writer (from ToranaAutoConfiguration)
         * @param circuitBreaker the circuit breaker instance
         * @param fallback the fallback writer
         * @return circuit breaker audit writer
         */
        @Bean
        @Primary
        @ConditionalOnBean({AuditWriter.class, CircuitBreaker.class, FallbackAuditWriter.class})
        public CircuitBreakerAuditWriter toranaCircuitBreakerAuditWriter(
                @Qualifier("circuitBreakerDelegateAuditWriter") AuditWriter delegate,
                @Qualifier("toranaCircuitBreaker") CircuitBreaker circuitBreaker,
                FallbackAuditWriter fallback) {
            return new CircuitBreakerAuditWriter(delegate, circuitBreaker, fallback);
        }
    }

    /**
     * Configuration for retry logic.
     *
     * <p>Activated when {@code torana.resilience.retry.enabled=true}.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(Retry.class)
    @ConditionalOnProperty(
            prefix = "torana.resilience.retry",
            name = "enabled",
            havingValue = "true")
    static class RetryConfiguration {

        /**
         * Creates the Resilience4j Retry instance.
         *
         * @param properties Torana configuration properties
         * @return configured retry
         */
        @Bean
        @ConditionalOnMissingBean(name = "toranaRetry")
        public Retry toranaRetry(ToranaProperties properties) {
            ToranaProperties.ResilienceProperties.RetryProperties retryProps =
                    properties.getResilience().getRetry();

            RetryConfig config =
                    RetryConfig.custom()
                            .maxAttempts(retryProps.getMaxAttempts())
                            .waitDuration(Duration.ofMillis(retryProps.getWaitDurationMillis()))
                            .intervalFunction(
                                    IntervalFunction.ofExponentialBackoff(
                                            Duration.ofMillis(retryProps.getWaitDurationMillis()),
                                            retryProps.getExponentialBackoffMultiplier()))
                            .build();

            return Retry.of("toranaAuditWriter", config);
        }

        /**
         * Creates the retryable audit writer.
         *
         * <p>This decorates the circuit breaker writer (if enabled) or the base writer.
         *
         * @param delegate the underlying audit writer
         * @param retry the retry instance
         * @return retryable audit writer
         */
        @Bean
        @Primary
        @ConditionalOnBean({AuditWriter.class, Retry.class})
        public RetryableAuditWriter toranaRetryableAuditWriter(
                @Qualifier("retryDelegateAuditWriter") AuditWriter delegate,
                @Qualifier("toranaRetry") Retry retry) {
            return new RetryableAuditWriter(delegate, retry);
        }
    }

    /**
     * Provides qualified delegate beans for proper decoration order.
     *
     * <p>Wiring order (bottom-up):
     *
     * <pre>
     * 1. delegateAuditWriter (from ToranaAutoConfiguration) - Base JDBC writer
     * 2. circuitBreakerDelegateAuditWriter - Wraps with circuit breaker (if enabled)
     * 3. retryDelegateAuditWriter - Wraps with retry (if enabled)
     * </pre>
     */
    @Configuration(proxyBeanMethods = false)
    static class DelegateConfiguration {

        /**
         * Qualifier for circuit breaker's delegate.
         *
         * <p>Points to the metrics writer (if metrics enabled) or the base JDBC writer.
         */
        @Bean
        @Qualifier("circuitBreakerDelegateAuditWriter")
        @ConditionalOnBean(name = "delegateAuditWriter")
        @ConditionalOnProperty(
                prefix = "torana.resilience.circuit-breaker",
                name = "enabled",
                havingValue = "true")
        public AuditWriter circuitBreakerDelegateAuditWriter(
                @Qualifier("delegateAuditWriter") AuditWriter delegate) {
            return delegate;
        }

        /**
         * Qualifier for retry's delegate.
         *
         * <p>Points to the circuit breaker writer (if enabled) or the base writer.
         */
        @Bean
        @Qualifier("retryDelegateAuditWriter")
        @ConditionalOnBean(AuditWriter.class)
        @ConditionalOnProperty(
                prefix = "torana.resilience.retry",
                name = "enabled",
                havingValue = "true")
        public AuditWriter retryDelegateAuditWriter(AuditWriter writer) {
            // If circuit breaker is enabled, this will be the circuit breaker writer
            // Otherwise, it's the metrics writer or base writer
            return writer;
        }
    }
}
