package io.torana.jdbc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.torana.api.AuditEntryView;
import io.torana.api.AuditQuery;
import io.torana.api.model.Actor;
import io.torana.api.model.ActorType;
import io.torana.api.model.AuditOutcome;
import io.torana.api.model.ChangeSet;
import io.torana.api.model.FieldChange;
import io.torana.api.model.RequestContext;
import io.torana.api.model.Target;
import io.torana.api.model.Tenant;
import io.torana.api.model.TraceContext;
import io.torana.jdbc.dialect.SqlDialect;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * JDBC-based implementation of {@link AuditQuery}.
 *
 * <p>Executes queries against a relational database using Spring's {@link JdbcTemplate}. Supports
 * multiple database dialects through the {@link SqlDialect} abstraction.
 */
public class JdbcAuditQueryExecutor implements AuditQuery {

    private static final String DEFAULT_TABLE_NAME = "audit_entries";
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final SqlDialect dialect;
    private final String tableName;
    private final ObjectMapper objectMapper;

    // Query parameters
    private String action;
    private String actionPrefix;
    private String actorId;
    private String tenantId;
    private String targetType;
    private String targetId;
    private Instant from;
    private Instant to;
    private AuditOutcome outcome;
    private String requestId;
    private String traceId;
    private int limit = DEFAULT_LIMIT;
    private int offset = 0;

    /**
     * Creates a new JdbcAuditQueryExecutor with the default table name.
     *
     * @param jdbcTemplate the JDBC template
     * @param dialect the SQL dialect
     */
    public JdbcAuditQueryExecutor(JdbcTemplate jdbcTemplate, SqlDialect dialect) {
        this(jdbcTemplate, dialect, DEFAULT_TABLE_NAME);
    }

    /**
     * Creates a new JdbcAuditQueryExecutor with a custom table name.
     *
     * @param jdbcTemplate the JDBC template
     * @param dialect the SQL dialect
     * @param tableName the table name
     */
    public JdbcAuditQueryExecutor(JdbcTemplate jdbcTemplate, SqlDialect dialect, String tableName) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.dialect = Objects.requireNonNull(dialect, "dialect must not be null");
        this.tableName = Objects.requireNonNull(tableName, "tableName must not be null");
        this.objectMapper = createObjectMapper();
    }

    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(
                com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                false);
        return mapper;
    }

    @Override
    public AuditQuery action(String action) {
        this.action = action;
        return this;
    }

    @Override
    public AuditQuery actionPrefix(String prefix) {
        this.actionPrefix = prefix;
        return this;
    }

    @Override
    public AuditQuery actor(String actorId) {
        this.actorId = actorId;
        return this;
    }

    @Override
    public AuditQuery tenant(String tenantId) {
        this.tenantId = tenantId;
        return this;
    }

    @Override
    public AuditQuery target(String type, String id) {
        this.targetType = type;
        this.targetId = id;
        return this;
    }

    @Override
    public AuditQuery targetType(String type) {
        this.targetType = type;
        return this;
    }

    @Override
    public AuditQuery from(Instant from) {
        this.from = from;
        return this;
    }

    @Override
    public AuditQuery to(Instant to) {
        this.to = to;
        return this;
    }

    @Override
    public AuditQuery outcome(AuditOutcome outcome) {
        this.outcome = outcome;
        return this;
    }

    @Override
    public AuditQuery requestId(String requestId) {
        this.requestId = requestId;
        return this;
    }

    @Override
    public AuditQuery traceId(String traceId) {
        this.traceId = traceId;
        return this;
    }

    @Override
    public AuditQuery limit(int limit) {
        this.limit = Math.min(Math.max(1, limit), MAX_LIMIT);
        return this;
    }

    @Override
    public AuditQuery offset(int offset) {
        this.offset = Math.max(0, offset);
        return this;
    }

    @Override
    public List<AuditEntryView> execute() {
        StringBuilder sql = new StringBuilder(dialect.getQueryBaseSql(tableName));
        List<Object> params = new ArrayList<>();

        appendWhereClause(sql, params);
        sql.append(" ORDER BY occurred_at DESC");
        sql.append(" LIMIT ? OFFSET ?");
        params.add(limit);
        params.add(offset);

        return jdbcTemplate.query(sql.toString(), new AuditEntryRowMapper(), params.toArray());
    }

    @Override
    public long count() {
        StringBuilder sql = new StringBuilder(dialect.getCountSql(tableName));
        List<Object> params = new ArrayList<>();

        appendWhereClause(sql, params);

        Long result = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return result != null ? result : 0L;
    }

    private void appendWhereClause(StringBuilder sql, List<Object> params) {
        if (action != null) {
            sql.append(" AND action = ?");
            params.add(action);
        }
        if (actionPrefix != null) {
            sql.append(" AND action LIKE ?");
            params.add(actionPrefix + "%");
        }
        if (actorId != null) {
            sql.append(" AND actor_id = ?");
            params.add(actorId);
        }
        if (tenantId != null) {
            sql.append(" AND tenant_id = ?");
            params.add(tenantId);
        }
        if (targetType != null) {
            sql.append(" AND target_type = ?");
            params.add(targetType);
        }
        if (targetId != null) {
            sql.append(" AND target_id = ?");
            params.add(targetId);
        }
        if (from != null) {
            sql.append(" AND occurred_at >= ?");
            params.add(Timestamp.from(from));
        }
        if (to != null) {
            sql.append(" AND occurred_at <= ?");
            params.add(Timestamp.from(to));
        }
        if (outcome != null) {
            sql.append(" AND outcome = ?");
            params.add(outcome.name());
        }
        if (requestId != null) {
            sql.append(" AND request_id = ?");
            params.add(requestId);
        }
        if (traceId != null) {
            sql.append(" AND trace_id = ?");
            params.add(traceId);
        }
    }

    /**
     * Creates a new query executor with the same configuration.
     *
     * <p>Use this to create fresh queries without sharing state.
     *
     * @return a new JdbcAuditQueryExecutor
     */
    public JdbcAuditQueryExecutor newQuery() {
        return new JdbcAuditQueryExecutor(jdbcTemplate, dialect, tableName);
    }

    /** Immutable implementation of AuditEntryView. */
    private record JdbcAuditEntryView(
            UUID id,
            String action,
            Instant occurredAt,
            AuditOutcome outcome,
            Actor actor,
            Tenant tenant,
            Target target,
            RequestContext requestContext,
            TraceContext traceContext,
            Map<String, Object> metadata,
            ChangeSet changes,
            String errorMessage,
            int schemaVersion)
            implements AuditEntryView {}

    /** Row mapper for audit entries. */
    private class AuditEntryRowMapper implements RowMapper<AuditEntryView> {

        @Override
        public AuditEntryView mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new JdbcAuditEntryView(
                    UUID.fromString(rs.getString("id")),
                    rs.getString("action"),
                    rs.getTimestamp("occurred_at").toInstant(),
                    AuditOutcome.valueOf(rs.getString("outcome")),
                    mapActor(rs),
                    mapTenant(rs),
                    mapTarget(rs),
                    mapRequestContext(rs),
                    mapTraceContext(rs),
                    parseMetadata(rs.getString("metadata")),
                    parseChanges(rs.getString("changes")),
                    rs.getString("error_message"),
                    rs.getInt("schema_version"));
        }

        private Actor mapActor(ResultSet rs) throws SQLException {
            String actorId = rs.getString("actor_id");
            if (actorId == null) {
                return null;
            }
            String actorTypeStr = rs.getString("actor_type");
            ActorType actorType =
                    actorTypeStr != null ? ActorType.valueOf(actorTypeStr) : ActorType.USER;
            String actorName = rs.getString("actor_name");
            return new Actor(actorId, actorType, actorName, Map.of());
        }

        private Tenant mapTenant(ResultSet rs) throws SQLException {
            String tenantId = rs.getString("tenant_id");
            if (tenantId == null) {
                return null;
            }
            String tenantName = rs.getString("tenant_name");
            return new Tenant(tenantId, tenantName);
        }

        private Target mapTarget(ResultSet rs) throws SQLException {
            String targetType = rs.getString("target_type");
            String targetId = rs.getString("target_id");
            if (targetType == null || targetId == null) {
                return null;
            }
            String targetName = rs.getString("target_name");
            return new Target(targetType, targetId, targetName);
        }

        private RequestContext mapRequestContext(ResultSet rs) throws SQLException {
            return new RequestContext(
                    rs.getString("request_id"),
                    rs.getString("request_method"),
                    rs.getString("request_path"),
                    rs.getString("client_ip"),
                    rs.getString("user_agent"),
                    Map.of());
        }

        private TraceContext mapTraceContext(ResultSet rs) throws SQLException {
            return new TraceContext(
                    rs.getString("trace_id"),
                    rs.getString("span_id"),
                    rs.getString("parent_span_id"));
        }

        private Map<String, Object> parseMetadata(String json) {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            try {
                return objectMapper.readValue(json, new TypeReference<>() {});
            } catch (JsonProcessingException e) {
                return Map.of();
            }
        }

        private ChangeSet parseChanges(String json) {
            if (json == null || json.isBlank()) {
                return ChangeSet.empty();
            }
            try {
                // Changes are stored as a list directly
                List<FieldChange> changes =
                        objectMapper.readValue(json, new TypeReference<List<FieldChange>>() {});
                return ChangeSet.of(changes);
            } catch (JsonProcessingException e) {
                return ChangeSet.empty();
            }
        }
    }
}
