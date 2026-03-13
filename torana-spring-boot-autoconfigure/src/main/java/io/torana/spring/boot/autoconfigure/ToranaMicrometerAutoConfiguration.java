package io.torana.spring.boot.autoconfigure;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Tracer;
import io.torana.micrometer.MicrometerTraceResolver;
import io.torana.micrometer.MicrometerTracingResolver;
import io.torana.spi.TraceResolver;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration for Micrometer tracing integration.
 *
 * <p>Provides trace context resolution from Micrometer when present. Prefers the Tracer-based
 * resolver when micrometer-tracing is available, falls back to ObservationRegistry-based resolver
 * otherwise.
 */
@AutoConfiguration
public class ToranaMicrometerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TraceResolver.class)
    @ConditionalOnClass(Tracer.class)
    @ConditionalOnBean(Tracer.class)
    public TraceResolver toranaMicrometerTracingResolver(Tracer tracer) {
        return new MicrometerTracingResolver(tracer);
    }

    @Bean
    @ConditionalOnMissingBean(TraceResolver.class)
    @ConditionalOnClass(ObservationRegistry.class)
    @ConditionalOnBean(ObservationRegistry.class)
    public TraceResolver toranaMicrometerTraceResolver(ObservationRegistry observationRegistry) {
        return new MicrometerTraceResolver(observationRegistry);
    }
}
