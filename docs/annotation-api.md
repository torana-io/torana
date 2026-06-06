# Annotation API Reference

This guide provides comprehensive documentation for Torana's annotation-based audit trail API.

## Table of Contents

- [Introduction](#introduction)
- [@AuditedAction Annotation](#auditedaction-annotation)
- [Preset Annotations](#preset-annotations)
- [Metadata Formats](#metadata-formats)
- [SpEL Expression Reference](#spel-expression-reference)
- [Migration Guide](#migration-guide)
- [Best Practices](#best-practices)
- [Examples](#examples)

## Introduction

Torana provides a declarative annotation-based API for auditing business actions. The core annotation is `@AuditedAction`, which marks methods to be audited.

```java
@AuditedAction(
    value = "order.cancelled",
    targetType = "Order",
    targetId = "#command.orderId"
)
public void cancelOrder(CancelOrderCommand command) {
    // business logic
}
```

When the annotated method executes, Torana automatically captures:
- Action name
- Target information (type, ID, display name)
- Execution outcome (success/failure)
- Actor (from Spring Security context)
- Tenant (if configured)
- Request and trace IDs
- Custom metadata
- Before/after snapshots (if enabled)

## @AuditedAction Annotation

### Required Attributes

#### `value`

The action name that describes the business operation being performed.

**Type:** `String` (required)

**Format:** Lowercase dotted notation (e.g., `"order.created"`, `"invoice.approved"`)

**Example:**
```java
@AuditedAction("order.cancelled")
public void cancelOrder(String orderId) { }
```

### Target Attributes

#### `targetType`

The type of business entity being affected (e.g., "Order", "Invoice", "Customer").

**Type:** `String` (optional, default: `""`)

**Example:**
```java
@AuditedAction(
    value = "order.cancelled",
    targetType = "Order"
)
```

#### `targetId`

SpEL expression to extract the target entity's ID from method parameters.

**Type:** `String` (optional, default: `""`)

**Format:** SpEL expression (e.g., `"#command.orderId"`, `"#orderId"`)

**Example:**
```java
@AuditedAction(
    value = "order.cancelled",
    targetType = "Order",
    targetId = "#command.orderId"  // Extracts orderId from command parameter
)
public void cancelOrder(CancelOrderCommand command) { }
```

#### `targetDisplayName`

SpEL expression to extract a human-readable display name for the target.

**Type:** `String` (optional, default: `""`)

**Format:** SpEL expression (e.g., `"#order.orderNumber"`, `"#command.orderRef"`)

**Example:**
```java
@AuditedAction(
    value = "order.cancelled",
    targetType = "Order",
    targetId = "#order.id",
    targetDisplayName = "#order.orderNumber"  // e.g., "ORD-12345"
)
public void cancelOrder(Order order) { }
```

### Change Tracking Attributes

#### `captureChanges`

Whether to capture before/after snapshots for change tracking.

**Type:** `boolean` (optional, default: `false`)

**Example:**
```java
@AuditedAction(
    value = "order.updated",
    targetType = "Order",
    targetId = "#order.id",
    captureChanges = true,
    snapshotSource = "#order"
)
public void updateOrder(Order order, UpdateOrderCommand command) {
    // Apply changes to order
}
```

#### `snapshotSource`

SpEL expression to reference the object to snapshot for change tracking.

**Type:** `String` (optional, default: `""`)

**Format:** SpEL expression (e.g., `"#order"`, `"#command.entity"`)

**Used only when:** `captureChanges = true`

**Example:**
```java
@AuditedAction(
    value = "invoice.approved",
    captureChanges = true,
    snapshotSource = "#invoice"  // Snapshot the invoice object
)
public void approveInvoice(Invoice invoice) { }
```

### Metadata Attributes

See [Metadata Formats](#metadata-formats) section for detailed documentation.

#### `metadataFields` (Recommended)

Array of key:expression pairs for metadata.

**Type:** `String[]` (optional, default: `{}`)

**Format:** Each element: `"key:expression"`

**Example:**
```java
@AuditedAction(
    value = "order.cancelled",
    metadataFields = {
        "reason:#command.reason",
        "priority:#command.priority"
    }
)
```

#### `metadata` (Legacy, Deprecated)

Comma-separated key=expression pairs.

**Type:** `String` (optional, default: `""`)

**Format:** `"key1=expr1,key2=expr2"`

**Deprecated:** Since 0.2.0. Use `metadataFields` or `@AuditMetadata` instead.

**Limitations:**
- Breaks with complex expressions containing commas
- Limited to `[\w.]+` key naming pattern
- No type safety

**Example (legacy):**
```java
@AuditedAction(
    value = "order.cancelled",
    metadata = "reason=#command.reason,priority=#command.priority"
)
```

### Other Attributes

#### `tags`

String tags for categorization and filtering.

**Type:** `String[]` (optional, default: `{}`)

**Example:**
```java
@AuditedAction(
    value = "payment.processed",
    tags = {"financial", "customer-facing"}
)
```

#### `recordFailures`

Whether to record audit entries when the method throws an exception.

**Type:** `boolean` (optional, default: `true`)

**Example:**
```java
@AuditedAction(
    value = "notification.sent",
    recordFailures = false  // Don't audit failed notifications
)
```

## Preset Annotations

Torana provides preset annotations for common CRUD operations. These are convenience wrappers around `@AuditedAction` with predefined action names, reducing boilerplate code.

### Available Presets

| Annotation | Action Name | Use Case |
|------------|-------------|----------|
| `@AuditedCreate` | `entity.created` | Entity creation operations |
| `@AuditedUpdate` | `entity.updated` | Entity update/modification operations |
| `@AuditedDelete` | `entity.deleted` | Entity deletion operations |

### Benefits

- **Less boilerplate:** No need to specify action name repeatedly
- **Consistent naming:** Enforces standard action naming conventions
- **Self-documenting:** Code intent is clear from annotation name
- **All features supported:** Supports all `@AuditedAction` attributes (metadata, change tracking, tags, etc.)

### @AuditedCreate

Preset for entity creation operations with action name `"entity.created"`.

**Attributes:**
- All standard attributes from `@AuditedAction` except `value` (predefined)
- Supports: `targetType`, `targetId`, `targetDisplayName`, `captureChanges`, `snapshotSource`, `metadataFields`, `tags`, `recordFailures`

**Example:**
```java
@AuditedCreate(
    targetType = "Order",
    targetId = "#order.id",
    captureChanges = true,
    snapshotSource = "#order"
)
@Transactional
public void createOrder(CreateOrderCommand command) {
    Order order = orderService.createOrder(command);
    orderRepository.save(order);
}
```

**Equivalent to:**
```java
@AuditedAction(
    value = "entity.created",
    targetType = "Order",
    targetId = "#order.id",
    captureChanges = true,
    snapshotSource = "#order"
)
```

**Note:** For create operations, `captureChanges` is optional since the "before" state is typically empty or null.

### @AuditedUpdate

Preset for entity update operations with action name `"entity.updated"`.

**Attributes:**
- All standard attributes from `@AuditedAction` except `value` (predefined)
- Supports: `targetType`, `targetId`, `targetDisplayName`, `captureChanges`, `snapshotSource`, `metadataFields`, `tags`, `recordFailures`

**Example:**
```java
@AuditedUpdate(
    targetType = "Order",
    targetId = "#order.id",
    captureChanges = true,
    snapshotSource = "#order"
)
@Transactional
public void updateOrder(Order order, UpdateOrderCommand command) {
    orderService.applyChanges(order, command);
    orderRepository.save(order);
}
```

**Equivalent to:**
```java
@AuditedAction(
    value = "entity.updated",
    targetType = "Order",
    targetId = "#order.id",
    captureChanges = true,
    snapshotSource = "#order"
)
```

**Recommendation:** Always enable `captureChanges = true` for update operations to track what changed.

### @AuditedDelete

Preset for entity deletion operations with action name `"entity.deleted"`.

**Attributes:**
- All standard attributes from `@AuditedAction` except `value` (predefined)
- Supports: `targetType`, `targetId`, `targetDisplayName`, `captureChanges`, `snapshotSource`, `metadataFields`, `tags`, `recordFailures`

**Example:**
```java
@AuditedDelete(
    targetType = "Order",
    targetId = "#orderId"
)
@Transactional
public void deleteOrder(String orderId) {
    orderRepository.deleteById(orderId);
}
```

**Equivalent to:**
```java
@AuditedAction(
    value = "entity.deleted",
    targetType = "Order",
    targetId = "#orderId"
)
```

**Note:** For delete operations, `captureChanges` is typically `false` since the "after" state is empty (entity deleted). However, you can enable it to capture the state before deletion if needed.

### Combining Presets with Metadata

Preset annotations work seamlessly with all metadata formats:

**With metadataFields:**
```java
@AuditedCreate(
    targetType = "Order",
    targetId = "#order.id",
    metadataFields = {
        "customerId:#order.customerId",
        "total:#order.total"
    }
)
```

**With @AuditMetadata:**
```java
@AuditedUpdate(
    targetType = "Invoice",
    targetId = "#invoice.id",
    captureChanges = true,
    snapshotSource = "#invoice"
)
@AuditMetadata({
    @MetadataField(key = "approver", value = "#approverName"),
    @MetadataField(key = "amount", value = "#invoice.total")
})
public void approveInvoice(Invoice invoice, String approverName) { }
```

### When to Use Presets

**Use preset annotations when:**
- The operation matches standard CRUD patterns (create, update, delete)
- You want consistent action naming across your codebase
- You want to reduce boilerplate

**Use @AuditedAction when:**
- The operation is domain-specific (e.g., "order.cancelled", "invoice.approved")
- You need a custom action name
- The operation doesn't fit CRUD patterns

## Metadata Formats

Torana supports three metadata formats, with varying levels of type safety and complexity support.

### Format Comparison

| Format | Type Safety | Complex Expressions | IDE Support | Recommended |
|--------|-------------|---------------------|-------------|-------------|
| `@AuditMetadata` | ✅ Excellent | ✅ Full support | ✅ Excellent | ✅ **For structured metadata** |
| `metadataFields` | ⚠️ Moderate | ✅ Full support | ✅ Good | ✅ **General purpose** |
| `metadata` (legacy) | ❌ None | ❌ Limited | ❌ Poor | ❌ **Deprecated** |

### 1. @AuditMetadata (Type-Safe Structured Format)

The recommended format for structured metadata with explicit key-value pairs.

**Benefits:**
- Compile-time validation of annotation structure
- Excellent IDE support with autocomplete
- Self-documenting code
- Type-safe with `@MetadataField` definitions

**Usage:**
```java
@AuditedAction(
    value = "order.placed",
    targetType = "Order",
    targetId = "#order.id"
)
@AuditMetadata({
    @MetadataField(key = "customerId", value = "#order.customerId"),
    @MetadataField(key = "items", value = "#order.items.size()"),
    @MetadataField(key = "total", value = "#order.total"),
    @MetadataField(key = "paymentMethod", value = "#command.paymentMethod")
})
public void placeOrder(PlaceOrderCommand command) { }
```

**Annotation Definition:**
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditMetadata {
    MetadataField[] value();
}

@Target({})
@Retention(RetentionPolicy.RUNTIME)
public @interface MetadataField {
    String key();    // The metadata key
    String value();  // SpEL expression
}
```

**Use when:**
- You have multiple metadata fields (3+ fields)
- You want explicit structure and documentation
- You prefer compile-time validation
- You work in an IDE with good annotation support

### 2. metadataFields (Array Format)

The recommended general-purpose metadata format.

**Benefits:**
- Supports complex SpEL expressions with commas, quotes, method calls
- Each array element is independent (no parsing issues)
- Simple colon separator: `"key:expression"`
- Better IDE support than legacy string format

**Usage:**
```java
@AuditedAction(
    value = "order.cancelled",
    targetType = "Order",
    targetId = "#command.orderId",
    metadataFields = {
        "reason:#command.reason",
        "priority:#command.priority",
        "total:#order.calculateTotal(#tax, #shipping)",  // Complex expression with commas
        "itemCount:#order.items.size()",
        "status:#order.status.name()"
    }
)
public void cancelOrder(CancelOrderCommand command, Order order) { }
```

**Format:** Each element is `"key:expression"` where:
- `key`: Metadata field name (any valid string, including hyphens and special characters)
- `expression`: SpEL expression evaluated against method parameters

**Use when:**
- You have simple to moderate metadata needs (1-5 fields)
- You need complex SpEL expressions
- You want concise annotation syntax
- You're migrating from legacy `metadata` format

### 3. metadata (Legacy String Format - Deprecated)

The original metadata format using comma-separated key=expression pairs.

**Status:** Deprecated since 0.2.0. Supported indefinitely for backward compatibility.

**Limitations:**
- **Breaks with commas in expressions:** `"total=order.calc(a, b)"` parses incorrectly
- **Limited key naming:** Keys restricted to `[\w.]+` pattern
- **No IDE support:** String expressions not validated
- **Silent failures:** Errors only discovered at runtime

**Usage (not recommended):**
```java
@AuditedAction(
    value = "order.cancelled",
    metadata = "reason=#command.reason,priority=#command.priority"
)
```

**Migration:** See [Migration Guide](#migration-guide) below.

### Metadata Processing Priority

When multiple metadata sources are present on the same method, they are merged in this order (later sources override earlier ones):

1. **@AuditMetadata** annotation
2. **metadataFields** array
3. **metadata** string (legacy)

**Example:**
```java
@AuditedAction(
    value = "order.placed",
    metadataFields = {
        "priority:high",
        "source:api"
    },
    metadata = "priority=low"  // Overridden by metadataFields
)
@AuditMetadata({
    @MetadataField(key = "priority", value = "'critical'")  // Final value
})
public void placeOrder() { }

// Result metadata: priority='critical', source='api'
```

This allows for gradual migration and override capabilities.

## SpEL Expression Reference

Torana uses Spring Expression Language (SpEL) for dynamic value extraction from method parameters.

### Available Variables

In SpEL expressions, you can access:

**1. Parameters by name** (requires `-parameters` compiler flag or Spring's parameter name discovery):
```java
@AuditedAction(
    value = "order.cancelled",
    targetId = "#command.orderId",  // Access by parameter name
    metadataFields = {
        "reason:#command.reason"
    }
)
public void cancelOrder(CancelOrderCommand command) { }
```

**2. Parameters by position:**
```java
@AuditedAction(
    value = "order.cancelled",
    targetId = "#arg0.orderId",  // First parameter
    metadataFields = {
        "reason:#p0.reason"  // Also first parameter
    }
)
public void cancelOrder(CancelOrderCommand command) { }
```

**3. Properties and methods:**
```java
@AuditedAction(
    metadataFields = {
        "itemCount:#order.items.size()",
        "total:#order.total",
        "customerName:#order.customer.name"
    }
)
```

**4. Complex expressions:**
```java
@AuditedAction(
    metadataFields = {
        "priority:#order.total > 1000 ? 'high' : 'normal'",
        "itemNames:#order.items.![name]",  // Collection projection
        "hasDiscount:#order.discountCode != null"
    }
)
```

### Common SpEL Patterns

#### Conditional Expressions
```java
"priority:#amount > 1000 ? 'high' : 'low'"
"status:#isApproved ? 'approved' : 'pending'"
```

#### Null-Safe Navigation
```java
"customerName:#order.customer?.name"  // Returns null if customer is null
"email:#user?.profile?.email"
```

#### Collection Operations
```java
"itemCount:#order.items.size()"
"itemNames:#order.items.![name]"              // Projection: list of names
"total:#order.items.![price].sum()"           // Sum all prices
"hasExpensive:#order.items.?[price > 100]"    // Selection: filter items
```

#### Method Calls
```java
"upperName:#command.name.toUpperCase()"
"formatted:#order.formatTotal(#locale)"
"calculated:#service.calculateTotal(#order, #tax)"  // Method with multiple params
```

#### String Operations
```java
"description:'Order for customer: ' + #customerId"
"status:#order.status.name().toLowerCase()"
```

### Expression Evaluation

- **Evaluation context:** Method parameters are available as variables
- **Null handling:** Expressions that evaluate to `null` are excluded from metadata
- **Error handling:** Exceptions during evaluation are caught silently (field excluded from metadata)
- **Type conversion:** All values evaluated to `Object.class` (generic type)

## Migration Guide

### Migrating from Legacy `metadata` String

If you're using the legacy `metadata` attribute, here's how to migrate:

**Before (legacy format):**
```java
@AuditedAction(
    value = "order.cancelled",
    metadata = "reason=#command.reason,priority=#command.priority"
)
```

**After (Option 1: metadataFields):**
```java
@AuditedAction(
    value = "order.cancelled",
    metadataFields = {
        "reason:#command.reason",
        "priority:#command.priority"
    }
)
```

**After (Option 2: @AuditMetadata):**
```java
@AuditedAction(value = "order.cancelled")
@AuditMetadata({
    @MetadataField(key = "reason", value = "#command.reason"),
    @MetadataField(key = "priority", value = "#command.priority")
})
```

### Migration Strategy

1. **Identify breaking cases first:** Find usages with complex expressions containing commas
2. **Migrate gradually:** Both formats can coexist in the same codebase
3. **Test each migration:** Verify metadata is captured correctly after migration
4. **Remove legacy format:** Once all usages are migrated (optional)

### Finding Usages

Search for:
```
metadata\s*=\s*"
```

This regex finds all `metadata = "..."` usages in your codebase.

## Best Practices

### 1. Choose the Right Metadata Format

- **Use `@AuditMetadata`** for structured metadata with 3+ fields
- **Use `metadataFields`** for simple metadata with 1-3 fields
- **Avoid legacy `metadata` string** for new code

### 2. Action Naming Conventions

Use lowercase dotted notation:
```java
@AuditedAction("order.created")       // ✅ Good
@AuditedAction("invoice.approved")    // ✅ Good
@AuditedAction("ORDER.CREATED")       // ❌ Bad - uppercase
@AuditedAction("order_created")       // ❌ Bad - underscore
```

### 3. Target Type Naming

Use PascalCase for entity types:
```java
targetType = "Order"           // ✅ Good
targetType = "Invoice"         // ✅ Good
targetType = "CustomerProfile" // ✅ Good
targetType = "order"           // ❌ Bad - lowercase
targetType = "ORDER"           // ❌ Bad - uppercase
```

### 4. Metadata Key Naming

Use camelCase for metadata keys:
```java
metadataFields = {
    "customerId:#order.customerId",        // ✅ Good
    "paymentMethod:#command.paymentMethod", // ✅ Good
    "customer_id:#order.customerId"        // ⚠️ OK but not preferred
}
```

### 5. Use Meaningful Metadata

Capture business-relevant data, not technical details:
```java
// ✅ Good - business context
metadataFields = {
    "reason:#command.cancellationReason",
    "refundAmount:#order.total",
    "customerTier:#customer.tier"
}

// ❌ Bad - technical details
metadataFields = {
    "className:#this.class.name",
    "threadId:#thread.id"
}
```

### 6. Capture Changes for Updates

Enable `captureChanges` for update/modify operations:
```java
@AuditedAction(
    value = "order.updated",
    captureChanges = true,     // ✅ Good - track what changed
    snapshotSource = "#order"
)
```

Don't enable for create/delete (before/after snapshots not meaningful):
```java
@AuditedAction(
    value = "order.created",
    captureChanges = false  // ✅ Good - no meaningful before state
)
```

### 7. Handle Null Values Gracefully

Use null-safe navigation:
```java
metadataFields = {
    "email:#user.profile?.email",  // ✅ Good - null-safe
    "name:#user?.name"
}
```

### 8. Keep Expressions Simple

Complex logic should be in methods, not SpEL:
```java
// ❌ Bad - complex logic in SpEL
"priority:#order.total > 1000 && #order.customer.tier == 'gold' ? 'high' : 'low'"

// ✅ Good - logic in method
"priority:#order.calculatePriority()"
```

## Examples

### Example 1: Order Cancellation with Metadata

```java
@AuditedAction(
    value = "order.cancelled",
    targetType = "Order",
    targetId = "#orderId",
    targetDisplayName = "#order.orderNumber",
    metadataFields = {
        "reason:#command.reason",
        "refundAmount:#order.total",
        "customerEmail:#order.customer.email"
    }
)
@Transactional
public void cancelOrder(String orderId, CancelOrderCommand command) {
    Order order = orderRepository.findById(orderId);
    order.cancel(command.reason());
    orderRepository.save(order);
}
```

### Example 2: Invoice Approval with Change Tracking

```java
@AuditedAction(
    value = "invoice.approved",
    targetType = "Invoice",
    targetId = "#invoice.id",
    targetDisplayName = "#invoice.invoiceNumber",
    captureChanges = true,
    snapshotSource = "#invoice"
)
@AuditMetadata({
    @MetadataField(key = "approver", value = "#approverName"),
    @MetadataField(key = "amount", value = "#invoice.total"),
    @MetadataField(key = "dueDate", value = "#invoice.dueDate"),
    @MetadataField(key = "vendor", value = "#invoice.vendor.name")
})
@Transactional
public void approveInvoice(Invoice invoice, String approverName) {
    invoice.approve(approverName);
    invoiceRepository.save(invoice);
}
```

### Example 3: Payment Processing with Complex Metadata

```java
@AuditedAction(
    value = "payment.processed",
    targetType = "Payment",
    targetId = "#payment.id",
    tags = {"financial", "customer-facing"},
    metadataFields = {
        "amount:#payment.amount",
        "currency:#payment.currency",
        "method:#payment.method.name()",
        "last4:#payment.card.lastFourDigits",
        "success:#result.isSuccessful()",
        "transactionId:#result.transactionId"
    }
)
public PaymentResult processPayment(Payment payment, ProcessPaymentCommand command) {
    PaymentResult result = paymentGateway.charge(payment);
    return result;
}
```

### Example 4: User Registration with Conditional Metadata

```java
@AuditedAction(
    value = "user.registered",
    targetType = "User",
    targetId = "#user.id",
    targetDisplayName = "#user.email"
)
@AuditMetadata({
    @MetadataField(key = "email", value = "#user.email"),
    @MetadataField(key = "plan", value = "#command.subscriptionPlan"),
    @MetadataField(key = "referralSource", value = "#command.referralCode ?: 'direct'"),
    @MetadataField(key = "isPremium", value = "#command.subscriptionPlan == 'premium'")
})
public void registerUser(RegisterUserCommand command) {
    User user = userService.createUser(command);
    emailService.sendWelcomeEmail(user);
}
```

### Example 5: Combining Multiple Metadata Formats

```java
@AuditedAction(
    value = "order.modified",
    targetType = "Order",
    targetId = "#order.id",
    captureChanges = true,
    snapshotSource = "#order",
    metadataFields = {
        "modifiedBy:#securityContext.username",  // From metadataFields
        "timestamp:#instant.now()"
    }
)
@AuditMetadata({
    @MetadataField(key = "changeType", value = "#changeType"),  // From @AuditMetadata
    @MetadataField(key = "fieldCount", value = "#changes.size()")
})
public void modifyOrder(Order order, Map<String, Object> changes, String changeType) {
    orderService.applyChanges(order, changes);
}

// Result metadata includes: modifiedBy, timestamp, changeType, fieldCount
```

## See Also

- [Transaction Semantics Guide](transaction-semantics.md) - Transaction behavior and error handling
- [Schema Management Guide](schema-management.md) - Database schema setup
- [Configuration Reference](configuration-reference.md) - Full configuration options
