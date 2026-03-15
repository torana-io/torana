package io.torana.spring.boot.autoconfigure;

import io.torana.spi.TraceResolver;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Auto-configuration for Micrometer tracing integration.
 *
 * <p>Provides trace context resolution from Micrometer when present. Prefers the Tracer-based
 * resolver when micrometer-tracing is available, falls back to ObservationRegistry-based resolver
 * otherwise.
 */
@AutoConfiguration
public class ToranaMicrometerAutoConfiguration {

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"io.micrometer.tracing.Tracer", "io.torana.micrometer.MicrometerTracingResolver"})
    static class TracerConfiguration {
        @Bean
        @ConditionalOnMissingBean(TraceResolver.class)
        @ConditionalOnBean(name = "tracer")
        public TraceResolver toranaMicrometerTracingResolver(io.micrometer.tracing.Tracer tracer) {
            return new io.torana.micrometer.MicrometerTracingResolver(tracer);
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {"io.micrometer.observation.ObservationRegistry", "io.torana.micrometer.MicrometerTraceResolver"})
    static class ObservationConfiguration {
        @Bean
        @ConditionalOnMissingBean(TraceResolver.class)
        @ConditionalOnBean(name = "observationRegistry")
        public TraceResolver toranaMicrometerTraceResolver(io.micrometer.observation.ObservationRegistry observationRegistry) {
            return new io.torana.micrometer.MicrometerTraceResolver(observationRegistry);
        }
    }
}
