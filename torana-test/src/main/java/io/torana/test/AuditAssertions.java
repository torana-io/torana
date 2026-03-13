package io.torana.test;

import io.torana.api.model.AuditEntry;
import io.torana.api.model.AuditOutcome;

import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.Assertions;

import java.time.Instant;

/**
 * AssertJ-style assertions for audit entries and writers.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * import static io.torana.test.AuditAssertions.*;
 *
 * InMemoryAuditWriter writer = new InMemoryAuditWriter();
 * // ... run code
 *
 * assertThat(writer)
 *     .hasRecorded(1)
 *     .hasRecordedAction("order.cancelled");
 *
 * assertThat(writer.getLastEntry().orElseThrow())
 *     .hasAction("order.cancelled")
 *     .hasTarget("Order", "order-123")
 *     .succeeded();
 * }</pre>
 */
public class AuditAssertions {

    /** Creates assertions for an audit entry. */
    public static AuditEntryAssert assertThat(AuditEntry entry) {
        return new AuditEntryAssert(entry);
    }

    /** Creates assertions for an in-memory writer. */
    public static InMemoryWriterAssert assertThat(InMemoryAuditWriter writer) {
        return new InMemoryWriterAssert(writer);
    }

    /** Assertions for AuditEntry. */
    public static class AuditEntryAssert extends AbstractAssert<AuditEntryAssert, AuditEntry> {

        protected AuditEntryAssert(AuditEntry actual) {
            super(actual, AuditEntryAssert.class);
        }

        /** Verifies the action name. */
        public AuditEntryAssert hasAction(String expected) {
            isNotNull();
            Assertions.assertThat(actual.action().name()).as("action").isEqualTo(expected);
            return this;
        }

        /** Verifies the action starts with a prefix. */
        public AuditEntryAssert hasActionStartingWith(String prefix) {
            isNotNull();
            Assertions.assertThat(actual.action().name()).as("action").startsWith(prefix);
            return this;
        }

        /** Verifies the outcome. */
        public AuditEntryAssert hasOutcome(AuditOutcome expected) {
            isNotNull();
            Assertions.assertThat(actual.outcome()).as("outcome").isEqualTo(expected);
            return this;
        }

        /** Verifies the entry succeeded. */
        public AuditEntryAssert succeeded() {
            return hasOutcome(AuditOutcome.SUCCESS);
        }

        /** Verifies the entry failed. */
        public AuditEntryAssert failed() {
            return hasOutcome(AuditOutcome.FAILURE);
        }

        /** Verifies the actor ID. */
        public AuditEntryAssert hasActorId(String expected) {
            isNotNull();
            Assertions.assertThat(actual.actor()).as("actor").isNotNull();
            Assertions.assertThat(actual.actor().id()).as("actor.id").isEqualTo(expected);
            return this;
        }

        /** Verifies the tenant ID. */
        public AuditEntryAssert hasTenantId(String expected) {
            isNotNull();
            Assertions.assertThat(actual.tenant()).as("tenant").isNotNull();
            Assertions.assertThat(actual.tenant().id()).as("tenant.id").isEqualTo(expected);
            return this;
        }

        /** Verifies the target. */
        public AuditEntryAssert hasTarget(String type, String id) {
            isNotNull();
            Assertions.assertThat(actual.target()).as("target").isNotNull();
            Assertions.assertThat(actual.target().type()).as("target.type").isEqualTo(type);
            Assertions.assertThat(actual.target().id()).as("target.id").isEqualTo(id);
            return this;
        }

        /** Verifies a metadata entry exists. */
        public AuditEntryAssert hasMetadata(String key, Object value) {
            isNotNull();
            Assertions.assertThat(actual.metadata()).as("metadata").containsEntry(key, value);
            return this;
        }

        /** Verifies the entry has changes. */
        public AuditEntryAssert hasChanges() {
            isNotNull();
            Assertions.assertThat(actual.hasChanges()).as("hasChanges").isTrue();
            return this;
        }

        /** Verifies the entry has no changes. */
        public AuditEntryAssert hasNoChanges() {
            isNotNull();
            Assertions.assertThat(actual.hasChanges()).as("hasChanges").isFalse();
            return this;
        }

        /** Verifies the error message. */
        public AuditEntryAssert hasErrorMessage(String expected) {
            isNotNull();
            Assertions.assertThat(actual.errorMessage()).as("errorMessage").isEqualTo(expected);
            return this;
        }

        /** Verifies the entry has an error message containing text. */
        public AuditEntryAssert hasErrorMessageContaining(String text) {
            isNotNull();
            Assertions.assertThat(actual.errorMessage()).as("errorMessage").contains(text);
            return this;
        }

        /** Verifies the occurredAt is after the given instant. */
        public AuditEntryAssert occurredAfter(Instant instant) {
            isNotNull();
            Assertions.assertThat(actual.occurredAt()).as("occurredAt").isAfter(instant);
            return this;
        }

        /** Verifies the occurredAt is before the given instant. */
        public AuditEntryAssert occurredBefore(Instant instant) {
            isNotNull();
            Assertions.assertThat(actual.occurredAt()).as("occurredAt").isBefore(instant);
            return this;
        }
    }

    /** Assertions for InMemoryAuditWriter. */
    public static class InMemoryWriterAssert
            extends AbstractAssert<InMemoryWriterAssert, InMemoryAuditWriter> {

        protected InMemoryWriterAssert(InMemoryAuditWriter actual) {
            super(actual, InMemoryWriterAssert.class);
        }

        /** Verifies the number of recorded entries. */
        public InMemoryWriterAssert hasRecorded(int count) {
            isNotNull();
            Assertions.assertThat(actual.size()).as("recorded count").isEqualTo(count);
            return this;
        }

        /** Verifies at least one entry with the given action was recorded. */
        public InMemoryWriterAssert hasRecordedAction(String action) {
            isNotNull();
            Assertions.assertThat(actual.findByAction(action))
                    .as("recorded action: " + action)
                    .isPresent();
            return this;
        }

        /** Verifies no entries were recorded. */
        public InMemoryWriterAssert isEmpty() {
            isNotNull();
            Assertions.assertThat(actual.isEmpty()).as("isEmpty").isTrue();
            return this;
        }

        /** Verifies entries were recorded. */
        public InMemoryWriterAssert isNotEmpty() {
            isNotNull();
            Assertions.assertThat(actual.isEmpty()).as("isEmpty").isFalse();
            return this;
        }

        /** Returns assertions for the first entry. */
        public AuditEntryAssert firstEntry() {
            isNotNull();
            Assertions.assertThat(actual.getEntries()).as("entries").isNotEmpty();
            return new AuditEntryAssert(actual.getEntries().get(0));
        }

        /** Returns assertions for the last entry. */
        public AuditEntryAssert lastEntry() {
            isNotNull();
            Assertions.assertThat(actual.getLastEntry()).as("lastEntry").isPresent();
            return new AuditEntryAssert(actual.getLastEntry().get());
        }
    }
}
