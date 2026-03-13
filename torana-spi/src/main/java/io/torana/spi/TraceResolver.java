package io.torana.spi;

import io.torana.api.model.TraceContext;

import java.util.Optional;

/**
 * SPI for resolving distributed tracing context.
 *
 * <p>Implementations typically extract trace and span IDs from tracing libraries (Micrometer,
 * OpenTelemetry, etc.).
 *
 * <p>Multiple resolvers can be registered. The first one that returns a non-empty result wins.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class MicrometerTraceResolver implements TraceResolver {
 *     private final Tracer tracer;
 *
 *     @Override
 *     public Optional<TraceContext> resolve() {
 *         var span = tracer.currentSpan();
 *         if (span == null) return Optional.empty();
 *
 *         var context = span.context();
 *         return Optional.of(TraceContext.of(
 *             context.traceId(),
 *             context.spanId()
 *         ));
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface TraceResolver {

    /**
     * Resolves the distributed tracing context.
     *
     * @return the resolved trace context, or empty if not available
     */
    Optional<TraceContext> resolve();
}
