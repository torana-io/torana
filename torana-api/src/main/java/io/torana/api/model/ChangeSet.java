package io.torana.api.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Represents structured changes captured during an audited action.
 *
 * <p>A ChangeSet contains a list of individual field changes, tracking what was added, modified, or
 * removed.
 *
 * <p>This is an immutable value object.
 */
public record ChangeSet(List<FieldChange> changes) {

    private static final ChangeSet EMPTY = new ChangeSet(List.of());

    public ChangeSet {
        changes = changes == null ? List.of() : List.copyOf(changes);
    }

    /**
     * Returns an empty change set.
     *
     * @return an empty ChangeSet
     */
    public static ChangeSet empty() {
        return EMPTY;
    }

    /**
     * Creates a change set from a list of changes.
     *
     * @param changes the list of field changes
     * @return a new ChangeSet
     */
    public static ChangeSet of(List<FieldChange> changes) {
        return new ChangeSet(changes);
    }

    /**
     * Creates a change set from varargs.
     *
     * @param changes the field changes
     * @return a new ChangeSet
     */
    public static ChangeSet of(FieldChange... changes) {
        return new ChangeSet(List.of(changes));
    }

    /**
     * Creates a change set builder.
     *
     * @return a new Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Checks if this change set is empty.
     *
     * @return true if there are no changes
     */
    public boolean isEmpty() {
        return changes.isEmpty();
    }

    /**
     * Returns the number of changes.
     *
     * @return the change count
     */
    public int size() {
        return changes.size();
    }

    /**
     * Returns a stream of the changes.
     *
     * @return a stream of FieldChange
     */
    public Stream<FieldChange> stream() {
        return changes.stream();
    }

    /**
     * Returns only the additions.
     *
     * @return a list of ADDED changes
     */
    public List<FieldChange> additions() {
        return changes.stream().filter(FieldChange::isAdded).toList();
    }

    /**
     * Returns only the modifications.
     *
     * @return a list of MODIFIED changes
     */
    public List<FieldChange> modifications() {
        return changes.stream().filter(FieldChange::isModified).toList();
    }

    /**
     * Returns only the removals.
     *
     * @return a list of REMOVED changes
     */
    public List<FieldChange> removals() {
        return changes.stream().filter(FieldChange::isRemoved).toList();
    }

    /** Builder for ChangeSet. */
    public static class Builder {
        private final List<FieldChange> changes = new ArrayList<>();

        public Builder add(FieldChange change) {
            changes.add(change);
            return this;
        }

        public Builder added(String path, Object newValue) {
            changes.add(FieldChange.added(path, newValue));
            return this;
        }

        public Builder modified(String path, Object previousValue, Object newValue) {
            changes.add(FieldChange.modified(path, previousValue, newValue));
            return this;
        }

        public Builder removed(String path, Object previousValue) {
            changes.add(FieldChange.removed(path, previousValue));
            return this;
        }

        public ChangeSet build() {
            return new ChangeSet(changes);
        }
    }
}
