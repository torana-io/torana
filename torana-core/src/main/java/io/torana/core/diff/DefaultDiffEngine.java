package io.torana.core.diff;

import io.torana.api.model.ChangeSet;
import io.torana.api.model.FieldChange;
import io.torana.api.model.FieldChange.ChangeType;
import io.torana.spi.DiffEngine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Default implementation of diff computation.
 *
 * <p>Compares before and after snapshots to produce a list of changes. Supports nested maps up to a
 * configurable maximum depth.
 */
public class DefaultDiffEngine implements DiffEngine {

    private static final int DEFAULT_MAX_DEPTH = 5;
    private static final int DEFAULT_MAX_CHANGES = 100;

    private final int defaultMaxDepth;
    private final int maxChanges;

    public DefaultDiffEngine() {
        this(DEFAULT_MAX_DEPTH, DEFAULT_MAX_CHANGES);
    }

    public DefaultDiffEngine(int defaultMaxDepth, int maxChanges) {
        this.defaultMaxDepth = defaultMaxDepth;
        this.maxChanges = maxChanges;
    }

    @Override
    public ChangeSet diff(Map<String, Object> before, Map<String, Object> after) {
        return diff(before, after, defaultMaxDepth);
    }

    @Override
    public ChangeSet diff(Map<String, Object> before, Map<String, Object> after, int maxDepth) {
        if (before == null && after == null) {
            return ChangeSet.empty();
        }

        List<FieldChange> changes = new ArrayList<>();
        Set<String> allKeys = new HashSet<>();

        if (before != null) {
            allKeys.addAll(before.keySet());
        }
        if (after != null) {
            allKeys.addAll(after.keySet());
        }

        for (String key : allKeys) {
            if (changes.size() >= maxChanges) {
                break; // Limit the number of changes
            }
            Object beforeValue = before != null ? before.get(key) : null;
            Object afterValue = after != null ? after.get(key) : null;

            computeChanges(key, beforeValue, afterValue, changes, 0, maxDepth);
        }

        return ChangeSet.of(changes);
    }

    @SuppressWarnings("unchecked")
    private void computeChanges(
            String path,
            Object before,
            Object after,
            List<FieldChange> changes,
            int depth,
            int maxDepth) {

        if (changes.size() >= maxChanges) {
            return;
        }

        if (depth > maxDepth) {
            if (!Objects.equals(before, after)) {
                changes.add(new FieldChange(path, ChangeType.MODIFIED, before, after));
            }
            return;
        }

        if (before == null && after != null) {
            changes.add(FieldChange.added(path, after));
        } else if (before != null && after == null) {
            changes.add(FieldChange.removed(path, before));
        } else if (!Objects.equals(before, after)) {
            if (before instanceof Map<?, ?> beforeMap && after instanceof Map<?, ?> afterMap) {
                Map<String, Object> bm = (Map<String, Object>) beforeMap;
                Map<String, Object> am = (Map<String, Object>) afterMap;

                Set<String> keys = new HashSet<>();
                keys.addAll(bm.keySet());
                keys.addAll(am.keySet());

                for (String key : keys) {
                    if (changes.size() >= maxChanges) {
                        break;
                    }
                    computeChanges(
                            path + "." + key,
                            bm.get(key),
                            am.get(key),
                            changes,
                            depth + 1,
                            maxDepth);
                }
            } else {
                changes.add(FieldChange.modified(path, before, after));
            }
        }
    }
}
