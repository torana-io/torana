package io.torana.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.torana.api.model.Actor;
import io.torana.api.model.AuditEntry;
import io.torana.api.model.RequestContext;
import io.torana.api.model.Target;
import io.torana.api.model.Tenant;
import io.torana.api.model.TraceContext;
import io.torana.jdbc.dialect.SqlDialect;
import io.torana.spi.AuditWriter;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;

/**
 * JDBC-based implementation of {@link AuditWriter}.
 *
 * <p>Persists audit entries to a relational database using Spring's {@link JdbcTemplate}. Supports
 * multiple database dialects through the {@link SqlDialect} abstraction.
 *
 * <p>JSON serialization of metadata and changes is handled by Jackson.
 */
public class JdbcAuditWriter implements AuditWriter {

    private static final String DEFAULT_TABLE_NAME = "audit_entries";

    private final JdbcTemplate jdbcTemplate;
    private final SqlDialect dialect;
    private final String tableName;
    private final ObjectMapper objectMapper;

    /**
     * Creates a new JdbcAuditWriter with the default table name.
     *
     * @param jdbcTemplate the JDBC template
     * @param dialect the SQL dialect
     */
    public JdbcAuditWriter(JdbcTemplate jdbcTemplate, SqlDialect dialect) {
        this(jdbcTemplate, dialect, DEFAULT_TABLE_NAME);
    }

    /**
     * Creates a new JdbcAuditWriter with a custom table name.
     *
     * @param jdbcTemplate the JDBC template
     * @param dialect the SQL dialect
     * @param tableName the table name
     */
    public JdbcAuditWriter(JdbcTemplate jdbcTemplate, SqlDialect dialect, String tableName) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        this.objectMapper = createObjectMapper();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }

    @Override
    public void write(AuditEntry entry) {
        Objects.requireNonNull(entry, "entry must not be null");

        String sql = dialect.getInsertSql(tableName);

        jdbcTemplate.update(
                sql,
                // id, action, occurred_at, outcome
                entry.id(),
                entry.action().name(),
                Timestamp.from(entry.occurredAt()),
                entry.outcome().name(),
                // actor_id, actor_type, actor_name
                extractActorId(entry.actor()),
                extractActorType(entry.actor()),
                extractActorName(entry.actor()),
                // tenant_id, tenant_name
                extractTenantId(entry.tenant()),
                extractTenantName(entry.tenant()),
                // target_type, target_id, target_name
                extractTargetType(entry.target()),
                extractTargetId(entry.target()),
                extractTargetName(entry.target()),
                // request_id, request_method, request_path, client_ip, user_agent
                extractRequestId(entry.requestContext()),
                extractRequestMethod(entry.requestContext()),
                extractRequestPath(entry.requestContext()),
                extractClientIp(entry.requestContext()),
                extractUserAgent(entry.requestContext()),
                // trace_id, span_id, parent_span_id
                extractTraceId(entry.traceContext()),
                extractSpanId(entry.traceContext()),
                extractParentSpanId(entry.traceContext()),
                // metadata, changes (serialize just the changes list, not the wrapper)
                dialect.toJsonValue(serializeToJson(entry.metadata())),
                dialect.toJsonValue(
                        serializeToJson(
                                entry.changes() != null ? entry.changes().changes() : null)),
                // error_message, schema_version
                entry.errorMessage(),
                entry.schemaVersion());
    }

    @Override
    public void writeBatch(List<AuditEntry> entries) {
        Objects.requireNonNull(entries, "entries must not be null");

        if (entries.isEmpty()) {
            return;
        }

        String sql = dialect.getInsertSql(tableName);

        jdbcTemplate.batchUpdate(
                sql,
                entries,
                entries.size(),
                (ps, entry) -> {
                    int idx = 1;
                    // id, action, occurred_at, outcome
                    ps.setObject(idx++, entry.id());
                    ps.setString(idx++, entry.action().name());
                    ps.setTimestamp(idx++, Timestamp.from(entry.occurredAt()));
                    ps.setString(idx++, entry.outcome().name());
                    // actor_id, actor_type, actor_name
                    ps.setString(idx++, extractActorId(entry.actor()));
                    ps.setString(idx++, extractActorType(entry.actor()));
                    ps.setString(idx++, extractActorName(entry.actor()));
                    // tenant_id, tenant_name
                    ps.setString(idx++, extractTenantId(entry.tenant()));
                    ps.setString(idx++, extractTenantName(entry.tenant()));
                    // target_type, target_id, target_name
                    ps.setString(idx++, extractTargetType(entry.target()));
                    ps.setString(idx++, extractTargetId(entry.target()));
                    ps.setString(idx++, extractTargetName(entry.target()));
                    // request_id, request_method, request_path, client_ip, user_agent
                    ps.setString(idx++, extractRequestId(entry.requestContext()));
                    ps.setString(idx++, extractRequestMethod(entry.requestContext()));
                    ps.setString(idx++, extractRequestPath(entry.requestContext()));
                    ps.setString(idx++, extractClientIp(entry.requestContext()));
                    ps.setString(idx++, extractUserAgent(entry.requestContext()));
                    // trace_id, span_id, parent_span_id
                    ps.setString(idx++, extractTraceId(entry.traceContext()));
                    ps.setString(idx++, extractSpanId(entry.traceContext()));
                    ps.setString(idx++, extractParentSpanId(entry.traceContext()));
                    // metadata, changes (serialize just the changes list, not the wrapper)
                    ps.setObject(idx++, dialect.toJsonValue(serializeToJson(entry.metadata())));
                    ps.setObject(
                            idx++,
                            dialect.toJsonValue(
                                    serializeToJson(
                                            entry.changes() != null
                                                    ? entry.changes().changes()
                                                    : null)));
                    // error_message, schema_version
                    ps.setString(idx++, entry.errorMessage());
                    ps.setInt(idx, entry.schemaVersion());
                });
    }

    private String extractActorId(Actor actor) {
        return actor != null ? actor.id() : null;
    }

    private String extractActorType(Actor actor) {
        return actor != null && actor.type() != null ? actor.type().name() : null;
    }

    private String extractActorName(Actor actor) {
        return actor != null ? actor.displayName() : null;
    }

    private String extractTenantId(Tenant tenant) {
        return tenant != null ? tenant.id() : null;
    }

    private String extractTenantName(Tenant tenant) {
        return tenant != null ? tenant.name() : null;
    }

    private String extractTargetType(Target target) {
        return target != null ? target.type() : null;
    }

    private String extractTargetId(Target target) {
        return target != null ? target.id() : null;
    }

    private String extractTargetName(Target target) {
        return target != null ? target.displayName() : null;
    }

    private String extractRequestId(RequestContext ctx) {
        return ctx != null ? ctx.requestId() : null;
    }

    private String extractRequestMethod(RequestContext ctx) {
        return ctx != null ? ctx.method() : null;
    }

    private String extractRequestPath(RequestContext ctx) {
        return ctx != null ? ctx.path() : null;
    }

    private String extractClientIp(RequestContext ctx) {
        return ctx != null ? ctx.clientIp() : null;
    }

    private String extractUserAgent(RequestContext ctx) {
        return ctx != null ? ctx.userAgent() : null;
    }

    private String extractTraceId(TraceContext ctx) {
        return ctx != null ? ctx.traceId() : null;
    }

    private String extractSpanId(TraceContext ctx) {
        return ctx != null ? ctx.spanId() : null;
    }

    private String extractParentSpanId(TraceContext ctx) {
        return ctx != null ? ctx.parentSpanId() : null;
    }

    private String serializeToJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize value to JSON", e);
        }
    }

    /**
     * Returns the table name used by this writer.
     *
     * @return the table name
     */
    public String getTableName() {
        return tableName;
    }

    /**
     * Returns the SQL dialect used by this writer.
     *
     * @return the dialect
     */
    public SqlDialect getDialect() {
        return dialect;
    }
}
