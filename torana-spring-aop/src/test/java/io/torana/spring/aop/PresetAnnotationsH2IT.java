package io.torana.spring.aop;

import static org.assertj.core.api.Assertions.*;

import io.torana.api.model.AuditAction;
import io.torana.api.model.AuditEntry;
import io.torana.api.model.AuditOutcome;
import io.torana.core.*;
import io.torana.core.diff.DefaultDiffEngine;
import io.torana.core.redaction.DefaultRedactionPolicy;
import io.torana.jdbc.JdbcAuditWriter;
import io.torana.jdbc.dialect.H2Dialect;
import io.torana.spi.RedactionPolicy;
import io.torana.spring.aop.testapp.Order;
import io.torana.spring.aop.testapp.OrderService;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.sql.DataSource;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Integration test for preset audit annotations using H2 embedded database.
 *
 * <p>This test runs WITHOUT Docker, making it suitable for CI environments and quick feedback.
 *
 * <p>Verifies:
 * - Preset annotations (@AuditedCreate, @AuditedUpdate, @AuditedDelete) work end-to-end
 * - SpEL expressions are evaluated correctly
 * - Metadata fields are populated
 * - Exception handling works with recordFailures flag
 * - All three preset annotations use correct action names
 */
@SpringJUnitConfig(PresetAnnotationsH2IT.TestConfiguration.class)
class PresetAnnotationsH2IT {

    private static EmbeddedDatabase embeddedDatabase;

    @Autowired private OrderService orderService;

    @Autowired private JdbcTemplate jdbcTemplate;

    @BeforeAll
    static void setupDatabase() {
        embeddedDatabase =
                new EmbeddedDatabaseBuilder()
                        .setType(EmbeddedDatabaseType.H2)
                        .generateUniqueName(true)
                        .build();

        // Run Flyway migration
        Flyway flyway =
                Flyway.configure()
                        .dataSource(embeddedDatabase)
                        .locations("classpath:db/migration/h2")
                        .load();
        flyway.migrate();
    }

    @AfterAll
    static void tearDownDatabase() {
        if (embeddedDatabase != null) {
            embeddedDatabase.shutdown();
        }
    }

    @BeforeEach
    void setUp() {
        orderService.clear();
        jdbcTemplate.execute("DELETE FROM audit_entries");
    }

    @AfterEach
    void tearDown() {
        orderService.clear();
    }

    @Test
    void auditedCreate_capturesEntityCreation() {
        // When: Create an order using @AuditedCreate
        Order order = orderService.createOrder("CUST-123", new BigDecimal("99.99"));

        // Then: Verify audit entry was created
        List<AuditEntry> entries = queryAuditEntries();
        assertThat(entries).hasSize(1);

        AuditEntry entry = entries.get(0);
        assertThat(entry.action().name()).isEqualTo("entity.created");
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(entry.target().type()).isEqualTo("Order");
        assertThat(entry.target().id()).isEqualTo(order.getId());
        assertThat(entry.target().displayName()).isEqualTo(order.getOrderNumber());

        // Verify metadata fields
        Map<String, Object> metadata = entry.metadata();
        assertThat(metadata)
                .containsEntry("customerId", "CUST-123")
                .containsEntry("total", 99.99)
                .containsEntry("operation", "create");

        // Verify tags
        @SuppressWarnings("unchecked")
        List<String> tags = (List<String>) metadata.get("tags");
        assertThat(tags).containsExactly("financial", "customer-facing");
    }

    @Test
    void auditedCreate_capturesSnapshot_whenEnabled() {
        // When: Create order with snapshot capture
        Order order = orderService.createOrderWithSnapshot("CUST-456", new BigDecimal("150.00"));

        // Then: Verify changes field is populated
        List<AuditEntry> entries = queryAuditEntries();
        assertThat(entries).hasSize(1);

        AuditEntry entry = entries.get(0);
        assertThat(entry.changes()).isNotNull();
        assertThat(entry.changes().isEmpty()).isFalse();
    }

    @Test
    void auditedUpdate_capturesEntityUpdate() {
        // Given: An existing order
        Order order = orderService.createOrder("CUST-789", new BigDecimal("200.00"));
        jdbcTemplate.execute("DELETE FROM audit_entries"); // Clear create entry

        // When: Update the order status using @AuditedUpdate
        orderService.updateOrderStatus(order, "CONFIRMED");

        // Then: Verify audit entry was created
        List<AuditEntry> entries = queryAuditEntries();
        assertThat(entries).hasSize(1);

        AuditEntry entry = entries.get(0);
        assertThat(entry.action().name()).isEqualTo("entity.updated");
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(entry.target().type()).isEqualTo("Order");
        assertThat(entry.target().id()).isEqualTo(order.getId());

        // Verify metadata
        Map<String, Object> metadata = entry.metadata();
        assertThat(metadata)
                .containsEntry("newStatus", "CONFIRMED")
                .containsEntry("operation", "update");
    }

    @Test
    void auditedUpdate_capturesChanges_whenEnabled() {
        // Given: An existing order
        Order order = orderService.createOrder("CUST-999", new BigDecimal("300.00"));
        jdbcTemplate.execute("DELETE FROM audit_entries");

        // When: Update the order total with change tracking
        BigDecimal newTotal = new BigDecimal("350.00");
        orderService.updateOrderTotal(order, newTotal);

        // Then: Verify changes were captured
        List<AuditEntry> entries = queryAuditEntries();
        assertThat(entries).hasSize(1);

        AuditEntry entry = entries.get(0);
        assertThat(entry.changes()).isNotNull();
        assertThat(entry.changes().isEmpty()).isFalse();

        // Verify field changes list contains the total change
        assertThat(entry.changes().changes()).isNotEmpty();
    }

    @Test
    void auditedDelete_capturesEntityDeletion() {
        // Given: An existing order
        Order order = orderService.createOrder("CUST-111", new BigDecimal("50.00"));
        String orderId = order.getId();
        jdbcTemplate.execute("DELETE FROM audit_entries");

        // When: Delete the order using @AuditedDelete
        orderService.deleteOrder(orderId, "Customer requested cancellation");

        // Then: Verify audit entry was created
        List<AuditEntry> entries = queryAuditEntries();
        assertThat(entries).hasSize(1);

        AuditEntry entry = entries.get(0);
        assertThat(entry.action().name()).isEqualTo("entity.deleted");
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(entry.target().type()).isEqualTo("Order");
        assertThat(entry.target().id()).isEqualTo(orderId);

        // Verify metadata
        Map<String, Object> metadata = entry.metadata();
        assertThat(metadata)
                .containsEntry("reason", "Customer requested cancellation")
                .containsEntry("operation", "delete");
    }

    @Test
    void auditedDelete_capturesSnapshot_beforeDeletion() {
        // Given: An existing order
        Order order = orderService.createOrder("CUST-222", new BigDecimal("75.00"));
        jdbcTemplate.execute("DELETE FROM audit_entries");

        // When: Delete with snapshot capture
        orderService.deleteOrderWithSnapshot(order, "Duplicate order");

        // Then: Verify changes field is populated
        List<AuditEntry> entries = queryAuditEntries();
        assertThat(entries).hasSize(1);

        AuditEntry entry = entries.get(0);
        assertThat(entry.changes()).isNotNull();
        assertThat(entry.changes().isEmpty()).isFalse();
    }

    @Test
    void auditedCreate_recordsFailure_whenExceptionThrown() {
        // When: Method throws exception with recordFailures=true
        assertThatThrownBy(
                        () ->
                                orderService.createOrderThatFails(
                                        "CUST-ERR", new BigDecimal("100.00")))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Intentional failure for testing");

        // Then: Failure should be audited
        List<AuditEntry> entries = queryAuditEntries();
        assertThat(entries).hasSize(1);

        AuditEntry entry = entries.get(0);
        assertThat(entry.action().name()).isEqualTo("entity.created");
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(entry.errorMessage()).contains("Intentional failure for testing");
        assertThat(entry.target().id()).isEqualTo("CUST-ERR");
    }

    @Test
    void auditedCreate_doesNotRecordFailure_whenRecordFailuresFalse() {
        // When: Method throws exception with recordFailures=false
        assertThatThrownBy(
                        () ->
                                orderService.createOrderWithoutFailureRecording(
                                        "CUST-NO-AUDIT", new BigDecimal("100.00")))
                .isInstanceOf(RuntimeException.class);

        // Then: No audit entry should be created
        List<AuditEntry> entries = queryAuditEntries();
        assertThat(entries).isEmpty();
    }

    @Test
    void spelExpressions_evaluateCorrectly() {
        // Test various SpEL expression patterns

        // #result.property (return value access)
        Order order1 = orderService.createOrder("CUST-SPEL-1", new BigDecimal("10.00"));
        AuditEntry entry1 = queryAuditEntries().get(0);
        assertThat(entry1.target().id()).isEqualTo(order1.getId());
        assertThat(entry1.target().displayName()).isEqualTo(order1.getOrderNumber());

        jdbcTemplate.execute("DELETE FROM audit_entries");

        // #parameter (parameter access)
        orderService.deleteOrder(order1.getId(), "test reason");
        AuditEntry entry2 = queryAuditEntries().get(0);
        assertThat(entry2.target().id()).isEqualTo(order1.getId());
        assertThat(entry2.metadata().get("reason")).isEqualTo("test reason");
    }

    // Helper methods

    private List<AuditEntry> queryAuditEntries() {
        return jdbcTemplate.query(
                "SELECT * FROM audit_entries ORDER BY occurred_at ASC",
                (rs, rowNum) -> {
                    // Simplified mapping for testing
                    String targetType = rs.getString("target_type");
                    String targetId = rs.getString("target_id");
                    String targetName = rs.getString("target_name");

                    io.torana.api.model.Target target = null;
                    if (targetType != null) {
                        target = new io.torana.api.model.Target(targetType, targetId, targetName);
                    }

                    return AuditEntry.builder()
                            .id(java.util.UUID.fromString(rs.getString("id")))
                            .action(AuditAction.of(rs.getString("action")))
                            .occurredAt(rs.getTimestamp("occurred_at").toInstant())
                            .outcome(AuditOutcome.valueOf(rs.getString("outcome")))
                            .target(target)
                            .metadata(parseJson(rs.getString("metadata")))
                            .changes(parseChanges(rs.getString("changes")))
                            .errorMessage(rs.getString("error_message"))
                            .build();
                });
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return new ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return Map.of();
        }
    }

    private io.torana.api.model.ChangeSet parseChanges(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return new ObjectMapper().readValue(json, io.torana.api.model.ChangeSet.class);
        } catch (Exception e) {
            return null;
        }
    }

    @Configuration
    @EnableAspectJAutoProxy
    static class TestConfiguration {

        @Bean
        public DataSource dataSource() {
            // embeddedDatabase is already initialized in @BeforeAll
            if (embeddedDatabase == null) {
                throw new IllegalStateException("Embedded database not initialized");
            }
            return embeddedDatabase;
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public PlatformTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public JdbcAuditWriter jdbcAuditWriter(JdbcTemplate jdbcTemplate) {
            return new JdbcAuditWriter(jdbcTemplate, new H2Dialect(), "audit_entries");
        }

        @Bean
        public ContextCollector contextCollector() {
            return new ContextCollector(List.of(), List.of(), List.of(), List.of());
        }

        @Bean
        public AuditEntryFactory entryFactory() {
            return new AuditEntryFactory(new DefaultDiffEngine());
        }

        @Bean
        public RedactionPolicy redactionPolicy() {
            return DefaultRedactionPolicy.withDefaults();
        }

        @Bean
        public TransactionAwareWriter transactionAwareWriter(JdbcAuditWriter jdbcAuditWriter) {
            return new DefaultTransactionAwareWriter(jdbcAuditWriter);
        }

        @Bean
        public AuditPipeline auditPipeline(
                ContextCollector contextCollector,
                AuditEntryFactory entryFactory,
                RedactionPolicy redactionPolicy,
                TransactionAwareWriter transactionAwareWriter) {
            return new AuditPipeline(
                    contextCollector, entryFactory, redactionPolicy, transactionAwareWriter);
        }

        @Bean
        public io.torana.core.ReflectionSnapshotProvider snapshotProvider() {
            return new io.torana.core.ReflectionSnapshotProvider();
        }

        @Bean
        public AuditedActionAspect auditedActionAspect(
                AuditPipeline auditPipeline,
                io.torana.core.ReflectionSnapshotProvider snapshotProvider) {
            return new AuditedActionAspect(auditPipeline, snapshotProvider);
        }

        @Bean
        public OrderService orderService() {
            return new OrderService();
        }
    }
}
