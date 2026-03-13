package io.torana.core;

import io.torana.api.AuditScope;
import io.torana.api.model.AuditAction;
import io.torana.api.model.AuditOutcome;
import io.torana.api.model.Target;
import io.torana.spi.SnapshotProvider;

/** Default implementation of scoped auditing. */
public class DefaultAuditScope implements AuditScope {

    private final AuditContext context;
    private final AuditPipeline pipeline;
    private final SnapshotProvider snapshotProvider;
    private boolean closed = false;

    public DefaultAuditScope(
            String action, AuditPipeline pipeline, SnapshotProvider snapshotProvider) {
        this.context = new AuditContext();
        this.context.setAction(AuditAction.of(action));
        this.context.markStarted();
        this.pipeline = pipeline;
        this.snapshotProvider = snapshotProvider;
    }

    @Override
    public AuditScope target(String type, String id) {
        context.setTarget(Target.of(type, id));
        return this;
    }

    @Override
    public AuditScope target(String type, String id, String displayName) {
        context.setTarget(Target.of(type, id, displayName));
        return this;
    }

    @Override
    public AuditScope metadata(String key, Object value) {
        context.addMetadata(key, value);
        return this;
    }

    @Override
    public AuditScope beforeSnapshot(Object state) {
        if (snapshotProvider != null
                && state != null
                && snapshotProvider.supports(state.getClass())) {
            context.setBeforeSnapshot(snapshotProvider.capture(state));
        }
        return this;
    }

    @Override
    public AuditScope afterSnapshot(Object state) {
        if (snapshotProvider != null
                && state != null
                && snapshotProvider.supports(state.getClass())) {
            context.setAfterSnapshot(snapshotProvider.capture(state));
        }
        return this;
    }

    @Override
    public AuditScope outcome(AuditOutcome outcome) {
        context.setOutcome(outcome);
        return this;
    }

    @Override
    public AuditScope error(Throwable error) {
        context.setOutcome(AuditOutcome.FAILURE);
        context.setErrorMessage(error != null ? error.getMessage() : "Unknown error");
        return this;
    }

    @Override
    public AuditScope error(String errorMessage) {
        context.setOutcome(AuditOutcome.FAILURE);
        context.setErrorMessage(errorMessage);
        return this;
    }

    @Override
    public void close() {
        if (!closed) {
            closed = true;
            pipeline.process(context);
        }
    }
}
