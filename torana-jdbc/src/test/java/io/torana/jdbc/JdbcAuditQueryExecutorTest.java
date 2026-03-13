package io.torana.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.torana.api.AuditEntryView;
import io.torana.api.model.Actor;
import io.torana.api.model.AuditEntry;
import io.torana.api.model.AuditOutcome;
import io.torana.api.model.ChangeSet;
import io.torana.api.model.FieldChange;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

class JdbcAuditQueryExecutorTest {

    private DataSource dataSource;
    private JdbcTemplate jdbcTemplate;
    private SqlDialect dialect;
    private JdbcAuditWriter writer;
    private JdbcAuditQueryExecutor queryExecutor;

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
        queryExecutor = new JdbcAuditQueryExecutor(jdbcTemplate, dialect);
    }

    @Test
    void findsEntryByAction() {
        writer.write(AuditEntry.builder().action("order.created").build());
        writer.write(AuditEntry.builder().action("order.cancelled").build());
        writer.write(AuditEntry.builder().action("payment.processed").build());

        List<AuditEntryView> results = queryExecutor.newQuery().action("order.created").execute();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).action()).isEqualTo("order.created");
    }

    @Test
    void findsEntriesByActionPrefix() {
        writer.write(AuditEntry.builder().action("order.created").build());
        writer.write(AuditEntry.builder().action("order.cancelled").build());
        writer.write(AuditEntry.builder().action("payment.processed").build());

        List<AuditEntryView> results = queryExecutor.newQuery().actionPrefix("order.").execute();

        assertThat(results).hasSize(2);
        assertThat(results)
                .extracting(AuditEntryView::action)
                .containsExactlyInAnyOrder("order.created", "order.cancelled");
    }

    @Test
    void findsEntriesByActor() {
        writer.write(
                AuditEntry.builder()
                        .action("order.created")
                        .actor(Actor.user("alice", "Alice"))
                        .build());
        writer.write(
                AuditEntry.builder()
                        .action("order.created")
                        .actor(Actor.user("bob", "Bob"))
                        .build());

        List<AuditEntryView> results = queryExecutor.newQuery().actor("alice").execute();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).actor().id()).isEqualTo("alice");
    }

    @Test
    void findsEntriesByTenant() {
        writer.write(
                AuditEntry.builder().action("order.created").tenant(Tenant.of("tenant-1")).build());
        writer.write(
                AuditEntry.builder().action("order.created").tenant(Tenant.of("tenant-2")).build());

        List<AuditEntryView> results = queryExecutor.newQuery().tenant("tenant-1").execute();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).tenant().id()).isEqualTo("tenant-1");
    }

    @Test
    void findsEntriesByTarget() {
        writer.write(
                AuditEntry.builder()
                        .action("order.updated")
                        .target(Target.of("Order", "order-123"))
                        .build());
        writer.write(
                AuditEntry.builder()
                        .action("order.updated")
                        .target(Target.of("Order", "order-456"))
                        .build());

        List<AuditEntryView> results =
                queryExecutor.newQuery().target("Order", "order-123").execute();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).target().id()).isEqualTo("order-123");
    }

    @Test
    void findsEntriesByTargetType() {
        writer.write(
                AuditEntry.builder()
                        .action("entity.created")
                        .target(Target.of("Order", "order-123"))
                        .build());
        writer.write(
                AuditEntry.builder()
                        .action("entity.created")
                        .target(Target.of("Invoice", "inv-456"))
                        .build());

        List<AuditEntryView> results = queryExecutor.newQuery().targetType("Order").execute();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).target().type()).isEqualTo("Order");
    }

    @Test
    void findsEntriesByTimeRange() {
        Instant now = Instant.now();
        writer.write(
                AuditEntry.builder()
                        .action("old.action")
                        .occurredAt(now.minus(Duration.ofDays(10)))
                        .build());
        writer.write(
                AuditEntry.builder()
                        .action("recent.action")
                        .occurredAt(now.minus(Duration.ofHours(1)))
                        .build());

        List<AuditEntryView> results =
                queryExecutor.newQuery().from(now.minus(Duration.ofDays(1))).to(now).execute();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).action()).isEqualTo("recent.action");
    }

    @Test
    void findsEntriesByOutcome() {
        writer.write(
                AuditEntry.builder().action("order.created").outcome(AuditOutcome.SUCCESS).build());
        writer.write(
                AuditEntry.builder().action("order.created").outcome(AuditOutcome.FAILURE).build());

        List<AuditEntryView> results =
                queryExecutor.newQuery().outcome(AuditOutcome.FAILURE).execute();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).outcome()).isEqualTo(AuditOutcome.FAILURE);
    }

    @Test
    void findsEntriesByTraceId() {
        writer.write(
                AuditEntry.builder()
                        .action("order.created")
                        .traceContext(TraceContext.of("trace-123", "span-1"))
                        .build());
        writer.write(
                AuditEntry.builder()
                        .action("order.updated")
                        .traceContext(TraceContext.of("trace-456", "span-2"))
                        .build());

        List<AuditEntryView> results = queryExecutor.newQuery().traceId("trace-123").execute();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).traceContext().traceId()).isEqualTo("trace-123");
    }

    @Test
    void appliesLimit() {
        for (int i = 0; i < 10; i++) {
            writer.write(AuditEntry.builder().action("test.action").build());
        }

        List<AuditEntryView> results = queryExecutor.newQuery().limit(5).execute();

        assertThat(results).hasSize(5);
    }

    @Test
    void appliesOffset() {
        for (int i = 0; i < 10; i++) {
            writer.write(
                    AuditEntry.builder()
                            .action("test.action")
                            .occurredAt(Instant.now().plusSeconds(i))
                            .build());
        }

        List<AuditEntryView> results = queryExecutor.newQuery().limit(3).offset(3).execute();

        assertThat(results).hasSize(3);
    }

    @Test
    void countsMatchingEntries() {
        for (int i = 0; i < 10; i++) {
            writer.write(AuditEntry.builder().action("order.created").build());
        }
        writer.write(AuditEntry.builder().action("payment.processed").build());

        long count = queryExecutor.newQuery().action("order.created").count();

        assertThat(count).isEqualTo(10);
    }

    @Test
    void combinesMultipleFilters() {
        writer.write(
                AuditEntry.builder()
                        .action("order.created")
                        .actor(Actor.user("alice", "Alice"))
                        .tenant(Tenant.of("tenant-1"))
                        .outcome(AuditOutcome.SUCCESS)
                        .build());
        writer.write(
                AuditEntry.builder()
                        .action("order.created")
                        .actor(Actor.user("alice", "Alice"))
                        .tenant(Tenant.of("tenant-2"))
                        .outcome(AuditOutcome.SUCCESS)
                        .build());
        writer.write(
                AuditEntry.builder()
                        .action("order.created")
                        .actor(Actor.user("bob", "Bob"))
                        .tenant(Tenant.of("tenant-1"))
                        .outcome(AuditOutcome.SUCCESS)
                        .build());

        List<AuditEntryView> results =
                queryExecutor
                        .newQuery()
                        .action("order.created")
                        .actor("alice")
                        .tenant("tenant-1")
                        .execute();

        assertThat(results).hasSize(1);
    }

    @Test
    void returnsEntriesOrderedByTimeDescending() {
        Instant now = Instant.now();
        writer.write(
                AuditEntry.builder()
                        .action("first")
                        .occurredAt(now.minus(Duration.ofHours(2)))
                        .build());
        writer.write(
                AuditEntry.builder()
                        .action("second")
                        .occurredAt(now.minus(Duration.ofHours(1)))
                        .build());
        writer.write(AuditEntry.builder().action("third").occurredAt(now).build());

        List<AuditEntryView> results = queryExecutor.newQuery().execute();

        assertThat(results)
                .extracting(AuditEntryView::action)
                .containsExactly("third", "second", "first");
    }

    @Test
    void mapsMetadataCorrectly() {
        writer.write(
                AuditEntry.builder()
                        .action("order.created")
                        .metadata(Map.of("key", "value", "count", 42))
                        .build());

        List<AuditEntryView> results = queryExecutor.newQuery().execute();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).metadata()).containsEntry("key", "value");
    }

    @Test
    void mapsChangesCorrectly() {
        writer.write(
                AuditEntry.builder()
                        .action("order.updated")
                        .changes(
                                ChangeSet.of(
                                        FieldChange.modified("status", "PENDING", "COMPLETED")))
                        .build());

        List<AuditEntryView> results = queryExecutor.newQuery().execute();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).changes().changes()).hasSize(1);
    }

    @Test
    void returnsEmptyListWhenNoMatches() {
        List<AuditEntryView> results =
                queryExecutor.newQuery().action("nonexistent.action").execute();

        assertThat(results).isEmpty();
    }

    @Test
    void returnsZeroCountWhenNoMatches() {
        long count = queryExecutor.newQuery().action("nonexistent.action").count();

        assertThat(count).isZero();
    }

    @Test
    void newQueryCreatesIndependentInstance() {
        JdbcAuditQueryExecutor query1 = queryExecutor.newQuery();
        JdbcAuditQueryExecutor query2 = queryExecutor.newQuery();

        query1.action("action1");
        query2.action("action2");

        // They should be independent
        assertThat(query1).isNotSameAs(query2);
    }
}
