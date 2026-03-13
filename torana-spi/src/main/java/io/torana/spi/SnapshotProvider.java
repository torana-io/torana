package io.torana.spi;

import java.util.Map;

/**
 * SPI for capturing object state snapshots for change tracking.
 *
 * <p>Implementations convert domain objects into flat maps of property paths to values, which can
 * then be compared using a {@link DiffEngine}.
 *
 * <p>Example implementation using reflection:
 *
 * <pre>{@code
 * public class ReflectionSnapshotProvider implements SnapshotProvider {
 *     @Override
 *     public Map<String, Object> capture(Object source) {
 *         Map<String, Object> snapshot = new HashMap<>();
 *         for (Field field : source.getClass().getDeclaredFields()) {
 *             field.setAccessible(true);
 *             snapshot.put(field.getName(), field.get(source));
 *         }
 *         return snapshot;
 *     }
 *
 *     @Override
 *     public boolean supports(Class<?> type) {
 *         return true; // supports all types
 *     }
 * }
 * }</pre>
 */
public interface SnapshotProvider {

    /**
     * Captures a snapshot of the given object's state.
     *
     * <p>The snapshot is represented as a map of property paths (e.g., "status", "address.city") to
     * their values.
     *
     * @param source the object to snapshot
     * @return a map of property paths to values
     */
    Map<String, Object> capture(Object source);

    /**
     * Checks if this provider supports the given type.
     *
     * @param type the class to check
     * @return true if this provider can snapshot instances of the type
     */
    boolean supports(Class<?> type);
}
