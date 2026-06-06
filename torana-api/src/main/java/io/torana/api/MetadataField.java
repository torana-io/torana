package io.torana.api;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a single metadata field with a key and SpEL expression.
 *
 * <p>This annotation can only be used inside {@link AuditMetadata}. It provides a type-safe way to
 * define metadata key-value pairs with explicit structure and better IDE support.
 *
 * <p>The {@link #value()} expression is evaluated as a SpEL expression against method parameters,
 * allowing you to extract values from command objects, domain entities, or any method argument.
 *
 * <p>Example:
 *
 * <pre>{@code
 * @AuditedAction(value = "invoice.approved", targetType = "Invoice", targetId = "#invoiceId")
 * @AuditMetadata({
 *     @MetadataField(key = "approver", value = "#command.approverName"),
 *     @MetadataField(key = "amount", value = "#command.amount"),
 *     @MetadataField(key = "itemCount", value = "#invoice.lineItems.size()")
 * })
 * public void approveInvoice(String invoiceId, ApproveInvoiceCommand command) {
 *     // business logic
 * }
 * }</pre>
 *
 * @see AuditMetadata
 * @see AuditedAction
 */
@Target({}) // No target - only usable as nested annotation inside @AuditMetadata
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface MetadataField {

    /**
     * The metadata key name.
     *
     * <p>This is the key under which the evaluated value will be stored in the audit entry's
     * metadata map.
     *
     * <p>Unlike the legacy {@link AuditedAction#metadata()} format which restricts keys to {@code
     * [\w.]+} pattern, this attribute accepts any valid string, including hyphens and other special
     * characters.
     *
     * @return the metadata key
     */
    String key();

    /**
     * SpEL expression to evaluate for the value.
     *
     * <p>The expression is evaluated against method parameters using Spring's SpEL engine. You can
     * access:
     *
     * <ul>
     *   <li>Parameters by name: {@code #command}, {@code #orderId}, etc.
     *   <li>Parameters by position: {@code #arg0}, {@code #arg1}, or {@code #p0}, {@code #p1}
     *   <li>Properties and methods on parameters: {@code #command.reason}, {@code
     *       #order.items.size()}
     *   <li>Complex expressions: {@code #order.total > 1000 ? 'high' : 'normal'}
     * </ul>
     *
     * <p>If the expression evaluates to null or throws an exception, the field is silently excluded
     * from the metadata map. This allows for safe navigation and optional fields.
     *
     * @return the SpEL expression
     */
    String value();
}
