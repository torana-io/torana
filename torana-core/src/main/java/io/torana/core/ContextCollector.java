package io.torana.core;

import io.torana.spi.ActorResolver;
import io.torana.spi.RequestContextResolver;
import io.torana.spi.TenantResolver;
import io.torana.spi.TraceResolver;

import java.util.List;
import java.util.Objects;

/**
 * Collects context from all registered resolvers.
 *
 * <p>The context collector aggregates information from multiple resolver implementations, using the
 * first non-empty result from each category.
 */
public class ContextCollector {

    private final List<ActorResolver> actorResolvers;
    private final List<TenantResolver> tenantResolvers;
    private final List<RequestContextResolver> requestContextResolvers;
    private final List<TraceResolver> traceResolvers;

    public ContextCollector(
            List<ActorResolver> actorResolvers,
            List<TenantResolver> tenantResolvers,
            List<RequestContextResolver> requestContextResolvers,
            List<TraceResolver> traceResolvers) {
        this.actorResolvers = Objects.requireNonNullElse(actorResolvers, List.of());
        this.tenantResolvers = Objects.requireNonNullElse(tenantResolvers, List.of());
        this.requestContextResolvers =
                Objects.requireNonNullElse(requestContextResolvers, List.of());
        this.traceResolvers = Objects.requireNonNullElse(traceResolvers, List.of());
    }

    /**
     * Collects context from all resolvers and populates the audit context.
     *
     * @param context the audit context to populate
     */
    public void collect(AuditContext context) {
        collectActor(context);
        collectTenant(context);
        collectRequestContext(context);
        collectTraceContext(context);
    }

    private void collectActor(AuditContext context) {
        if (context.getActor() != null) {
            return; // Already set explicitly
        }
        actorResolvers.stream()
                .map(ActorResolver::resolve)
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .ifPresent(context::setActor);
    }

    private void collectTenant(AuditContext context) {
        if (context.getTenant() != null) {
            return;
        }
        tenantResolvers.stream()
                .map(TenantResolver::resolve)
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .ifPresent(context::setTenant);
    }

    private void collectRequestContext(AuditContext context) {
        if (context.getRequestContext() != null) {
            return;
        }
        requestContextResolvers.stream()
                .map(RequestContextResolver::resolve)
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .ifPresent(context::setRequestContext);
    }

    private void collectTraceContext(AuditContext context) {
        if (context.getTraceContext() != null) {
            return;
        }
        traceResolvers.stream()
                .map(TraceResolver::resolve)
                .flatMap(java.util.Optional::stream)
                .findFirst()
                .ifPresent(context::setTraceContext);
    }
}
