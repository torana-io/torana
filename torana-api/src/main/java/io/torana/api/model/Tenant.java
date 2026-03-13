package io.torana.api.model;

import java.util.Objects;

/**
 * Represents a multi-tenant identifier for audit entries.
 *
 * <p>In multi-tenant applications, this identifies which tenant the audited action belongs to.
 *
 * <p>This is an immutable value object.
 */
public record Tenant(String id, String name) {

    private static final Tenant DEFAULT = new Tenant("default", "Default");

    public Tenant {
        Objects.requireNonNull(id, "Tenant id must not be null");
    }

    /**
     * Creates a tenant with just an identifier.
     *
     * @param id the tenant identifier
     * @return a new Tenant
     */
    public static Tenant of(String id) {
        return new Tenant(id, null);
    }

    /**
     * Creates a tenant with an identifier and name.
     *
     * @param id the tenant identifier
     * @param name the tenant name
     * @return a new Tenant
     */
    public static Tenant of(String id, String name) {
        return new Tenant(id, name);
    }

    /**
     * Returns a default tenant for non-multi-tenant applications.
     *
     * @return the default tenant
     */
    public static Tenant defaultTenant() {
        return DEFAULT;
    }

    /**
     * Returns a copy of this tenant with a name.
     *
     * @param name the tenant name
     * @return a new Tenant with the name
     */
    public Tenant withName(String name) {
        return new Tenant(id, name);
    }
}
