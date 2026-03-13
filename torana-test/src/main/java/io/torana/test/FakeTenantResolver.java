package io.torana.test;

import io.torana.api.model.Tenant;
import io.torana.spi.TenantResolver;

import java.util.Optional;

/**
 * Fake tenant resolver for testing.
 *
 * <p>Allows setting a specific tenant to be returned, or no tenant.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * FakeTenantResolver resolver = new FakeTenantResolver();
 * resolver.setTenant(Tenant.of("acme", "ACME Corp"));
 *
 * // Later, for single-tenant testing:
 * resolver.clearTenant();
 * }</pre>
 */
public class FakeTenantResolver implements TenantResolver {

    private Tenant tenant;

    /** Creates a resolver with a default test tenant. */
    public FakeTenantResolver() {
        this.tenant = Tenant.of("test-tenant", "Test Tenant");
    }

    /**
     * Creates a resolver with the specified tenant.
     *
     * @param tenant the tenant to return
     */
    public FakeTenantResolver(Tenant tenant) {
        this.tenant = tenant;
    }

    @Override
    public Optional<Tenant> resolve() {
        return Optional.ofNullable(tenant);
    }

    /**
     * Sets the tenant to return.
     *
     * @param tenant the tenant
     */
    public void setTenant(Tenant tenant) {
        this.tenant = tenant;
    }

    /**
     * Sets a tenant with just an ID.
     *
     * @param id the tenant ID
     */
    public void setTenant(String id) {
        this.tenant = Tenant.of(id);
    }

    /**
     * Sets a tenant with ID and name.
     *
     * @param id the tenant ID
     * @param name the tenant name
     */
    public void setTenant(String id, String name) {
        this.tenant = Tenant.of(id, name);
    }

    /** Clears the tenant (resolve will return empty). */
    public void clearTenant() {
        this.tenant = null;
    }

    /** Sets the default tenant. */
    public void setDefaultTenant() {
        this.tenant = Tenant.defaultTenant();
    }
}
