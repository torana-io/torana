package io.torana.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Preset annotation for auditing entity update operations.
 *
 * <p>This is a convenience annotation that wraps {@link AuditedAction} with a predefined action
 * name of "entity.updated". It reduces boilerplate for the common pattern of auditing update
 * operations.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @AuditedUpdate(
 *     targetType = "Order",
 *     targetId = "#order.id",
 *     captureChanges = true,
 *     snapshotSource = "#order"
 * )
 * @Transactional
 * public void updateOrder(Order order, UpdateOrderCommand command) {
 *     orderService.applyChanges(order, command);
 *     orderRepository.save(order);
 * }
 * }</pre>
 *
 * <p>This is equivalent to:
 *
 * <pre>{@code
 * @AuditedAction(
 *     value = "entity.updated",
 *     targetType = "Order",
 *     targetId = "#order.id",
 *     captureChanges = true,
 *     snapshotSource = "#order"
 * )
 * }</pre>
 *
 * <p><strong>Recommended:</strong> Enable {@link #captureChanges()} for update operations to track
 * what changed.
 *
 * @see AuditedAction
 * @see AuditedCreate
 * @see AuditedDelete
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditedUpdate {

    /**
     * The type of entity being updated (e.g., "Order", "Invoice", "Customer").
     *
     * <p>This is a required attribute that identifies the type of business object being updated.
     *
     * @return the target entity type
     */
    String targetType();

    /**
     * SpEL expression for the entity ID.
     *
     * <p>The expression is evaluated against method parameters. Example: "#order.id" or "#orderId"
     *
     * @return the SpEL expression for target ID
     */
    String targetId();

    /**
     * SpEL expression for the display name (optional).
     *
     * <p>Optional human-readable name for the updated entity. Example: "#order.orderNumber" or
     * "#customer.email"
     *
     * @return the SpEL expression for target display name
     */
    String targetDisplayName() default "";

    /**
     * Whether to capture before/after snapshots.
     *
     * <p><strong>Recommended: true</strong> for update operations to track what changed.
     *
     * <p>When enabled, Torana will capture the state of the entity before and after the method
     * execution, allowing you to see exactly what fields were modified.
     *
     * @return true to capture changes
     */
    boolean captureChanges() default false;

    /**
     * SpEL expression for the object to snapshot.
     *
     * <p>Only used when {@link #captureChanges()} is true. Example: "#order" or "#entity"
     *
     * <p>The snapshot source should reference the entity being updated, typically a method
     * parameter.
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
     *     "changeType:#command.changeType",
     *     "modifiedBy:#securityContext.username"
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
