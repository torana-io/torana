package io.torana.api.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

class AuditEntryTest {

    @Test
    void shouldBuildMinimalEntry() {
        AuditEntry entry = AuditEntry.builder().action("order.cancelled").build();

        assertThat(entry.id()).isNotNull();
        assertThat(entry.action().name()).isEqualTo("order.cancelled");
        assertThat(entry.occurredAt()).isNotNull();
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(entry.schemaVersion()).isEqualTo(AuditEntry.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void shouldBuildCompleteEntry() {
        UUID id = UUID.randomUUID();
        Instant now = Instant.now();
        Actor actor = Actor.user("alice", "Alice");
        Tenant tenant = Tenant.of("acme");
        Target target = Target.of("Order", "order-123");
        ChangeSet changes = ChangeSet.of(FieldChange.modified("status", "PENDING", "CANCELLED"));

        AuditEntry entry =
                AuditEntry.builder()
                        .id(id)
                        .action("order.cancelled")
                        .occurredAt(now)
                        .outcome(AuditOutcome.SUCCESS)
                        .actor(actor)
                        .tenant(tenant)
                        .target(target)
                        .requestContext(RequestContext.of("POST", "/api/orders/cancel"))
                        .traceContext(TraceContext.of("trace-123", "span-456"))
                        .metadata(Map.of("reason", "customer request"))
                        .changes(changes)
                        .build();

        assertThat(entry.id()).isEqualTo(id);
        assertThat(entry.action().name()).isEqualTo("order.cancelled");
        assertThat(entry.occurredAt()).isEqualTo(now);
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(entry.actor()).isEqualTo(actor);
        assertThat(entry.tenant()).isEqualTo(tenant);
        assertThat(entry.target()).isEqualTo(target);
        assertThat(entry.requestContext().method()).isEqualTo("POST");
        assertThat(entry.traceContext().traceId()).isEqualTo("trace-123");
        assertThat(entry.metadata()).containsEntry("reason", "customer request");
        assertThat(entry.changes()).isEqualTo(changes);
    }

    @Test
    void shouldBuildFailureEntry() {
        AuditEntry entry =
                AuditEntry.builder().action("order.cancelled").failure("Order not found").build();

        assertThat(entry.outcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(entry.errorMessage()).isEqualTo("Order not found");
        assertThat(entry.isFailure()).isTrue();
        assertThat(entry.isSuccess()).isFalse();
        assertThat(entry.hasError()).isTrue();
    }

    @Test
    void shouldBuildWithTargetTypeAndId() {
        AuditEntry entry =
                AuditEntry.builder().action("order.cancelled").target("Order", "order-123").build();

        assertThat(entry.target().type()).isEqualTo("Order");
        assertThat(entry.target().id()).isEqualTo("order-123");
    }

    @Test
    void shouldRejectNullId() {
        assertThatThrownBy(() -> AuditEntry.builder().id(null).action("order.cancelled").build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("id must not be null");
    }

    @Test
    void shouldRejectNullAction() {
        assertThatThrownBy(() -> AuditEntry.builder().build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("Action must not be null");
    }

    @Test
    void shouldDefaultContextsToEmpty() {
        AuditEntry entry = AuditEntry.builder().action("order.cancelled").build();

        assertThat(entry.requestContext()).isEqualTo(RequestContext.empty());
        assertThat(entry.traceContext()).isEqualTo(TraceContext.empty());
        assertThat(entry.metadata()).isEmpty();
        assertThat(entry.changes().isEmpty()).isTrue();
    }

    @Test
    void shouldMakeMetadataImmutable() {
        AuditEntry entry =
                AuditEntry.builder()
                        .action("order.cancelled")
                        .metadata(Map.of("key", "value"))
                        .build();

        assertThatThrownBy(() -> entry.metadata().put("new", "value"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldCheckHasChanges() {
        AuditEntry withChanges =
                AuditEntry.builder()
                        .action("order.cancelled")
                        .changes(
                                ChangeSet.of(
                                        FieldChange.modified("status", "PENDING", "CANCELLED")))
                        .build();

        AuditEntry withoutChanges = AuditEntry.builder().action("order.cancelled").build();

        assertThat(withChanges.hasChanges()).isTrue();
        assertThat(withoutChanges.hasChanges()).isFalse();
    }
}
