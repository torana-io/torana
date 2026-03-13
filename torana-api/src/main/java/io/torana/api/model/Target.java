package io.torana.api.model;

import java.util.Objects;

/**
 * Represents the primary business object affected by an audited action.
 *
 * <p>A target is identified by a type (e.g., "Order", "Invoice") and an ID. An optional display
 * name can provide human-readable context.
 *
 * <p>This is an immutable value object.
 */
public record Target(String type, String id, String displayName) {

    public Target {
        Objects.requireNonNull(type, "Target type must not be null");
        Objects.requireNonNull(id, "Target id must not be null");
    }

    /**
     * Creates a target without a display name.
     *
     * @param type the target type (e.g., "Order", "Invoice")
     * @param id the target identifier
     * @return a new Target
     */
    public static Target of(String type, String id) {
        return new Target(type, id, null);
    }

    /**
     * Creates a target with a display name.
     *
     * @param type the target type
     * @param id the target identifier
     * @param displayName the display name
     * @return a new Target
     */
    public static Target of(String type, String id, String displayName) {
        return new Target(type, id, displayName);
    }

    /**
     * Returns a composite reference in the format "Type:id".
     *
     * @return the composite reference
     */
    public String reference() {
        return type + ":" + id;
    }

    /**
     * Returns a copy of this target with a display name.
     *
     * @param displayName the display name
     * @return a new Target with the display name
     */
    public Target withDisplayName(String displayName) {
        return new Target(type, id, displayName);
    }
}
