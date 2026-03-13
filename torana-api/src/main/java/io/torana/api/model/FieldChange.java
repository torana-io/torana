package io.torana.api.model;

import java.util.Objects;

/**
 * Represents a single field change within a ChangeSet.
 *
 * <p>Each field change records the path of the changed field, the type of change, and the previous
 * and new values.
 *
 * <p>This is an immutable value object.
 */
public record FieldChange(String path, ChangeType type, Object previousValue, Object newValue) {

    public FieldChange {
        Objects.requireNonNull(path, "Field path must not be null");
        Objects.requireNonNull(type, "Change type must not be null");
    }

    /**
     * Creates an ADDED field change.
     *
     * @param path the field path
     * @param newValue the new value
     * @return a new FieldChange
     */
    public static FieldChange added(String path, Object newValue) {
        return new FieldChange(path, ChangeType.ADDED, null, newValue);
    }

    /**
     * Creates a MODIFIED field change.
     *
     * @param path the field path
     * @param previousValue the previous value
     * @param newValue the new value
     * @return a new FieldChange
     */
    public static FieldChange modified(String path, Object previousValue, Object newValue) {
        return new FieldChange(path, ChangeType.MODIFIED, previousValue, newValue);
    }

    /**
     * Creates a REMOVED field change.
     *
     * @param path the field path
     * @param previousValue the previous value
     * @return a new FieldChange
     */
    public static FieldChange removed(String path, Object previousValue) {
        return new FieldChange(path, ChangeType.REMOVED, previousValue, null);
    }

    /**
     * Checks if this is an addition.
     *
     * @return true if type is ADDED
     */
    public boolean isAdded() {
        return type == ChangeType.ADDED;
    }

    /**
     * Checks if this is a modification.
     *
     * @return true if type is MODIFIED
     */
    public boolean isModified() {
        return type == ChangeType.MODIFIED;
    }

    /**
     * Checks if this is a removal.
     *
     * @return true if type is REMOVED
     */
    public boolean isRemoved() {
        return type == ChangeType.REMOVED;
    }

    /** The type of change that occurred to a field. */
    public enum ChangeType {
        /** A new field was added. */
        ADDED,

        /** An existing field was modified. */
        MODIFIED,

        /** A field was removed. */
        REMOVED
    }
}
