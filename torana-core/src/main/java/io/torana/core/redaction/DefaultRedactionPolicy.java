package io.torana.core.redaction;

import io.torana.api.model.AuditEntry;
import io.torana.api.model.ChangeSet;
import io.torana.api.model.FieldChange;
import io.torana.spi.RedactionPolicy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Default redaction policy based on field name patterns.
 *
 * <p>Redacts fields that match configured exact names or regex patterns. By default, redacts common
 * sensitive fields like password, secret, token, etc.
 */
public class DefaultRedactionPolicy implements RedactionPolicy {

    private static final String REDACTED = "[REDACTED]";

    private final Set<String> exactFields;
    private final List<Pattern> patterns;

    public DefaultRedactionPolicy(Set<String> exactFields, List<String> regexPatterns) {
        this.exactFields = Set.copyOf(exactFields);
        this.patterns = regexPatterns.stream().map(Pattern::compile).toList();
    }

    /** Creates a policy with sensible defaults for common sensitive fields. */
    public static DefaultRedactionPolicy withDefaults() {
        return new DefaultRedactionPolicy(
                Set.of(
                        "password",
                        "secret",
                        "token",
                        "apikey",
                        "apiKey",
                        "creditcard",
                        "creditCard",
                        "ssn",
                        "cvv",
                        "pin"),
                List.of(
                        ".*[Pp]assword.*",
                        ".*[Ss]ecret.*",
                        ".*[Tt]oken.*",
                        ".*[Cc]redential.*",
                        ".*[Aa]uth.*[Kk]ey.*"));
    }

    /** Creates an empty policy that doesn't redact anything. */
    public static DefaultRedactionPolicy none() {
        return new DefaultRedactionPolicy(Set.of(), List.of());
    }

    @Override
    public AuditEntry apply(AuditEntry entry) {
        Map<String, Object> redactedMetadata = redactMap(entry.metadata(), "metadata");
        ChangeSet redactedChanges = redactChangeSet(entry.changes());

        return new AuditEntry(
                entry.id(),
                entry.action(),
                entry.occurredAt(),
                entry.outcome(),
                entry.actor(),
                entry.tenant(),
                entry.target(),
                entry.requestContext(),
                entry.traceContext(),
                redactedMetadata,
                redactedChanges,
                entry.errorMessage(),
                entry.schemaVersion());
    }

    @Override
    public Object redactValue(String fieldPath, Object value) {
        if (shouldRedact(fieldPath)) {
            return REDACTED;
        }
        return value;
    }

    @Override
    public boolean shouldRedact(String fieldPath) {
        if (fieldPath == null || fieldPath.isEmpty()) {
            return false;
        }

        // Extract the last segment (field name)
        String lastSegment =
                fieldPath.contains(".")
                        ? fieldPath.substring(fieldPath.lastIndexOf('.') + 1)
                        : fieldPath;

        // Check exact matches (case-insensitive)
        if (exactFields.stream().anyMatch(f -> f.equalsIgnoreCase(lastSegment))) {
            return true;
        }

        // Check regex patterns
        return patterns.stream().anyMatch(p -> p.matcher(fieldPath).matches());
    }

    private Map<String, Object> redactMap(Map<String, Object> map, String prefix) {
        if (map == null || map.isEmpty()) {
            return map;
        }

        Map<String, Object> result = new HashMap<>();
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            String path = prefix + "." + entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map<?, ?> nested) {
                @SuppressWarnings("unchecked")
                Map<String, Object> nestedMap = (Map<String, Object>) nested;
                result.put(entry.getKey(), redactMap(nestedMap, path));
            } else {
                result.put(entry.getKey(), redactValue(path, value));
            }
        }
        return result;
    }

    private ChangeSet redactChangeSet(ChangeSet changes) {
        if (changes == null || changes.isEmpty()) {
            return changes;
        }

        List<FieldChange> redactedChanges =
                changes.changes().stream()
                        .map(
                                fc ->
                                        new FieldChange(
                                                fc.path(),
                                                fc.type(),
                                                redactValue(fc.path(), fc.previousValue()),
                                                redactValue(fc.path(), fc.newValue())))
                        .toList();

        return ChangeSet.of(redactedChanges);
    }
}
