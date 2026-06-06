package io.torana.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Preset annotation for auditing entity deletion operations.
 *
 * <p>This is a convenience annotation that wraps {@link AuditedAction} with a predefined action
 * name of "entity.deleted". It reduces boilerplate for the common pattern of auditing delete
 * operations.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @AuditedDelete(
 *     targetType = "Order",
 *     targetId = "#orderId"
 * )
 * @Transactional
 * public void deleteOrder(String orderId) {
 *     orderRepository.deleteById(orderId);
 * }
 * }</pre>
 *
 * <p>This is equivalent to:
 *
 * <pre>{@code
 * @AuditedAction(
 *     value = "entity.deleted",
 *     targetType = "Order",
 *     targetId = "#orderId"
 * )
 * }</pre>
 *
 * <p><strong>Note:</strong> For delete operations, {@link #captureChanges()} is typically not
 * enabled because the "after" state is empty (entity deleted). If you need to capture the state
 * before deletion, you can enable it and provide the entity as the snapshot source.
 *
 * @see AuditedAction
 * @see AuditedCreate
 * @see AuditedUpdate
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditedDelete {

    /**
     * The type of entity being deleted (e.g., "Order", "Invoice", "Customer").
     *
     * <p>This is a required attribute that identifies the type of business object being deleted.
     *
     * @return the target entity type
     */
    String targetType();

    /**
     * SpEL expression for the entity ID.
     *
     * <p>The expression is evaluated against method parameters. Example: "#orderId" or "#entity.id"
     *
     * <p>For delete operations, you typically reference the ID directly (as a parameter) since the
     * entity may be deleted by the time the audit entry is created.
     *
     * @return the SpEL expression for target ID
     */
    String targetId();

    /**
     * SpEL expression for the display name (optional).
     *
     * <p>Optional human-readable name for the deleted entity. Example: "#order.orderNumber" or
     * "#entity.name"
     *
     * <p><strong>Note:</strong> If the entity is loaded before deletion, you can reference its
     * properties. Otherwise, this will be null.
     *
     * @return the SpEL expression for target display name
     */
    String targetDisplayName() default "";

    /**
     * Whether to capture before/after snapshots.
     *
     * <p>For delete operations, this is typically false because the "after" state is empty (entity
     * deleted).
     *
     * <p>However, you can enable this if you want to capture the state before deletion. In this
     * case, provide the entity (before it's deleted) as the {@link #snapshotSource()}.
     *
     * @return true to capture changes
     */
    boolean captureChanges() default false;

    /**
     * SpEL expression for the object to snapshot.
     *
     * <p>Only used when {@link #captureChanges()} is true. Example: "#entity" (entity before
     * deletion)
     *
     * <p>The snapshot source should reference the entity before it's deleted, typically by loading
     * it first and passing it as a method parameter.
     *
     * @return the SpEL expression for the snapshot source
     */
    String snapshotSource() default "";

    /**
     * Metadata fields as key:expression pairs.
     *
     * <p>Each element should be in the format: {@code "key:expression"}
     *
     * <p>Example:
     *
     * <pre>{@code
     * metadataFields = {
     *     "reason:#command.deletionReason",
     *     "requestedBy:#command.requesterName"
     * }
     * }</pre>
     *
     * @return array of key:expression pairs
     */
    String[] metadataFields() default {};

    /**
     * Additional tags for categorization.
     *
     * <p>Tags are string values that can be used to categorize actions. Example: {"financial",
     * "customer-facing"}
     *
     * @return the tags
     */
    String[] tags() default {};

    /**
     * Whether to record failures (exceptions).
     *
     * <p>When true (default), audit entries are created even when the method throws an exception.
     *
     * @return true to record failures
     */
    boolean recordFailures() default true;
}
