package io.torana.jdbc;

import static org.assertj.core.api.Assertions.assertThat;

import io.torana.api.AuditEntryView;
import io.torana.api.model.*;
import io.torana.jdbc.dialect.PostgreSqlDialect;
import io.torana.jdbc.dialect.SqlDialect;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

/** Integration tests for JdbcAuditWriter with PostgreSQL using Testcontainers. */
@Testcontainers
class JdbcAuditWriterPostgresIT {

    @Container
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("torana_test")
                    .withUsername("test")
                    .withPassword("test");

    private static DataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static SqlDialect dialect;
    private static JdbcAuditWriter writer;

    @BeforeAll
    static void setupDatabase() {
        dataSource = createDataSource();
        jdbcTemplate = new JdbcTemplate(dataSource);
        dialect = new PostgreSqlDialect();

        // Run Flyway migrations
        Flyway flyway =
                Flyway.configure()
                        .dataSource(dataSource)
                        .locations("classpath:db/migration/postgresql")
                        .load();
        flyway.migrate();

        writer = new JdbcAuditWriter(jdbcTemplate, dialect, "audit_entries");
    }

    private static DataSource createDataSource() {
        DriverManagerDataSource ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(postgres.getJdbcUrl());
        ds.setUsername(postgres.getUsername());
        ds.setPassword(postgres.getPassword());
        return ds;
    }

    private JdbcAuditQueryExecutor newQuery() {
        return new JdbcAuditQueryExecutor(jdbcTemplate, dialect, "audit_entries");
    }

    @BeforeEach
    void cleanTable() {
        jdbcTemplate.execute("TRUNCATE TABLE audit_entries");
    }

    @Test
    void writeAndReadEntry_success() {
        // Given
        AuditEntry entry = createTestEntry("order.created");

        // When
        writer.write(entry);

        // Then
        List<AuditEntryView> results = newQuery().action("order.created").execute();

        assertThat(results).hasSize(1);
        AuditEntryView result = results.get(0);
        assertThat(result.id()).isEqualTo(entry.id());
        assertThat(result.action()).isEqualTo("order.created");
        assertThat(result.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(result.actor().id()).isEqualTo("user-123");
    }

    @Test
    void writeWithMetadata_preservesJsonb() {
        // Given
        Map<String, Object> metadata =
                Map.of("orderId", "ORD-12345", "amount", 99.99, "items", List.of("item1", "item2"));
        AuditEntry entry = createTestEntryWithMetadata("order.placed", metadata);

        // When
        writer.write(entry);

        // Then
        List<AuditEntryView> results = newQuery().action("order.placed").execute();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).metadata()).containsEntry("orderId", "ORD-12345");
    }

    @Test
    void writeWithChanges_preservesFieldChanges() {
        // Given
        ChangeSet changes =
                ChangeSet.of(
                        List.of(
                                new FieldChange(
                                        "status",
                                        FieldChange.ChangeType.MODIFIED,
                                        "pending",
                                        "shipped"),
                                new FieldChange(
                                        "trackingNumber",
                                        FieldChange.ChangeType.ADDED,
                                        null,
                                        "TRK-123")));
        AuditEntry entry = createTestEntryWithChanges("order.shipped", changes);

        // When
        writer.write(entry);

        // Then
        List<AuditEntryView> results = newQuery().action("order.shipped").execute();

        assertThat(results).hasSize(1);
        assertThat(results.get(0).changes()).isNotNull();
        assertThat(results.get(0).changes().changes()).hasSize(2);
    }

    @Test
    void queryByActor_filtersCorrectly() {
        // Given
        writer.write(createTestEntryWithActor("action.one", "user-A"));
        writer.write(createTestEntryWithActor("action.two", "user-B"));
        writer.write(createTestEntryWithActor("action.three", "user-A"));

        // When
        List<AuditEntryView> results = newQuery().actor("user-A").execute();

        // Then
        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(r -> assertThat(r.actor().id()).isEqualTo("user-A"));
    }

    @Test
    void queryByTimeRange_filtersCorrectly() {
        // Given
        Instant now = Instant.now();
        Instant oneHourAgo = now.minusSeconds(3600);
        Instant twoHoursAgo = now.minusSeconds(7200);

        writer.write(createTestEntryAtTime("old.action", twoHoursAgo));
        writer.write(createTestEntryAtTime("recent.action", oneHourAgo.plusSeconds(1800)));
        writer.write(createTestEntryAtTime("newer.action", now.minusSeconds(300)));

        // When
        List<AuditEntryView> results = newQuery().from(oneHourAgo).to(now).execute();

        // Then
        assertThat(results).hasSize(2);
    }

    @Test
    void queryWithPagination_returnsCorrectPage() {
        // Given
        for (int i = 0; i < 10; i++) {
            writer.write(createTestEntry("action.test" + i));
        }

        // When
        List<AuditEntryView> results = newQuery().limit(3).offset(2).execute();

        // Then
        assertThat(results).hasSize(3);
    }

    @Test
    void queryByOutcome_filtersCorrectly() {
        // Given
        writer.write(createTestEntryWithOutcome("success.action", AuditOutcome.SUCCESS));
        writer.write(createTestEntryWithOutcome("failure.action", AuditOutcome.FAILURE));
        writer.write(createTestEntryWithOutcome("another.success", AuditOutcome.SUCCESS));

        // When
        List<AuditEntryView> results = newQuery().outcome(AuditOutcome.FAILURE).execute();

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).action()).isEqualTo("failure.action");
    }

    @Test
    void count_returnsCorrectTotal() {
        // Given
        writer.write(createTestEntry("count.test.one"));
        writer.write(createTestEntry("count.test.two"));
        writer.write(createTestEntry("count.test.three"));

        // When
        long count = newQuery().count();

        // Then
        assertThat(count).isEqualTo(3);
    }

    private AuditEntry createTestEntry(String action) {
        return new AuditEntry(
                UUID.randomUUID(),
                AuditAction.of(action),
                Instant.now(),
                AuditOutcome.SUCCESS,
                new Actor("user-123", ActorType.USER, "Test User", Map.of()),
                new Tenant("tenant-1", "Test Tenant"),
                new Target("Order", "order-456", "Order #456"),
                new RequestContext(
                        "req-789", "POST", "/api/orders", "192.168.1.1", "TestAgent/1.0", Map.of()),
                new TraceContext("trace-abc", "span-def", null),
                Map.of("test", "value"),
                ChangeSet.empty(),
                null,
                1);
    }

    private AuditEntry createTestEntryWithMetadata(String action, Map<String, Object> metadata) {
        return new AuditEntry(
                UUID.randomUUID(),
                AuditAction.of(action),
                Instant.now(),
                AuditOutcome.SUCCESS,
                new Actor("user-123", ActorType.USER, "Test User", Map.of()),
                null,
                null,
                null,
                null,
                metadata,
                ChangeSet.empty(),
                null,
                1);
    }

    private AuditEntry createTestEntryWithChanges(String action, ChangeSet changes) {
        return new AuditEntry(
                UUID.randomUUID(),
                AuditAction.of(action),
                Instant.now(),
                AuditOutcome.SUCCESS,
                new Actor("user-123", ActorType.USER, "Test User", Map.of()),
                null,
                new Target("Order", "order-123", "Order"),
                null,
                null,
                Map.of(),
                changes,
                null,
                1);
    }

    private AuditEntry createTestEntryWithActor(String action, String actorId) {
        return new AuditEntry(
                UUID.randomUUID(),
                AuditAction.of(action),
                Instant.now(),
                AuditOutcome.SUCCESS,
                new Actor(actorId, ActorType.USER, "User " + actorId, Map.of()),
                null,
                null,
                null,
                null,
                Map.of(),
                ChangeSet.empty(),
                null,
                1);
    }

    private AuditEntry createTestEntryAtTime(String action, Instant occurredAt) {
        return new AuditEntry(
                UUID.randomUUID(),
                AuditAction.of(action),
                occurredAt,
                AuditOutcome.SUCCESS,
                new Actor("user-123", ActorType.USER, "Test User", Map.of()),
                null,
                null,
                null,
                null,
                Map.of(),
                ChangeSet.empty(),
                null,
                1);
    }

    private AuditEntry createTestEntryWithOutcome(String action, AuditOutcome outcome) {
        return new AuditEntry(
                UUID.randomUUID(),
                AuditAction.of(action),
                Instant.now(),
                outcome,
                new Actor("user-123", ActorType.USER, "Test User", Map.of()),
                null,
                null,
                null,
                null,
                Map.of(),
                ChangeSet.empty(),
                outcome == AuditOutcome.FAILURE ? "Test failure" : null,
                1);
    }
}
