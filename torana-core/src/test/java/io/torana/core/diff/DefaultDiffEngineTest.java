package io.torana.core.diff;

import static org.assertj.core.api.Assertions.assertThat;

import io.torana.api.model.ChangeSet;
import io.torana.api.model.FieldChange;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

class DefaultDiffEngineTest {

    private DefaultDiffEngine diffEngine;

    @BeforeEach
    void setUp() {
        diffEngine = new DefaultDiffEngine();
    }

    @Test
    void shouldReturnEmptyWhenBothNull() {
        ChangeSet changes = diffEngine.diff(null, null);
        assertThat(changes.isEmpty()).isTrue();
    }

    @Test
    void shouldDetectAddedFields() {
        Map<String, Object> before = Map.of();
        Map<String, Object> after = Map.of("status", "ACTIVE", "count", 5);

        ChangeSet changes = diffEngine.diff(before, after);

        assertThat(changes.size()).isEqualTo(2);
        assertThat(changes.additions()).hasSize(2);
    }

    @Test
    void shouldDetectRemovedFields() {
        Map<String, Object> before = Map.of("status", "ACTIVE", "count", 5);
        Map<String, Object> after = Map.of();

        ChangeSet changes = diffEngine.diff(before, after);

        assertThat(changes.size()).isEqualTo(2);
        assertThat(changes.removals()).hasSize(2);
    }

    @Test
    void shouldDetectModifiedFields() {
        Map<String, Object> before = Map.of("status", "PENDING", "count", 5);
        Map<String, Object> after = Map.of("status", "COMPLETED", "count", 10);

        ChangeSet changes = diffEngine.diff(before, after);

        assertThat(changes.size()).isEqualTo(2);
        assertThat(changes.modifications()).hasSize(2);
    }

    @Test
    void shouldIgnoreUnchangedFields() {
        Map<String, Object> before = Map.of("status", "ACTIVE", "count", 5);
        Map<String, Object> after = Map.of("status", "ACTIVE", "count", 5);

        ChangeSet changes = diffEngine.diff(before, after);

        assertThat(changes.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleMixedChanges() {
        Map<String, Object> before = Map.of("a", 1, "b", 2);
        Map<String, Object> after = Map.of("b", 3, "c", 4);

        ChangeSet changes = diffEngine.diff(before, after);

        assertThat(changes.size()).isEqualTo(3);
        assertThat(changes.removals()).extracting(FieldChange::path).containsExactly("a");
        assertThat(changes.modifications()).extracting(FieldChange::path).containsExactly("b");
        assertThat(changes.additions()).extracting(FieldChange::path).containsExactly("c");
    }

    @Test
    void shouldHandleNestedMaps() {
        Map<String, Object> before = Map.of("user", Map.of("name", "Alice", "age", 30));
        Map<String, Object> after = Map.of("user", Map.of("name", "Alice", "age", 31));

        ChangeSet changes = diffEngine.diff(before, after);

        assertThat(changes.size()).isEqualTo(1);
        assertThat(changes.changes().get(0).path()).isEqualTo("user.age");
        assertThat(changes.changes().get(0).previousValue()).isEqualTo(30);
        assertThat(changes.changes().get(0).newValue()).isEqualTo(31);
    }

    @Test
    void shouldRespectMaxDepth() {
        // Create deeply nested structure
        Map<String, Object> nested =
                Map.of(
                        "level1",
                        Map.of(
                                "level2",
                                Map.of(
                                        "level3",
                                        Map.of(
                                                "level4",
                                                Map.of(
                                                        "level5",
                                                        Map.of(
                                                                "level6",
                                                                Map.of("deep", "value")))))));

        Map<String, Object> before = Map.of("root", nested);
        Map<String, Object> after = Map.of("root", Map.of("level1", Map.of("changed", true)));

        // With default depth of 5, should still detect changes
        ChangeSet changes = diffEngine.diff(before, after);
        assertThat(changes.isEmpty()).isFalse();
    }

    @Test
    void shouldHandleNullValues() {
        Map<String, Object> before = new java.util.HashMap<>();
        before.put("nullable", null);
        before.put("value", "test");

        Map<String, Object> after = new java.util.HashMap<>();
        after.put("nullable", "now set");
        after.put("value", null);

        ChangeSet changes = diffEngine.diff(before, after);

        assertThat(changes.size()).isEqualTo(2);
    }

    @Test
    void shouldHandleNullBefore() {
        Map<String, Object> after = Map.of("status", "NEW");

        ChangeSet changes = diffEngine.diff(null, after);

        assertThat(changes.size()).isEqualTo(1);
        assertThat(changes.additions()).hasSize(1);
    }

    @Test
    void shouldHandleNullAfter() {
        Map<String, Object> before = Map.of("status", "OLD");

        ChangeSet changes = diffEngine.diff(before, null);

        assertThat(changes.size()).isEqualTo(1);
        assertThat(changes.removals()).hasSize(1);
    }
}
