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
 *
 * <h2>Metadata</h2>
 *
 * <p>There are three ways to specify metadata:
 *
 * <ol>
 *   <li><strong>{@link #metadataFields()} (recommended):</strong> Array of key:expression pairs.
 *       Supports complex SpEL expressions with commas, quotes, and method calls.
 *   <li><strong>{@code @AuditMetadata}:</strong> Companion annotation for structured metadata with
 *       type-safe {@code @MetadataField} definitions.
 *   <li><strong>{@link #metadata()} (legacy):</strong> Comma-separated string format. Deprecated
 *       due to parsing limitations with complex expressions.
 * </ol>
 *
 * <p>Example with {@link #metadataFields()}:
 *
 * <pre>{@code
 * @AuditedAction(
 *     value = "order.cancelled",
 *     targetType = "Order",
 *     targetId = "#command.orderId",
 *     metadataFields = {
 *         "reason:#command.reason",
 *         "priority:#command.priority"
 *     }
 * )
 * }</pre>
 *
 * <p>Example with {@code @AuditMetadata}:
 *
 * <pre>{@code
 * @AuditedAction(value = "order.placed", targetType = "Order", targetId = "#order.id")
 * @AuditMetadata({
 *     @MetadataField(key = "customerId", value = "#order.customerId"),
 *     @MetadataField(key = "total", value = "#order.total")
 * })
 * public void placeOrder(PlaceOrderCommand command) {
 *     // business logic
 * }
 * }</pre>
 *
 * @see io.torana.api.AuditMetadata
 * @see io.torana.api.MetadataField
 */
@Target({ElementType.METHOD, ElementType.ANNOTATION_TYPE})
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
     * <p><strong>Note:</strong> This format has limitations with complex expressions containing
     * commas. Use {@link #metadataFields()} or {@code @AuditMetadata} instead for better
     * reliability.
     *
     * @return the metadata expressions
     * @deprecated since 0.2.0. Use {@link #metadataFields()} or {@code @AuditMetadata} annotation
     *     for better type safety and support for complex expressions. This attribute will remain
     *     supported indefinitely for backward compatibility.
     */
    @Deprecated(since = "0.2.0", forRemoval = false)
    String metadata() default "";

    /**
     * Metadata fields as key:expression pairs.
     *
     * <p>This is the recommended way to specify metadata, replacing the legacy comma-separated
     * {@link #metadata()} string format. Each array element is independent, allowing complex SpEL
     * expressions with commas, quotes, and method calls.
     *
     * <p>Each element should be in the format: {@code "key:expression"} where the expression is a
     * SpEL expression evaluated against method parameters.
     *
     * <p>Example:
     *
     * <pre>{@code
     * @AuditedAction(
     *     value = "order.cancelled",
     *     targetType = "Order",
     *     targetId = "#command.orderId",
     *     metadataFields = {
     *         "reason:#command.reason",
     *         "priority:#command.priority",
     *         "total:#order.calculateTotal(#tax, #shipping)"
     *     }
     * )
     * }</pre>
     *
     * <p>Can be combined with {@code @AuditMetadata} annotation for structured metadata. When
     * multiple metadata sources are present, they are merged with later sources overriding earlier
     * ones.
     *
     * @return array of key:expression pairs
     * @see io.torana.api.AuditMetadata
     */
    String[] metadataFields() default {};

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
