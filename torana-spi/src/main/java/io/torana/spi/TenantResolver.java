package io.torana.spi;

import io.torana.api.model.Tenant;

import java.util.Optional;

/**
 * SPI for resolving the current tenant in multi-tenant applications.
 *
 * <p>Implementations typically extract tenant information from request headers, URL paths, or
 * thread-local context.
 *
 * <p>Multiple resolvers can be registered. The first one that returns a non-empty result wins.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class HeaderTenantResolver implements TenantResolver {
 *     @Override
 *     public Optional<Tenant> resolve() {
 *         HttpServletRequest request = getCurrentRequest();
 *         String tenantId = request.getHeader("X-Tenant-ID");
 *         return Optional.ofNullable(tenantId).map(Tenant::of);
 *     }
 * }
 * }</pre>
 */
@FunctionalInterface
public interface TenantResolver {

    /**
     * Resolves the current tenant from the execution context.
     *
     * @return the resolved tenant, or empty if not available
     */
    Optional<Tenant> resolve();
}
