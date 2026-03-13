package io.torana.core;

import io.torana.api.model.Actor;
import io.torana.api.model.AuditAction;
import io.torana.api.model.AuditOutcome;
import io.torana.api.model.RequestContext;
import io.torana.api.model.Target;
import io.torana.api.model.Tenant;
import io.torana.api.model.TraceContext;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Mutable context accumulated during the audit lifecycle.
 *
 * <p>This class holds all the information gathered during the execution of an audited action,
 * before it's converted to an immutable AuditEntry.
 */
public final class AuditContext {

    private final Map<String, Object> metadata = new HashMap<>();
    private AuditAction action;
    private Target target;
    private Actor actor;
    private Tenant tenant;
    private RequestContext requestContext;
    private TraceContext traceContext;
    private Map<String, Object> beforeSnapshot;
    private Map<String, Object> afterSnapshot;
    private AuditOutcome outcome = AuditOutcome.SUCCESS;
    private String errorMessage;
    private Instant startedAt;
    private Instant completedAt;

    public AuditContext() {}

    public AuditAction getAction() {
        return action;
    }

    // Action
    public void setAction(AuditAction action) {
        this.action = action;
    }

    public Target getTarget() {
        return target;
    }

    // Target
    public void setTarget(Target target) {
        this.target = target;
    }

    public Actor getActor() {
        return actor;
    }

    // Actor
    public void setActor(Actor actor) {
        this.actor = actor;
    }

    public Tenant getTenant() {
        return tenant;
    }

    // Tenant
    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    public RequestContext getRequestContext() {
        return requestContext;
    }

    // Request Context
    public void setRequestContext(RequestContext requestContext) {
        this.requestContext = requestContext;
    }

    public TraceContext getTraceContext() {
        return traceContext;
    }

    // Trace Context
    public void setTraceContext(TraceContext traceContext) {
        this.traceContext = traceContext;
    }

    // Metadata
    public void addMetadata(String key, Object value) {
        metadata.put(key, value);
    }

    public void addAllMetadata(Map<String, Object> additional) {
        metadata.putAll(additional);
    }

    public Map<String, Object> getMetadata() {
        return Map.copyOf(metadata);
    }

    public Map<String, Object> getBeforeSnapshot() {
        return beforeSnapshot;
    }

    // Snapshots
    public void setBeforeSnapshot(Map<String, Object> snapshot) {
        this.beforeSnapshot = snapshot != null ? Map.copyOf(snapshot) : null;
    }

    public Map<String, Object> getAfterSnapshot() {
        return afterSnapshot;
    }

    public void setAfterSnapshot(Map<String, Object> snapshot) {
        this.afterSnapshot = snapshot != null ? Map.copyOf(snapshot) : null;
    }

    public boolean hasSnapshots() {
        return beforeSnapshot != null && afterSnapshot != null;
    }

    public AuditOutcome getOutcome() {
        return outcome;
    }

    // Outcome
    public void setOutcome(AuditOutcome outcome) {
        this.outcome = outcome;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    // Error
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public void recordError(Throwable error) {
        this.outcome = AuditOutcome.FAILURE;
        this.errorMessage = error.getMessage();
    }

    // Timing
    public void markStarted() {
        this.startedAt = Instant.now();
    }

    public void markCompleted() {
        this.completedAt = Instant.now();
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getOccurredAt() {
        return completedAt != null ? completedAt : Instant.now();
    }
}
