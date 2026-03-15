package io.torana.core;

import io.torana.spi.SnapshotProvider;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Snapshot provider that uses reflection to capture object state.
 *
 * <p>Captures all non-static fields up to a configurable depth. Primitive types, strings, and
 * common wrapper types are captured directly. Collections are captured as lists. Maps are captured
 * as nested maps.
 */
public class ReflectionSnapshotProvider implements SnapshotProvider {

    private static final int DEFAULT_MAX_DEPTH = 3;

    private final int maxDepth;

    public ReflectionSnapshotProvider() {
        this(DEFAULT_MAX_DEPTH);
    }

    public ReflectionSnapshotProvider(int maxDepth) {
        this.maxDepth = maxDepth;
    }

    @Override
    public Map<String, Object> capture(Object source) {
        if (source == null) {
            return Map.of();
        }
        return captureObject(source, 0);
    }

    @Override
    public boolean supports(Class<?> type) {
        return type != null && !type.isPrimitive();
    }

    private Map<String, Object> captureObject(Object source, int depth) {
        if (source == null || depth > maxDepth) {
            return Map.of();
        }

        Map<String, Object> snapshot = new HashMap<>();
        Class<?> clazz = source.getClass();

        if (source instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                snapshot.put(key, captureValue(entry.getValue(), depth + 1));
            }
            return snapshot;
        }

        while (clazz != null && clazz != Object.class) {
            for (Field field : clazz.getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) {
                    continue;
                }
                if (Modifier.isTransient(field.getModifiers())) {
                    continue;
                }

                try {
                    field.setAccessible(true);
                    Object value = field.get(source);
                    snapshot.put(field.getName(), captureValue(value, depth + 1));
                } catch (IllegalAccessException | SecurityException e) {
                    // Skip fields we can't access
                }
            }
            clazz = clazz.getSuperclass();
        }

        return snapshot;
    }

    private Object captureValue(Object value, int depth) {
        if (value == null) {
            return null;
        }

        if (depth > maxDepth) {
            return value.toString();
        }

        if (isSimpleType(value.getClass())) {
            return value;
        }

        if (value instanceof Collection<?> collection) {
            return collection.stream().map(item -> captureValue(item, depth + 1)).toList();
        }

        if (value.getClass().isArray()) {
            Object[] array = (Object[]) value;
            return java.util.Arrays.stream(array)
                    .map(item -> captureValue(item, depth + 1))
                    .toList();
        }

        if (value instanceof Map<?, ?>) {
            return captureObject(value, depth);
        }

        return captureObject(value, depth);
    }

    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive()
                || type == String.class
                || type == Boolean.class
                || type == Character.class
                || Number.class.isAssignableFrom(type)
                || type.isEnum()
                || type == java.time.Instant.class
                || type == java.time.LocalDate.class
                || type == java.time.LocalDateTime.class
                || type == java.util.UUID.class;
    }
}
