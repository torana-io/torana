package io.torana.core.redaction;

import static org.assertj.core.api.Assertions.assertThat;

import io.torana.api.model.Actor;
import io.torana.api.model.AuditAction;
import io.torana.api.model.AuditEntry;
import io.torana.api.model.AuditOutcome;
import io.torana.api.model.ChangeSet;
import io.torana.api.model.FieldChange;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

class DefaultRedactionPolicyTest {

    private DefaultRedactionPolicy policy;

    @BeforeEach
    void setUp() {
        policy = DefaultRedactionPolicy.withDefaults();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "password",
                "Password",
                "PASSWORD",
                "secret",
                "token",
                "apiKey",
                "apikey",
                "creditCard",
                "ssn",
                "cvv"
            })
    void shouldRedactSensitiveFields(String field) {
        assertThat(policy.shouldRedact(field)).isTrue();
        assertThat(policy.shouldRedact("user." + field)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "email", "status", "count", "createdAt"})
    void shouldNotRedactNormalFields(String field) {
        assertThat(policy.shouldRedact(field)).isFalse();
    }

    @Test
    void shouldRedactValueForSensitiveField() {
        Object result = policy.redactValue("user.password", "secret123");
        assertThat(result).isEqualTo("[REDACTED]");
    }

    @Test
    void shouldNotRedactValueForNormalField() {
        Object result = policy.redactValue("user.name", "Alice");
        assertThat(result).isEqualTo("Alice");
    }

    @Test
    void shouldRedactMetadataInEntry() {
        AuditEntry entry =
                createEntryWithMetadata(
                        Map.of(
                                "password", "secret123",
                                "username", "alice"));

        AuditEntry redacted = policy.apply(entry);

        assertThat(redacted.metadata().get("password")).isEqualTo("[REDACTED]");
        assertThat(redacted.metadata().get("username")).isEqualTo("alice");
    }

    @Test
    void shouldRedactChangesInEntry() {
        ChangeSet changes =
                ChangeSet.of(
                        FieldChange.modified("password", "old", "new"),
                        FieldChange.modified("status", "PENDING", "ACTIVE"));

        AuditEntry entry = createEntryWithChanges(changes);
        AuditEntry redacted = policy.apply(entry);

        FieldChange passwordChange =
                redacted.changes().changes().stream()
                        .filter(c -> c.path().equals("password"))
                        .findFirst()
                        .orElseThrow();

        assertThat(passwordChange.previousValue()).isEqualTo("[REDACTED]");
        assertThat(passwordChange.newValue()).isEqualTo("[REDACTED]");

        FieldChange statusChange =
                redacted.changes().changes().stream()
                        .filter(c -> c.path().equals("status"))
                        .findFirst()
                        .orElseThrow();

        assertThat(statusChange.previousValue()).isEqualTo("PENDING");
        assertThat(statusChange.newValue()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldMatchPatterns() {
        DefaultRedactionPolicy patternPolicy =
                new DefaultRedactionPolicy(Set.of(), List.of(".*[Cc]onfidential.*"));

        assertThat(patternPolicy.shouldRedact("confidentialData")).isTrue();
        assertThat(patternPolicy.shouldRedact("user.confidentialInfo")).isTrue();
        assertThat(patternPolicy.shouldRedact("normalField")).isFalse();
    }

    @Test
    void shouldPreserveOtherFieldsInEntry() {
        AuditEntry entry = createEntryWithMetadata(Map.of("password", "secret"));
        AuditEntry redacted = policy.apply(entry);

        // Verify other fields are preserved
        assertThat(redacted.id()).isEqualTo(entry.id());
        assertThat(redacted.action()).isEqualTo(entry.action());
        assertThat(redacted.occurredAt()).isEqualTo(entry.occurredAt());
        assertThat(redacted.outcome()).isEqualTo(entry.outcome());
        assertThat(redacted.actor()).isEqualTo(entry.actor());
    }

    @Test
    void shouldHandleNullAndEmptyGracefully() {
        DefaultRedactionPolicy emptyPolicy = DefaultRedactionPolicy.none();

        assertThat(emptyPolicy.shouldRedact(null)).isFalse();
        assertThat(emptyPolicy.shouldRedact("")).isFalse();
    }

    private AuditEntry createEntryWithMetadata(Map<String, Object> metadata) {
        return new AuditEntry(
                UUID.randomUUID(),
                AuditAction.of("test.action"),
                Instant.now(),
                AuditOutcome.SUCCESS,
                Actor.system("test"),
                null,
                null,
                null,
                null,
                metadata,
                ChangeSet.empty(),
                null,
                1);
    }

    private AuditEntry createEntryWithChanges(ChangeSet changes) {
        return new AuditEntry(
                UUID.randomUUID(),
                AuditAction.of("test.action"),
                Instant.now(),
                AuditOutcome.SUCCESS,
                Actor.system("test"),
                null,
                null,
                null,
                null,
                Map.of(),
                changes,
                null,
                1);
    }
}
