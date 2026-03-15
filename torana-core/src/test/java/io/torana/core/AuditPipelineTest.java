package io.torana.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.torana.api.model.Actor;
import io.torana.api.model.AuditAction;
import io.torana.api.model.AuditEntry;
import io.torana.api.model.AuditOutcome;
import io.torana.api.model.RequestContext;
import io.torana.api.model.Tenant;
import io.torana.api.model.TraceContext;
import io.torana.core.diff.DefaultDiffEngine;
import io.torana.core.redaction.DefaultRedactionPolicy;
import io.torana.spi.AuditWriter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

class AuditPipelineTest {

    private AuditWriter mockWriter;
    private AuditPipeline pipeline;
    private ArgumentCaptor<AuditEntry> entryCaptor;

    @BeforeEach
    void setUp() {
        mockWriter = mock(AuditWriter.class);
        entryCaptor = ArgumentCaptor.forClass(AuditEntry.class);

        Actor testActor = Actor.user("test-user", "Test User");
        Tenant testTenant = Tenant.of("test-tenant");
        RequestContext testRequest = RequestContext.of("POST", "/api/test");
        TraceContext testTrace = TraceContext.of("trace-123", "span-456");

        ContextCollector collector =
                new ContextCollector(
                        List.of(() -> Optional.of(testActor)),
                        List.of(() -> Optional.of(testTenant)),
                        List.of(() -> Optional.of(testRequest)),
                        List.of(() -> Optional.of(testTrace)));

        AuditEntryFactory factory = new AuditEntryFactory(new DefaultDiffEngine());
        DefaultRedactionPolicy redaction = DefaultRedactionPolicy.withDefaults();
        TransactionAwareWriter txWriter = new DefaultTransactionAwareWriter(mockWriter);

        pipeline = new AuditPipeline(collector, factory, redaction, txWriter);
    }

    @Test
    void shouldProcessContextAndWriteEntry() {
        AuditContext context = new AuditContext();
        context.setAction(AuditAction.of("order.created"));
        context.markStarted();

        pipeline.process(context);

        verify(mockWriter).write(entryCaptor.capture());
        AuditEntry entry = entryCaptor.getValue();

        assertThat(entry.action().name()).isEqualTo("order.created");
        assertThat(entry.outcome()).isEqualTo(AuditOutcome.SUCCESS);
        assertThat(entry.actor().id()).isEqualTo("test-user");
        assertThat(entry.tenant().id()).isEqualTo("test-tenant");
        assertThat(entry.requestContext().method()).isEqualTo("POST");
        assertThat(entry.traceContext().traceId()).isEqualTo("trace-123");
    }

    @Test
    void shouldCollectContextFromResolvers() {
        AuditContext context = new AuditContext();
        context.setAction(AuditAction.of("test.action"));
        context.markStarted();

        pipeline.process(context);

        verify(mockWriter).write(entryCaptor.capture());
        AuditEntry entry = entryCaptor.getValue();

        assertThat(entry.actor()).isNotNull();
        assertThat(entry.tenant()).isNotNull();
        assertThat(entry.requestContext()).isNotNull();
        assertThat(entry.traceContext()).isNotNull();
    }

    @Test
    void shouldRedactSensitiveMetadata() {
        AuditContext context = new AuditContext();
        context.setAction(AuditAction.of("user.login"));
        context.addMetadata("password", "secret123");
        context.addMetadata("username", "alice");
        context.markStarted();

        pipeline.process(context);

        verify(mockWriter).write(entryCaptor.capture());
        AuditEntry entry = entryCaptor.getValue();

        assertThat(entry.metadata().get("password")).isEqualTo("[REDACTED]");
        assertThat(entry.metadata().get("username")).isEqualTo("alice");
    }

    @Test
    void shouldCaptureFailureOutcome() {
        AuditContext context = new AuditContext();
        context.setAction(AuditAction.of("order.create"));
        context.setOutcome(AuditOutcome.FAILURE);
        context.setErrorMessage("Validation failed");
        context.markStarted();

        pipeline.process(context);

        verify(mockWriter).write(entryCaptor.capture());
        AuditEntry entry = entryCaptor.getValue();

        assertThat(entry.outcome()).isEqualTo(AuditOutcome.FAILURE);
        assertThat(entry.errorMessage()).isEqualTo("Validation failed");
    }

    @Test
    void shouldComputeChangesFromSnapshots() {
        AuditContext context = new AuditContext();
        context.setAction(AuditAction.of("order.updated"));
        context.setBeforeSnapshot(Map.of("status", "PENDING"));
        context.setAfterSnapshot(Map.of("status", "COMPLETED"));
        context.markStarted();

        pipeline.process(context);

        verify(mockWriter).write(entryCaptor.capture());
        AuditEntry entry = entryCaptor.getValue();

        assertThat(entry.hasChanges()).isTrue();
        assertThat(entry.changes().size()).isEqualTo(1);
        assertThat(entry.changes().changes().get(0).path()).isEqualTo("status");
    }

    @Test
    void shouldPreserveExplicitlySetContext() {
        Actor explicitActor = Actor.user("explicit-user", "Explicit User");

        AuditContext context = new AuditContext();
        context.setAction(AuditAction.of("test.action"));
        context.setActor(explicitActor);
        context.markStarted();

        pipeline.process(context);

        verify(mockWriter).write(entryCaptor.capture());
        AuditEntry entry = entryCaptor.getValue();

        assertThat(entry.actor().id()).isEqualTo("explicit-user");
    }

    @Test
    void shouldMarkCompletedTime() {
        AuditContext context = new AuditContext();
        context.setAction(AuditAction.of("test.action"));
        context.markStarted();

        assertThat(context.getCompletedAt()).isNull();

        pipeline.process(context);

        assertThat(context.getCompletedAt()).isNotNull();
    }
}
