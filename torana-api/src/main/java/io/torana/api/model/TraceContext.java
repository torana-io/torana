package io.torana.api.model;

/**
 * Represents distributed tracing correlation data.
 *
 * <p>This captures trace and span IDs for correlating audit entries with distributed traces (e.g.,
 * from Micrometer, OpenTelemetry).
 *
 * <p>This is an immutable value object.
 */
public record TraceContext(String traceId, String spanId, String parentSpanId) {

    private static final TraceContext EMPTY = new TraceContext(null, null, null);

    /**
     * Returns an empty trace context.
     *
     * @return an empty TraceContext
     */
    public static TraceContext empty() {
        return EMPTY;
    }

    /**
     * Creates a trace context with trace and span IDs.
     *
     * @param traceId the trace identifier
     * @param spanId the span identifier
     * @return a new TraceContext
     */
    public static TraceContext of(String traceId, String spanId) {
        return new TraceContext(traceId, spanId, null);
    }

    /**
     * Creates a trace context with all IDs.
     *
     * @param traceId the trace identifier
     * @param spanId the span identifier
     * @param parentSpanId the parent span identifier
     * @return a new TraceContext
     */
    public static TraceContext of(String traceId, String spanId, String parentSpanId) {
        return new TraceContext(traceId, spanId, parentSpanId);
    }

    /**
     * Checks if this context has a trace ID.
     *
     * @return true if traceId is not null
     */
    public boolean hasTraceId() {
        return traceId != null;
    }

    /**
     * Checks if this context is empty (no trace data).
     *
     * @return true if both traceId and spanId are null
     */
    public boolean isEmpty() {
        return traceId == null && spanId == null;
    }
}
