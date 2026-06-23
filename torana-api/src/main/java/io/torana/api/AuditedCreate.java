package io.torana.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Preset annotation for auditing entity creation operations.
 *
 * <p>This is a convenience annotation that wraps {@link AuditedAction} with a predefined action
 * name of "entity.created". It reduces boilerplate for the common pattern of auditing create
 * operations.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @AuditedCreate(
 *     targetType = "Order",
 *     targetId = "#order.id",
 *     captureChanges = true,
 *     snapshotSource = "#order"
 * )
 * @Transactional
 * public void createOrder(CreateOrderCommand command) {
 *     Order order = orderService.createOrder(command);
 *     orderRepository.save(order);
 * }
 * }</pre>
 *
 * <p>This is equivalent to:
 *
 * <pre>{@code
 * @AuditedAction(
 *     value = "entity.created",
 *     targetType = "Order",
 *     targetId = "#order.id",
 *     captureChanges = true,
 *     snapshotSource = "#order"
 * )
 * }</pre>
 *
 * @see AuditedAction
 * @see AuditedUpdate
 * @see AuditedDelete
 */
@AuditedAction(value = "entity.created")
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditedCreate {

    /**
     * The type of entity being created (e.g., "Order", "Invoice", "Customer").
     *
     * <p>This is a required attribute that identifies the type of business object being created.
     *
     * @return the target entity type
     */
    String targetType();

    /**
     * SpEL expression for the entity ID.
     *
     * <p>The expression is evaluated against method parameters. Example: "#order.id" or
     * "#result.id"
     *
     * <p>For create operations, you typically reference the created entity after it's been assigned
     * an ID.
     *
     * @return the SpEL expression for target ID
     */
    String targetId();

    /**
     * SpEL expression for the display name (optional).
     *
     * <p>Optional human-readable name for the created entity. Example: "#order.orderNumber" or
     * "#customer.email"
     *
     * @return the SpEL expression for target display name
     */
    String targetDisplayName() default "";

    /**
     * Whether to capture before/after snapshots.
     *
     * <p>For create operations, the "before" snapshot is typically null or empty. Enable this if
     * you want to capture the initial state of the created entity.
     *
     * @return true to capture changes
     */
    boolean captureChanges() default false;

    /**
     * SpEL expression for the object to snapshot.
     *
     * <p>Only used when {@link #captureChanges()} is true. Example: "#order" or "#result"
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
     *     "customerId:#order.customerId",
     *     "total:#order.total"
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
