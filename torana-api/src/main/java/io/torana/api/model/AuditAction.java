package io.torana.api.model;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents a business action name following dotted notation.
 *
 * <p>Action names must be lowercase, using dots as separators. Examples: "order.cancelled",
 * "invoice.approved", "user.role.assigned"
 *
 * <p>This is an immutable value object.
 */
public record AuditAction(String name) {

    private static final Pattern VALID_ACTION_PATTERN =
            Pattern.compile("^[a-z][a-z0-9]*(\\.[a-z][a-z0-9]*)*$");

    public AuditAction {
        Objects.requireNonNull(name, "Action name must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Action name must not be blank");
        }
        if (!VALID_ACTION_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "Action name must be lowercase with dot separators (e.g., 'order.cancelled'): "
                            + name);
        }
    }

    /**
     * Creates an AuditAction from a string name.
     *
     * @param name the action name
     * @return a new AuditAction
     * @throws NullPointerException if name is null
     * @throws IllegalArgumentException if name is invalid
     */
    public static AuditAction of(String name) {
        return new AuditAction(name);
    }

    /**
     * Returns the category (first segment) of this action. For "order.cancelled", returns "order".
     *
     * @return the category
     */
    public String category() {
        int dotIndex = name.indexOf('.');
        return dotIndex > 0 ? name.substring(0, dotIndex) : name;
    }

    /**
     * Checks if this action starts with the given prefix.
     *
     * @param prefix the prefix to check
     * @return true if this action starts with the prefix
     */
    public boolean startsWith(String prefix) {
        return name.startsWith(prefix);
    }

    @Override
    public String toString() {
        return name;
    }
}
