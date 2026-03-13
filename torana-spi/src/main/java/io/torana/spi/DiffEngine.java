package io.torana.spi;

import io.torana.api.model.ChangeSet;

import java.util.Map;

/**
 * SPI for computing diffs between before and after snapshots.
 *
 * <p>Implementations compare two state snapshots and produce a {@link ChangeSet} describing what
 * was added, modified, or removed.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class DefaultDiffEngine implements DiffEngine {
 *     @Override
 *     public ChangeSet diff(Map<String, Object> before, Map<String, Object> after) {
 *         return diff(before, after, 5); // default depth
 *     }
 *
 *     @Override
 *     public ChangeSet diff(Map<String, Object> before, Map<String, Object> after, int maxDepth) {
 *         List<FieldChange> changes = new ArrayList<>();
 *         // Compare keys and values...
 *         return ChangeSet.of(changes);
 *     }
 * }
 * }</pre>
 */
public interface DiffEngine {

    /**
     * Computes changes between before and after snapshots.
     *
     * <p>Uses a default maximum depth for nested object comparison.
     *
     * @param before the state before the action
     * @param after the state after the action
     * @return the computed changes
     */
    ChangeSet diff(Map<String, Object> before, Map<String, Object> after);

    /**
     * Computes changes between before and after snapshots with a depth limit.
     *
     * <p>The depth limit prevents infinite recursion and bounds the performance cost of diffing
     * deeply nested structures.
     *
     * @param before the state before the action
     * @param after the state after the action
     * @param maxDepth the maximum depth to traverse
     * @return the computed changes
     */
    ChangeSet diff(Map<String, Object> before, Map<String, Object> after, int maxDepth);
}
