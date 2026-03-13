package io.torana.jdbc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.torana.api.model.Actor;
import io.torana.api.model.AuditEntry;
import io.torana.api.model.AuditOutcome;
import io.torana.api.model.ChangeSet;
import io.torana.api.model.FieldChange;
import io.torana.api.model.RequestContext;
import io.torana.api.model.Target;
import io.torana.api.model.Tenant;
import io.torana.api.model.TraceContext;
import io.torana.jdbc.dialect.H2Dialect;
import io.torana.jdbc.dialect.SqlDialect;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

class JdbcAuditWriterTest {

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private SqlDialect dialect;
    private JdbcAuditWriter writer;

    @BeforeEach
    void setUp() {
        dataSource =
                new EmbeddedDatabaseBuilder()
                        .setType(EmbeddedDatabaseType.H2)
                        .generateUniqueName(true)
                        .addScript("db/migration/h2/V1__create_audit_entries_table.sql")
                        .build();
        jdbcTemplate = new JdbcTemplate(dataSource);
        dialect = new H2Dialect();
        writer = new JdbcAuditWriter(jdbcTemplate, dialect);
    }

    @Test
    void writesMinimalEntry() {
        AuditEntry entry =
                AuditEntry.builder()
                        .id(UUID.randomUUID())
                        .action("order.created")
                        .occurredAt(Instant.now())
                        .outcome(AuditOutcome.SUCCESS)
                        .build();

        writer.write(entry);

        Long count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM audit_entries WHERE id = ?", Long.class, entry.id());
        assertThat(count).isEqualTo(1);
    }

    @Test
    void writesFullEntry() {
        UUID id = UUID.randomUUID();
        Instant occurredAt = Instant.now();

        AuditEntry entry =
                AuditEntry.builder()
                        .id(id)
                        .action("order.cancelled")
                        .occurredAt(occurredAt)
                        .outcome(AuditOutcome.SUCCESS)
                        .actor(Actor.user("user-123", "John Doe"))
                        .tenant(Tenant.of("tenant-1", "Acme Corp"))
                        .target(Target.of("Order", "order-456", "Order #456"))
                        .requestContext(
                                RequestContext.builder()
                                        .requestId("req-789")
                                        .method("POST")
                                        .path("/api/orders/order-456/cancel")
                                        .clientIp("192.168.1.1")
                                        .userAgent("Mozilla/5.0")
                                        .build())
                        .traceContext(TraceContext.of("trace-abc", "span-def", "parent-ghi"))
                        .metadata(Map.of("reason", "Customer request"))
                        .changes(
                                ChangeSet.of(FieldChange.modified("status", "ACTIVE", "CANCELLED")))
                        .build();

        writer.write(entry);

        Map<String, Object> row =
                jdbcTemplate.queryForMap("SELECT * FROM audit_entries WHERE id = ?", id);

        assertThat(row.get("action")).isEqualTo("order.cancelled");
        assertThat(row.get("outcome")).isEqualTo("SUCCESS");
        assertThat(row.get("actor_id")).isEqualTo("user-123");
        assertThat(row.get("actor_name")).isEqualTo("John Doe");
        assertThat(row.get("tenant_id")).isEqualTo("tenant-1");
        assertThat(row.get("tenant_name")).isEqualTo("Acme Corp");
        assertThat(row.get("target_type")).isEqualTo("Order");
        assertThat(row.get("target_id")).isEqualTo("order-456");
        assertThat(row.get("request_id")).isEqualTo("req-789");
        assertThat(row.get("request_method")).isEqualTo("POST");
        assertThat(row.get("trace_id")).isEqualTo("trace-abc");
        assertThat(row.get("span_id")).isEqualTo("span-def");
    }

    @Test
    void writesEntryWithFailureOutcome() {
        AuditEntry entry =
                AuditEntry.builder()
                        .action("payment.processed")
                        .outcome(AuditOutcome.FAILURE)
                        .errorMessage("Insufficient funds")
                        .build();

        writer.write(entry);

        Map<String, Object> row =
                jdbcTemplate.queryForMap("SELECT * FROM audit_entries WHERE id = ?", entry.id());

        assertThat(row.get("outcome")).isEqualTo("FAILURE");
        assertThat(row.get("error_message")).isEqualTo("Insufficient funds");
    }

    @Test
    void writesBatchOfEntries() {
        List<AuditEntry> entries =
                List.of(
                        AuditEntry.builder().action("order.created").build(),
                        AuditEntry.builder().action("order.updated").build(),
                        AuditEntry.builder().action("order.completed").build());

        writer.writeBatch(entries);

        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_entries", Long.class);
        assertThat(count).isEqualTo(3);
    }

    @Test
    void writeBatchHandlesEmptyList() {
        writer.writeBatch(List.of());

        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_entries", Long.class);
        assertThat(count).isEqualTo(0);
    }

    @Test
    void writesEntryWithNullOptionalFields() {
        AuditEntry entry =
                AuditEntry.builder().action("system.startup").outcome(AuditOutcome.SUCCESS).build();

        writer.write(entry);

        Map<String, Object> row =
                jdbcTemplate.queryForMap("SELECT * FROM audit_entries WHERE id = ?", entry.id());

        assertThat(row.get("actor_id")).isNull();
        assertThat(row.get("tenant_id")).isNull();
        assertThat(row.get("target_type")).isNull();
        assertThat(row.get("error_message")).isNull();
    }

    @Test
    void rejectsNullEntry() {
        assertThatThrownBy(() -> writer.write(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("entry must not be null");
    }

    @Test
    void rejectsNullEntriesList() {
        assertThatThrownBy(() -> writer.writeBatch(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("entries must not be null");
    }

    @Test
    void exposesTableName() {
        assertThat(writer.getTableName()).isEqualTo("audit_entries");
    }

    @Test
    void exposesDialect() {
        assertThat(writer.getDialect()).isEqualTo(dialect);
    }

    @Test
    void supportsCustomTableName() {
        JdbcAuditWriter customWriter = new JdbcAuditWriter(jdbcTemplate, dialect, "custom_audit");
        assertThat(customWriter.getTableName()).isEqualTo("custom_audit");
    }
}
