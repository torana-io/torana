package io.torana.api.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChangeSetTest {

    @Test
    void shouldCreateEmptyChangeSet() {
        ChangeSet changes = ChangeSet.empty();

        assertThat(changes.isEmpty()).isTrue();
        assertThat(changes.size()).isZero();
    }

    @Test
    void shouldCreateChangeSetFromList() {
        ChangeSet changes =
                ChangeSet.of(
                        FieldChange.added("newField", "value"),
                        FieldChange.modified("status", "PENDING", "COMPLETED"),
                        FieldChange.removed("oldField", "oldValue"));

        assertThat(changes.size()).isEqualTo(3);
        assertThat(changes.isEmpty()).isFalse();
    }

    @Test
    void shouldFilterAdditions() {
        ChangeSet changes =
                ChangeSet.of(
                        FieldChange.added("field1", "value1"),
                        FieldChange.modified("field2", "old", "new"),
                        FieldChange.added("field3", "value3"));

        assertThat(changes.additions()).hasSize(2);
        assertThat(changes.additions()).allMatch(FieldChange::isAdded);
    }

    @Test
    void shouldFilterModifications() {
        ChangeSet changes =
                ChangeSet.of(
                        FieldChange.added("field1", "value1"),
                        FieldChange.modified("field2", "old", "new"),
                        FieldChange.modified("field3", "before", "after"));

        assertThat(changes.modifications()).hasSize(2);
        assertThat(changes.modifications()).allMatch(FieldChange::isModified);
    }

    @Test
    void shouldFilterRemovals() {
        ChangeSet changes =
                ChangeSet.of(
                        FieldChange.removed("field1", "value1"),
                        FieldChange.modified("field2", "old", "new"),
                        FieldChange.removed("field3", "value3"));

        assertThat(changes.removals()).hasSize(2);
        assertThat(changes.removals()).allMatch(FieldChange::isRemoved);
    }

    @Test
    void shouldBuildChangeSetWithBuilder() {
        ChangeSet changes =
                ChangeSet.builder()
                        .added("newField", "value")
                        .modified("status", "PENDING", "COMPLETED")
                        .removed("oldField", "oldValue")
                        .build();

        assertThat(changes.size()).isEqualTo(3);
    }

    @Test
    void shouldStreamChanges() {
        ChangeSet changes =
                ChangeSet.of(
                        FieldChange.added("field1", "value1"),
                        FieldChange.added("field2", "value2"));

        long count = changes.stream().count();
        assertThat(count).isEqualTo(2);
    }

    @Test
    void shouldMakeChangesListImmutable() {
        ChangeSet changes = ChangeSet.of(FieldChange.added("field", "value"));

        assertThat(changes.changes()).isUnmodifiable();
    }
}
