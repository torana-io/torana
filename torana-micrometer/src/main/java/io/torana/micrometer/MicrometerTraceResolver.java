package io.torana.micrometer;

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

        // The Observation doesn't directly expose trace IDs - we need to access
        // the underlying tracer context. For now, we extract what we can from
        // the observation's context.
        Observation.Context context = currentObservation.getContext();
        if (context == null) {
            return Optional.empty();
        }

        // Try to get traceId and spanId from context attributes
        // These are typically set by the tracing bridge (Brave, OpenTelemetry)
        String traceId = getContextValue(context, "traceId");
        String spanId = getContextValue(context, "spanId");
        String parentSpanId = getContextValue(context, "parentSpanId");

        if (traceId == null && spanId == null) {
            // Fallback: use observation name as a pseudo-trace identifier
            return Optional.empty();
        }

        return Optional.of(TraceContext.of(traceId, spanId, parentSpanId));
    }

    private String getContextValue(Observation.Context context, String key) {
        // Try to get from high cardinality key values
        return context.getHighCardinalityKeyValues().stream()
                .filter(kv -> kv.getKey().equals(key))
                .map(kv -> kv.getValue())
                .findFirst()
                .orElse(null);
    }
}
