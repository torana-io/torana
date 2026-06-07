package io.torana.resilience;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.torana.api.model.AuditEntry;
import io.torana.api.model.RequestContext;
import io.torana.api.model.TraceContext;
import io.torana.spi.FallbackAuditWriter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fallback writer that writes audit entries to structured logs.
 *
 * <p>This implementation is useful for temporary fallback during database outages. Audit entries
 * are written as JSON to a dedicated logger, which can be captured by log aggregation systems
 * (Splunk, ELK, CloudWatch Logs, etc.) and later replayed or analyzed.
 *
 * <p>Features:
 *
 * <ul>
 *   <li>JSON-formatted log entries for easy parsing
 *   <li>Dedicated logger for fallback entries
 *   <li>Never throws exceptions (safe fallback of last resort)
 *   <li>Minimal overhead (no file I/O)
 * </ul>
 *
 * <p>Example log output:
 *
 * <pre>
 * 2026-06-06T10:30:45.123Z INFO  i.t.r.LoggingFallbackWriter - AUDIT_FALLBACK: {"id":"123e4567-e89b-12d3-a456-426614174000","action":"order.created","actor_id":"alice","target_type":"Order","target_id":"12345","outcome":"success","occurred_at":"2026-06-06T10:30:45.000Z"}
 * </pre>
 *
 * <p>Log aggregation query examples:
 *
 * <pre>
 * # Splunk
 * sourcetype=app_logs "AUDIT_FALLBACK" | spath | stats count by action
 *
 * # CloudWatch Logs Insights
 * fields @timestamp, action, actor_id, target_type
 * | filter @message like /AUDIT_FALLBACK/
 * | parse @message /AUDIT_FALLBACK: (?<entry>.*)/
 * </pre>
 */
public class LoggingFallbackWriter implements FallbackAuditWriter {

    /**
     * Dedicated logger for fallback audit entries.
     *
     * <p>Configure this logger separately in your logging configuration to route fallback entries
     * to a specific appender or log level.
     *
     * <p>Example Logback configuration:
     *
     * <pre>{@code
     * <logger name="io.torana.resilience.LoggingFallbackWriter" level="INFO" additivity="false">
     *     <appender-ref ref="AUDIT_FALLBACK_FILE"/>
     * </logger>
     * }</pre>
     */
    private static final Logger log = LoggerFactory.getLogger(LoggingFallbackWriter.class);

    private static final String LOG_PREFIX = "AUDIT_FALLBACK:";

    private final ObjectMapper objectMapper;

    /** Creates a logging fallback writer with default JSON serialization. */
    public LoggingFallbackWriter() {
        this.objectMapper =
                new ObjectMapper()
                        .registerModule(new JavaTimeModule())
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Creates a logging fallback writer with custom JSON serialization.
     *
     * @param objectMapper custom ObjectMapper for JSON serialization
     */
    public LoggingFallbackWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void writeFallback(AuditEntry entry) {
        try {
            String json = serializeToJson(entry);
            log.info("{} {}", LOG_PREFIX, json);
        } catch (Exception e) {
            log.error(
                    "{} Failed to serialize audit entry (id={}, action={}): {}",
                    LOG_PREFIX,
                    entry.id(),
                    entry.action(),
                    e.getMessage());

            log.info(
                    "{} action={}, actor={}, target={}:{}, outcome={}, occurred_at={}",
                    LOG_PREFIX,
                    entry.action(),
                    entry.actor() == null ? null : entry.actor().id(),
                    entry.target() == null ? null : entry.target().type(),
                    entry.target() == null ? null : entry.target().id(),
                    entry.outcome(),
                    entry.occurredAt());
        }
    }

    @Override
    public void writeFallbackBatch(List<AuditEntry> entries) {
        log.info("{} Writing batch of {} entries", LOG_PREFIX, entries.size());
        for (AuditEntry entry : entries) {
            writeFallback(entry);
        }
    }

    @Override
    public String getFallbackType() {
        return "logging";
    }

    /**
     * Serializes an audit entry to JSON.
     *
     * <p>The JSON structure includes all audit entry fields in a flat format for easy parsing by
     * log aggregation tools.
     *
     * @param entry the audit entry to serialize
     * @return JSON string
     * @throws JsonProcessingException if serialization fails
     */
    private String serializeToJson(AuditEntry entry) throws JsonProcessingException {
        // Create a flat map for easy parsing by log aggregation tools
        Map<String, Object> entryMap = new LinkedHashMap<>();
        entryMap.put("id", entry.id().toString());
        entryMap.put("action", entry.action().name());
        entryMap.put("actor_id", entry.actor() == null ? null : entry.actor().id());
        entryMap.put("tenant_id", entry.tenant() == null ? null : entry.tenant().id());
        entryMap.put("target_type", entry.target() == null ? null : entry.target().type());
        entryMap.put("target_id", entry.target() == null ? null : entry.target().id());
        entryMap.put("outcome", entry.outcome().name());
        entryMap.put("occurred_at", entry.occurredAt());

        RequestContext requestContext = entry.requestContext();
        if (requestContext != null && requestContext.requestId() != null) {
            entryMap.put("request_id", requestContext.requestId());
        }

        TraceContext traceContext = entry.traceContext();
        if (traceContext != null && traceContext.traceId() != null) {
            entryMap.put("trace_id", traceContext.traceId());
        }
        if (traceContext != null && traceContext.spanId() != null) {
            entryMap.put("span_id", traceContext.spanId());
        }
        if (entry.metadata() != null && !entry.metadata().isEmpty()) {
            entryMap.put("metadata", entry.metadata());
        }
        if (entry.changes() != null && !entry.changes().isEmpty()) {
            entryMap.put("changes", entry.changes());
        }

        return objectMapper.writeValueAsString(entryMap);
    }
}
