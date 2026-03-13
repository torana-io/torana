package io.torana.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as an audited business action.
 *
 * <p>When a method annotated with {@code @AuditedAction} is invoked, Torana will capture an audit
 * entry with the specified action name, target information, and execution context.
 *
 * <p>Example usage:
 *
 * <pre>{@code
 * @AuditedAction(
 *     value = "order.cancelled",
 *     targetType = "Order",
 *     targetId = "#command.orderId"
 * )
 * @Transactional
 * public void cancelOrder(CancelOrderCommand command) {
 *     // business logic
 * }
 * }</pre>
 *
 * <p>The {@code targetId} attribute supports SpEL expressions to extract values from method
 * parameters.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditedAction {

    /**
     * The action name (e.g., "order.cancelled").
     *
     * <p>Action names should follow lowercase dotted notation.
     *
     * @return the action name
     */
    String value();

    /**
     * The target type (e.g., "Order", "Invoice").
     *
     * <p>Identifies the type of business object being affected.
     *
     * @return the target type, or empty string if not specified
     */
    String targetType() default "";

    /**
     * SpEL expression for the target ID.
     *
     * <p>The expression is evaluated against method parameters. Example: "#command.orderId" or
     * "#orderId"
     *
     * @return the SpEL expression for target ID, or empty string if not specified
     */
    String targetId() default "";

    /**
     * SpEL expression for the target display name.
     *
     * <p>Optional human-readable name for the target.
     *
     * @return the SpEL expression for target display name
     */
    String targetDisplayName() default "";

    /**
     * Whether to capture before/after snapshots for change tracking.
     *
     * <p>When enabled, Torana will capture the state of the object specified by {@link
     * #snapshotSource()} before and after method execution.
     *
     * @return true to capture changes
     */
    boolean captureChanges() default false;

    /**
     * SpEL expression for the object to snapshot for change tracking.
     *
     * <p>Only used when {@link #captureChanges()} is true. Example: "#order" or "#command.order"
     *
     * @return the SpEL expression for the snapshot source
     */
    String snapshotSource() default "";

    /**
     * Additional metadata to include in the audit entry.
     *
     * <p>Format: key=expression pairs separated by commas. Example: "reason=#command.reason,
     * priority=#command.priority"
     *
     * @return the metadata expressions
     */
    String metadata() default "";

    /**
     * Additional tags for categorization and filtering.
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
