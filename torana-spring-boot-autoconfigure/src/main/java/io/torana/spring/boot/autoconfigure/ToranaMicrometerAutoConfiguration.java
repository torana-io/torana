package io.torana.spring.boot.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.torana.micrometer.MetricsAuditWriter;
import io.torana.spi.AuditWriter;
import io.torana.spi.TraceResolver;
import io.torana.spring.boot.autoconfigure.resolver.MicrometerTraceResolver;
import io.torana.spring.boot.autoconfigure.resolver.MicrometerTracingResolver;

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

/**
 * Auto-configuration for Micrometer integration.
 *
 * <p>Provides two main integrations:
 *
 * <ul>
 *   <li><strong>Trace Resolution:</strong> Extracts trace context from Micrometer Observation or
 *       Tracer
 *   <li><strong>Metrics Collection:</strong> Instruments audit writes with Micrometer metrics
 *       (latency, throughput, errors)
 * </ul>
 *
 * <p>Trace resolution automatically activates when Micrometer is present. Prefers Tracer-based
 * resolver when micrometer-tracing is available, falls back to ObservationRegistry-based resolver
 * otherwise.
 *
 * <p>Metrics collection activates when {@code MeterRegistry} bean is present and {@code
 * torana.metrics.enabled=true} (default).
 */
@AutoConfiguration
@EnableConfigurationProperties(ToranaProperties.class)
public class ToranaMicrometerAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.tracing.Tracer")
    static class TracerConfiguration {
        @Bean
        @ConditionalOnMissingBean(TraceResolver.class)
        @ConditionalOnBean(name = "tracer")
        public TraceResolver toranaMicrometerTracingResolver(io.micrometer.tracing.Tracer tracer) {
            return new MicrometerTracingResolver(tracer);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "io.micrometer.observation.ObservationRegistry")
    static class ObservationConfiguration {
        @Bean
        @ConditionalOnMissingBean(TraceResolver.class)
        @ConditionalOnBean(name = "observationRegistry")
        public TraceResolver toranaMicrometerTraceResolver(
                io.micrometer.observation.ObservationRegistry observationRegistry) {
            return new MicrometerTraceResolver(observationRegistry);
        }
    }

    /**
     * Configuration for Micrometer metrics collection.
     *
     * <p>Decorates the audit writer with {@link MetricsAuditWriter} to track:
     *
     * <ul>
     *   <li>Write latency (timer with percentiles)
     *   <li>Write throughput (success counter)
     *   <li>Error rates (error counter)
     * </ul>
     *
     * <p>Activates when:
     *
     * <ul>
     *   <li>{@code MeterRegistry} bean is present
     *   <li>{@code torana.metrics.enabled=true} (default)
     * </ul>
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnProperty(
            prefix = "torana.metrics",
            name = "enabled",
            havingValue = "true",
            matchIfMissing = true)
    static class MetricsConfiguration {

        /**
         * Creates a metrics-instrumented audit writer.
         *
         * <p>This bean decorates the delegate audit writer (typically {@code JdbcAuditWriter}) with
         * metrics collection. It becomes the primary {@code AuditWriter} bean when metrics are
         * enabled.
         *
         * <p>The delegate is identified by the {@code @Qualifier("delegateAuditWriter")} annotation
         * to avoid circular dependencies.
         *
         * <p>Being marked as {@code @Primary}, this bean will be injected into {@code
         * TransactionAwareWriter} instead of the delegate, ensuring all audit writes are
         * instrumented with metrics.
         *
         * @param delegate the underlying audit writer
         * @param meterRegistry the meter registry for recording metrics
         * @param properties Torana configuration properties
         * @return metrics-instrumented audit writer
         */
        @Bean
        @Primary
        @ConditionalOnBean(MeterRegistry.class)
        public MetricsAuditWriter toranaMetricsAuditWriter(
                @Qualifier("delegateAuditWriter") AuditWriter delegate,
                MeterRegistry meterRegistry,
                ToranaProperties properties) {
            return new MetricsAuditWriter(
                    delegate, meterRegistry, properties.getMetrics().isIncludeDetailedTags());
        }
    }
}
