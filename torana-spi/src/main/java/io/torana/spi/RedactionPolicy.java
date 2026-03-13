package io.torana.spi;

import io.torana.api.model.AuditEntry;

/**
 * SPI for redacting sensitive data before persistence.
 *
 * <p>Implementations apply redaction rules to audit entries, masking or removing sensitive fields
 * like passwords, tokens, or personal information.
 *
 * <p>Example implementation:
 *
 * <pre>{@code
 * public class PatternRedactionPolicy implements RedactionPolicy {
 *     private final Set<String> sensitiveFields = Set.of("password", "token", "secret");
 *
 *     @Override
 *     public AuditEntry apply(AuditEntry entry) {
 *         // Redact metadata and changes
 *         return new AuditEntry(
 *             entry.id(), entry.action(), entry.occurredAt(), entry.outcome(),
 *             entry.actor(), entry.tenant(), entry.target(),
 *             entry.requestContext(), entry.traceContext(),
 *             redactMap(entry.metadata()),
 *             redactChanges(entry.changes()),
 *             entry.errorMessage(), entry.schemaVersion()
 *         );
 *     }
 *
 *     @Override
 *     public boolean shouldRedact(String fieldPath) {
 *         String lastSegment = fieldPath.substring(fieldPath.lastIndexOf('.') + 1);
 *         return sensitiveFields.contains(lastSegment.toLowerCase());
 *     }
 * }
 * }</pre>
 */
public interface RedactionPolicy {

    /**
     * Applies redaction rules to an audit entry.
     *
     * <p>Returns a new entry with sensitive data redacted. The original entry is not modified.
     *
     * @param entry the audit entry to redact
     * @return a new entry with sensitive data redacted
     */
    AuditEntry apply(AuditEntry entry);

    /**
     * Redacts a single value by field path.
     *
     * <p>If the field should be redacted, returns the redacted value (e.g., "[REDACTED]").
     * Otherwise, returns the original value.
     *
     * @param fieldPath the path of the field (e.g., "user.password")
     * @param value the original value
     * @return the redacted value or original if not sensitive
     */
    Object redactValue(String fieldPath, Object value);

    /**
     * Checks if a field path should be redacted.
     *
     * @param fieldPath the path of the field
     * @return true if the field should be redacted
     */
    boolean shouldRedact(String fieldPath);
}
