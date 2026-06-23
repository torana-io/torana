package io.torana.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Companion annotation for {@link AuditedAction} to specify structured metadata.
 *
 * <p>This provides a type-safe alternative to the string-based metadata formats ({@link
 * AuditedAction#metadata()} and {@link AuditedAction#metadataFields()}). It offers better IDE
 * support, compile-time validation, and explicit key-value structure.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @AuditedAction(value = "order.placed", targetType = "Order", targetId = "#order.id")
 * @AuditMetadata({
 *     @MetadataField(key = "customerId", value = "#order.customerId"),
 *     @MetadataField(key = "items", value = "#order.items.size()"),
 *     @MetadataField(key = "total", value = "#order.total")
 * })
 * public void placeOrder(PlaceOrderCommand command) {
 *     // business logic
 * }
 * }</pre>
 *
 * <h2>Metadata Processing Priority</h2>
 *
 * <p>When multiple metadata sources are present, they are merged in this order (later sources
 * override earlier ones):
 *
 * <ol>
 *   <li>{@code @AuditMetadata} annotation
 *   <li>{@link AuditedAction#metadataFields()} array
 *   <li>{@link AuditedAction#metadata()} string (legacy)
 * </ol>
 *
 * <p>This allows for gradual migration from legacy formats and provides override capabilities.
 *
 * @see AuditedAction
 * @see MetadataField
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditMetadata {

    /**
     * Array of metadata field definitions.
     *
     * <p>Each {@link MetadataField} specifies a key and a SpEL expression to evaluate for the
     * value.
     *
     * @return the metadata fields
     */
    MetadataField[] value();
}
