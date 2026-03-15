package io.torana.spring.boot.autoconfigure.resolver;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.torana.api.model.TraceContext;
import io.torana.spi.TraceResolver;

import java.util.Optional;

/**
 * Trace resolver that extracts trace context from Micrometer Observation.
 *
 * <p>Uses Micrometer's ObservationRegistry to access the current observation and extract trace/span
 * information. This works with various tracing backends that integrate with Micrometer (e.g.,
 * Zipkin, Jaeger via Brave/OpenTelemetry).
 */
public class MicrometerTraceResolver implements TraceResolver {

    private final ObservationRegistry observationRegistry;

    public MicrometerTraceResolver(ObservationRegistry observationRegistry) {
        this.observationRegistry = observationRegistry;
    }

    @Override
    public Optional<TraceContext> resolve() {
        Observation currentObservation = observationRegistry.getCurrentObservation();
        if (currentObservation == null) {
            return Optional.empty();
        }

        Observation.Context context = currentObservation.getContext();
        if (context == null) {
            return Optional.empty();
        }

        String traceId = getContextValue(context, "traceId");
        String spanId = getContextValue(context, "spanId");
        String parentSpanId = getContextValue(context, "parentSpanId");

        if (traceId == null && spanId == null) {
            return Optional.empty();
        }

        return Optional.of(TraceContext.of(traceId, spanId, parentSpanId));
    }

    private String getContextValue(Observation.Context context, String key) {
        return context.getHighCardinalityKeyValues().stream()
                .filter(kv -> kv.getKey().equals(key))
                .map(kv -> kv.getValue())
                .findFirst()
                .orElse(null);
    }
}
