package io.torana.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.torana.api.model.AuditOutcome;
import io.torana.api.model.ChangeSet;
import io.torana.api.model.FieldChange;

import org.junit.jupiter.api.Test;

import java.util.Map;

class AuditRecordTest {

    @Test
    void shouldBuildMinimalRecord() {
        AuditRecord record = AuditRecord.builder().action("order.cancelled").build();

        assertThat(record.getAction()).isEqualTo("order.cancelled");
        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    @Test
    void shouldBuildRecordWithTarget() {
        AuditRecord record =
                AuditRecord.builder()
                        .action("order.cancelled")
                        .target("Order", "order-123")
                        .build();

        assertThat(record.getTargetType()).isEqualTo("Order");
        assertThat(record.getTargetId()).isEqualTo("order-123");
        assertThat(record.hasTarget()).isTrue();
    }

    @Test
    void shouldBuildRecordWithTargetAndDisplayName() {
        AuditRecord record =
                AuditRecord.builder()
                        .action("order.cancelled")
                        .target("Order", "order-123", "Order #123")
                        .build();

        assertThat(record.getTargetDisplayName()).isEqualTo("Order #123");
    }

    @Test
    void shouldBuildRecordWithMetadata() {
        AuditRecord record =
                AuditRecord.builder()
                        .action("order.cancelled")
                        .metadata("reason", "customer request")
                        .metadata("priority", "high")
                        .build();

        assertThat(record.getMetadata())
                .containsEntry("reason", "customer request")
                .containsEntry("priority", "high");
    }

    @Test
    void shouldBuildRecordWithMetadataMap() {
        AuditRecord record =
                AuditRecord.builder()
                        .action("order.cancelled")
                        .metadata(Map.of("key1", "value1", "key2", "value2"))
                        .build();

        assertThat(record.getMetadata()).hasSize(2);
    }

    @Test
    void shouldBuildSuccessRecord() {
        AuditRecord record = AuditRecord.builder().action("order.cancelled").success().build();

        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.SUCCESS);
    }

    @Test
    void shouldBuildFailureRecord() {
        AuditRecord record = AuditRecord.builder().action("order.cancelled").failure().build();

        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
    }

    @Test
    void shouldBuildFailureRecordWithMessage() {
        AuditRecord record =
                AuditRecord.builder().action("order.cancelled").failure("Order not found").build();

        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(record.getErrorMessage()).isEqualTo("Order not found");
    }

    @Test
    void shouldBuildRecordWithError() {
        Exception error = new RuntimeException("Something went wrong");

        AuditRecord record = AuditRecord.builder().action("order.cancelled").error(error).build();

        assertThat(record.getOutcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(record.getErrorMessage()).isEqualTo("Something went wrong");
    }

    @Test
    void shouldBuildRecordWithChanges() {
        ChangeSet changes = ChangeSet.of(FieldChange.modified("status", "PENDING", "CANCELLED"));

        AuditRecord record =
                AuditRecord.builder().action("order.cancelled").changes(changes).build();

        assertThat(record.getChanges()).isEqualTo(changes);
        assertThat(record.hasChanges()).isTrue();
    }

    @Test
    void shouldConvertTargetIdFromObject() {
        AuditRecord record =
                AuditRecord.builder()
                        .action("order.cancelled")
                        .targetType("Order")
                        .targetId(12345L)
                        .build();

        assertThat(record.getTargetId()).isEqualTo("12345");
    }

    @Test
    void shouldRejectNullAction() {
        assertThatThrownBy(() -> AuditRecord.builder().build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Action must not be null");
    }

    @Test
    void shouldReportNoTargetWhenMissing() {
        AuditRecord record = AuditRecord.builder().action("order.cancelled").build();

        assertThat(record.hasTarget()).isFalse();
    }

    @Test
    void shouldReportNoChangesWhenEmpty() {
        AuditRecord record = AuditRecord.builder().action("order.cancelled").build();

        assertThat(record.hasChanges()).isFalse();
    }
}
