package io.torana.spring.boot.autoconfigure.resolver;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import io.torana.api.model.TraceContext;
import io.torana.spi.TraceResolver;

import java.util.Optional;

/**
 * Trace resolver that extracts trace context from Micrometer Tracing's Tracer.
 *
 * <p>This resolver directly uses Micrometer Tracing's {@link Tracer} API to extract trace and span
 * IDs from the current span. This provides the most accurate trace context when using Micrometer
 * Tracing with backends like Brave or OpenTelemetry.
 */
public class MicrometerTracingResolver implements TraceResolver {

    private final Tracer tracer;

    public MicrometerTracingResolver(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Optional<TraceContext> resolve() {
        Span currentSpan = tracer.currentSpan();
        if (currentSpan == null) {
            return Optional.empty();
        }

        io.micrometer.tracing.TraceContext context = currentSpan.context();
        if (context == null) {
            return Optional.empty();
        }

        String traceId = context.traceId();
        String spanId = context.spanId();
        String parentSpanId = context.parentId();

        if (traceId == null || traceId.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(TraceContext.of(traceId, spanId, parentSpanId));
    }
}
