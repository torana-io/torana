package io.torana.core;

import io.torana.api.AuditQuery;
import io.torana.api.AuditRecord;
import io.torana.api.AuditScope;
import io.torana.api.AuditTrail;
import io.torana.api.model.AuditAction;
import io.torana.api.model.Target;
import io.torana.spi.SnapshotProvider;

import java.util.function.Supplier;

/** Default implementation of the AuditTrail programmatic API. */
public class DefaultAuditTrail implements AuditTrail {

    private final AuditPipeline pipeline;
    private final SnapshotProvider snapshotProvider;
    private final Supplier<AuditQuery> querySupplier;

    public DefaultAuditTrail(AuditPipeline pipeline) {
        this(
                pipeline,
                null,
                () -> {
                    throw new UnsupportedOperationException("Query not configured");
                });
    }

    public DefaultAuditTrail(
            AuditPipeline pipeline,
            SnapshotProvider snapshotProvider,
            Supplier<AuditQuery> querySupplier) {
        this.pipeline = pipeline;
        this.snapshotProvider = snapshotProvider;
        this.querySupplier = querySupplier;
    }

    @Override
    public void record(AuditRecord record) {
        AuditContext context = new AuditContext();
        context.markStarted();

        // Set action
        context.setAction(AuditAction.of(record.getAction()));

        // Set target if provided
        if (record.hasTarget()) {
            context.setTarget(
                    Target.of(
                            record.getTargetType(),
                            record.getTargetId(),
                            record.getTargetDisplayName()));
        }

        // Set outcome
        context.setOutcome(record.getOutcome());

        // Set metadata
        context.addAllMetadata(record.getMetadata());

        // Set error message if present
        if (record.getErrorMessage() != null) {
            context.setErrorMessage(record.getErrorMessage());
        }

        // Note: Changes from AuditRecord would need to be handled differently
        // since they're already computed. For now, we don't set before/after snapshots.

        // Process through pipeline
        pipeline.process(context);
    }

    @Override
    public AuditScope beginScope(String action) {
        return new DefaultAuditScope(action, pipeline, snapshotProvider);
    }

    @Override
    public AuditQuery query() {
        return querySupplier.get();
    }
}
