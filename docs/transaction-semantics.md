# Transaction Semantics

This guide explains how Torana handles transactions and errors during audit processing.

## Overview

Torana's transaction behavior is fully configurable. By default:

- **Success entries** are written after transaction commit (prevents orphaned audit records)
- **Failure entries** are written immediately (captures the attempt)
- **Audit failures** are logged but don't affect business operations

This behavior can be customized for your specific compliance, debugging, or operational requirements.

## Configuration

```yaml
torana:
  transaction:
    success-write-policy: after_commit  # after_commit | immediate | requires_new
    failure-write-policy: immediate     # after_commit | immediate | requires_new
    audit-error-policy: log_and_continue # log_and_continue | fail_transaction | callback
```

## Production Questions Answered

### Q1: If my business transaction rolls back, is the audit entry written?

**Answer:** It depends on the write policy configured.

| Policy | Entry Written on Rollback? | When to Use |
|--------|----------------------------|-------------|
| `after_commit` | **NO** - Entry discarded with transaction | Audit should reflect committed state only (default for success) |
| `immediate` | **YES** - Entry already persisted | You want to audit attempts, not just successes (default for failure) |
| `requires_new` | **YES** - Entry in separate transaction | Audit must survive rollbacks for compliance |

**Examples:**

**Scenario 1: Default behavior (success = after_commit, failure = immediate)**

```java
@Transactional
@AuditedAction("order.created")
public void createOrder(Order order) {
    orderRepository.save(order);
    if (fraud detected) {
        throw new FraudException(); // Transaction rolls back
    }
}
```

Result:
- If fraud detected: **SUCCESS entry NOT written** (rolled back), but if `recordFailures=true`, **FAILURE entry IS written** (immediate)
- If no fraud: **SUCCESS entry written** after commit

**Scenario 2: Compliance mode (both = requires_new)**

```yaml
torana:
  transaction:
    success-write-policy: requires_new
    failure-write-policy: requires_new
```

Result:
- Audit entries **always written**, even if business transaction rolls back
- Separate audit transaction commits independently

### Q2: If my audited method throws an exception, is the failure recorded?

**Answer:** Yes, if `recordFailures=true` in `@AuditedAction` (the default).

The exception is captured with:
- `outcome` set to `FAILURE`
- `error_message` containing the exception message
- Stacktrace in logs

The `failure-write-policy` determines **when** it's written:

| Policy | When Written | Use Case |
|--------|--------------|----------|
| `immediate` (default) | Right away, before transaction completes | Capture all failure attempts |
| `after_commit` | Only if transaction commits | Unusual - only committed failures |
| `requires_new` | In separate transaction | Critical failure audit for compliance |

**Example:**

```java
@AuditedAction(value = "payment.processed", recordFailures = true)
public void processPayment(Payment payment) {
    // This will be audited as FAILURE if it throws
    paymentGateway.charge(payment);
}
```

If `paymentGateway.charge()` throws:
- Audit entry created with `outcome=FAILURE`
- `error_message` contains exception message
- With `failure-write-policy: immediate`, entry written even if `@Transactional` method rolls back

### Q3: If audit persistence fails, does my business operation fail?

**Answer:** It depends on the `audit-error-policy` configuration.

| Policy | Business Fails? | When to Use |
|--------|-----------------|-------------|
| `log_and_continue` (default) | **NO** - Audit error logged, business continues | Audit important but not critical to correctness |
| `fail_transaction` | **YES** - Business transaction rolls back | Audit mandatory for compliance (no operation without audit) |
| `callback` | **Depends on handler** | Custom logic needed (selective failure, alerting, etc.) |

**Example with default (log_and_continue):**

```java
@Transactional
@AuditedAction("order.submitted")
public void submitOrder(Order order) {
    orderRepository.save(order);
    // If audit write fails (e.g., DB down), error is logged
    // but order is still saved successfully
}
```

**Example with fail_transaction:**

```yaml
torana:
  transaction:
    audit-error-policy: fail_transaction
```

```java
@Transactional
@AuditedAction("compliant.action")
public void complianceAction(Data data) {
    repository.save(data);
    // If audit write fails, entire transaction rolls back
    // No data persisted without corresponding audit entry
}
```

**Example with callback:**

```java
@Component
public class CustomAuditErrorHandler implements AuditErrorHandler {
    @Override
    public void handleError(AuditContext context, AuditEntry entry,
                          Exception error, ErrorPhase phase) {
        // Send to monitoring
        metrics.counter("audit.errors", "phase", phase.name()).increment();

        // Fail only for critical phases
        if (phase == ErrorPhase.PERSISTENCE) {
            throw new AuditException("Critical audit failure", error);
        }
        // Otherwise, log and continue
    }
}
```

### Q4: If the afterCommit audit write fails, how is that reported?

**Answer:** It depends on the `audit-error-policy` configuration.

| Policy | How Reported | Notes |
|--------|--------------|-------|
| `log_and_continue` (default) | ERROR level log | Business transaction already committed, cannot rollback |
| `fail_transaction` | Exception thrown | **Warning:** Transaction already committed! Cannot rollback. |
| `callback` | Custom handler invoked | Handler decides how to report (metrics, alerts, etc.) |

**Important Limitation:**

After-commit failures **cannot rollback** the business transaction because it's already committed!

Example sequence:
1. Business logic executes
2. `@Transactional` commits the database changes
3. Torana attempts to write audit entry
4. Audit write fails (database connection lost, table doesn't exist, etc.)
5. At this point, business data is already committed ✓
6. Audit entry cannot be written ✗

**Recommendations:**

1. **For critical audits**: Use `requires_new` policy instead of `after_commit`
   - Audit writes in separate transaction before business commits
   - If audit fails, business transaction can still rollback

2. **Monitor audit errors**: Set up alerts on audit error logs
   - `log.error("Audit processing failed at PERSISTENCE phase...")`
   - Use log aggregation (ELK, Splunk, etc.) with alerts

3. **Test failure scenarios**: Ensure audit system is resilient
   - Database connection pool exhaustion
   - Table doesn't exist
   - Disk full

## Write Policies

### `after_commit` (Default for Success)

Writes audit entry after the business transaction commits successfully.

**Pros:**
- Prevents orphaned audit records for rolled-back transactions
- Audit trail accurately reflects committed state
- No extra transaction overhead

**Cons:**
- Audit entries lost if transaction rolls back
- After-commit write failures cannot rollback business changes
- Not suitable if you need to audit failed attempts

**Use when:**
- Audit should reflect final committed state only
- You don't need audit trails of failed transactions
- Performance is important (no extra transactions)

**Configuration:**
```yaml
torana:
  transaction:
    success-write-policy: after_commit
```

### `immediate`

Writes audit entry immediately, regardless of transaction state.

**Pros:**
- Captures all attempts, even those that later rollback
- Useful for debugging and failure analysis
- No dependency on transaction state

**Cons:**
- Can create "orphaned" audit entries for rolled-back transactions
- Audit trail may include uncommitted operations
- Requires cleanup logic for invalid entries

**Use when:**
- You need to audit all attempts, not just successes
- Debugging or forensics require full attempt history
- Audit is independent of business transaction

**Configuration:**
```yaml
torana:
  transaction:
    success-write-policy: immediate
    failure-write-policy: immediate
```

### `requires_new`

Writes audit entry in a new transaction using Spring's REQUIRES_NEW propagation.

**Pros:**
- Audit persists even if parent transaction rolls back
- Guarantees audit trail for compliance scenarios
- Audit write failures don't affect business transaction (with log_and_continue)

**Cons:**
- Creates extra transaction overhead (performance impact)
- Requires `PlatformTransactionManager` bean
- May create "success" audit for eventually-failed operations

**Use when:**
- Compliance requires audit trail of all operations, even failures
- Audit integrity is more important than performance
- You need guaranteed audit persistence

**Requirements:**
- Spring transaction manager must be configured
- Works only in Spring applications

**Configuration:**
```yaml
torana:
  transaction:
    success-write-policy: requires_new
    failure-write-policy: requires_new
```

**Error if missing transaction manager:**
```
IllegalStateException: REQUIRES_NEW write policy requires PlatformTransactionManager.
Ensure a transaction manager bean is configured, or use IMMEDIATE or AFTER_COMMIT policy instead.
```

## Error Policies

### `log_and_continue` (Default)

Logs audit errors at ERROR level and allows business operation to continue.

**Behavior:**
- Audit pipeline catches all exceptions
- Logs error with full context (action, phase, exception)
- Business transaction proceeds normally

**Log format:**
```
ERROR io.torana.core.AuditPipeline - Audit processing failed at PERSISTENCE phase for action 'order.created': Connection timeout
```

**Use when:**
- Audit is important for observability but not critical for correctness
- Business operations must complete even if audit system fails
- You monitor audit errors through log aggregation

**Configuration:**
```yaml
torana:
  transaction:
    audit-error-policy: log_and_continue
```

### `fail_transaction`

Throws exception to fail the business transaction when audit processing fails.

**Behavior:**
- Audit pipeline throws exception on error
- Business transaction rolls back (if still active)
- Client receives error response

**Use when:**
- Audit is mandatory for compliance or regulatory requirements
- Business operations without audit trails are considered invalid
- Audit integrity is more important than availability

**Warning:**
- With `after_commit` policy, audit errors occur AFTER business commit
- In this case, exception is thrown but transaction cannot rollback
- Consider using `requires_new` policy with `fail_transaction`

**Configuration:**
```yaml
torana:
  transaction:
    success-write-policy: requires_new  # Ensures audit before business commits
    failure-write-policy: requires_new
    audit-error-policy: fail_transaction
```

### `callback`

Invokes custom `AuditErrorHandler` to decide how to handle errors.

**Behavior:**
- Audit pipeline calls your custom handler on error
- Handler receives full context (audit context, entry, exception, phase)
- Handler decides whether to throw exception or continue

**Use when:**
- Different errors should be handled differently
- You need integration with external monitoring/alerting
- Custom retry logic is required
- Phase-specific error handling needed

**Example handler:**

```java
@Component
public class ProductionAuditErrorHandler implements AuditErrorHandler {

    private final MetricRegistry metrics;
    private final AlertService alerts;

    @Override
    public void handleError(AuditContext context, AuditEntry entry,
                          Exception error, ErrorPhase phase) throws Exception {

        // Always record metrics
        metrics.counter("audit.errors",
            "phase", phase.name(),
            "action", context.getAction().name()
        ).increment();

        // Send alerts for persistence failures
        if (phase == ErrorPhase.PERSISTENCE) {
            alerts.send("Audit persistence failed for " + context.getAction().name());
        }

        // Fail transaction only for critical actions
        if (context.getAction().name().startsWith("compliance.")) {
            throw new AuditException("Critical audit failure for compliance action", error);
        }

        // Otherwise, log and continue
        log.error("Non-critical audit error at {} phase: {}", phase, error.getMessage());
    }
}
```

**Configuration:**
```yaml
torana:
  transaction:
    audit-error-policy: callback
```

**Fallback:** If no handler is registered, falls back to `log_and_continue` behavior.

## Error Phases

Errors are tracked by phase for fine-grained handling:

| Phase | When It Occurs | Common Causes |
|-------|---------------|---------------|
| `COLLECTION` | During context collection (actor, tenant, request, trace) | Spring Security context unavailable, resolver exceptions |
| `CREATION` | During audit entry creation | Snapshot provider errors, SpEL expression failures |
| `REDACTION` | During sensitive data redaction | JSON serialization errors, regex compilation failures |
| `PERSISTENCE` | During audit write to storage | Database down, table missing, connection timeout |

Use phases in custom handlers to implement different strategies per phase.

## Configuration Examples

### Conservative (Default)

Suitable for most applications where audit is valuable but not critical.

```yaml
torana:
  transaction:
    success-write-policy: after_commit
    failure-write-policy: immediate
    audit-error-policy: log_and_continue
```

**Behavior:**
- Success entries written after commit (no orphaned records)
- Failure entries written immediately (captures attempts)
- Audit errors don't affect business (logged only)

### Compliance-Critical

For applications where audit trails are mandatory (financial, healthcare, etc.).

```yaml
torana:
  transaction:
    success-write-policy: requires_new
    failure-write-policy: requires_new
    audit-error-policy: fail_transaction
```

**Behavior:**
- All audit entries written in separate transactions
- Audit persists even if business transaction rolls back
- Audit failures cause business transaction to fail
- **Requires:** PlatformTransactionManager bean

### Debug/Forensics

For troubleshooting or forensic analysis where you need complete attempt history.

```yaml
torana:
  transaction:
    success-write-policy: immediate
    failure-write-policy: immediate
    audit-error-policy: log_and_continue
```

**Behavior:**
- All entries written immediately
- Captures every attempt, including rollbacks
- May create orphaned audit entries (audit says success, but transaction rolled back)
- Useful for debugging but not for production auditing

### Custom Monitoring

For applications with sophisticated monitoring and alerting.

```yaml
torana:
  transaction:
    success-write-policy: after_commit
    failure-write-policy: immediate
    audit-error-policy: callback
```

Plus custom handler implementation for metrics, alerts, and selective failure.

## Best Practices

### 1. Choose the Right Policy for Your Use Case

| Use Case | Success Policy | Failure Policy | Error Policy |
|----------|----------------|----------------|--------------|
| General business auditing | `after_commit` | `immediate` | `log_and_continue` |
| Compliance/regulatory | `requires_new` | `requires_new` | `fail_transaction` |
| Debugging/troubleshooting | `immediate` | `immediate` | `log_and_continue` |
| High availability | `after_commit` | `immediate` | `log_and_continue` |
| Custom monitoring | `after_commit` | `immediate` | `callback` |

### 2. Monitor Audit Errors

Even with `log_and_continue`, monitor audit errors:

```yaml
# Example: Prometheus metrics
audit.errors.total{phase="PERSISTENCE"} > 10
```

Set up alerts for:
- High error rates
- Persistence phase failures
- Specific critical actions

### 3. Test Failure Scenarios

Test what happens when:
- Database is down
- Audit table doesn't exist
- Transaction manager unavailable (for `requires_new`)
- Network partition

### 4. Document Your Configuration

Document why you chose specific policies:

```yaml
torana:
  transaction:
    # Use requires_new for GDPR compliance - all data access must be audited
    success-write-policy: requires_new
    failure-write-policy: requires_new
    # Fail transaction if audit fails - no unaudited data access allowed
    audit-error-policy: fail_transaction
```

### 5. Consider Performance Impact

`requires_new` creates extra transactions:

- Benchmark with realistic load
- Monitor transaction count and duration
- Use selectively for critical actions only

### 6. Plan for After-Commit Failures

With `after_commit` policy:

- Monitor after-commit write errors
- Consider `requires_new` for critical audits
- Have fallback audit mechanism (file logs, message queue)

## Migration Guide

### From Hardcoded to Explicit Configuration

Before version 0.2.0, transaction behavior was hardcoded. If you're upgrading:

**Old behavior (implicit):**
- Success entries: `after_commit`
- Failure entries: `immediate`
- Errors: logged, business continues

**New behavior (explicit, but same defaults):**

```yaml
torana:
  transaction:
    success-write-policy: after_commit  # Same as before
    failure-write-policy: immediate     # Same as before
    audit-error-policy: log_and_continue # Same as before
```

**No action required** if you want to keep the existing behavior. The defaults match the previous hardcoded behavior.

### Enabling New Features

To take advantage of new features:

**1. Guaranteed audit persistence:**

```yaml
torana:
  transaction:
    success-write-policy: requires_new
```

**2. Fail on audit errors:**

```yaml
torana:
  transaction:
    audit-error-policy: fail_transaction
```

**3. Custom error handling:**

```java
@Component
public class MyAuditErrorHandler implements AuditErrorHandler {
    // Implementation
}
```

```yaml
torana:
  transaction:
    audit-error-policy: callback
```

## Troubleshooting

### "REQUIRES_NEW write policy requires PlatformTransactionManager"

**Cause:** Configured `requires_new` policy but no transaction manager bean found.

**Solution:**
1. Ensure Spring transaction management is enabled:
```java
@EnableTransactionManagement
@SpringBootApplication
public class Application { }
```

2. Ensure DataSource is configured (transaction manager auto-configured)

3. Or provide explicit transaction manager:
```java
@Bean
public PlatformTransactionManager transactionManager(DataSource dataSource) {
    return new DataSourceTransactionManager(dataSource);
}
```

### Audit entries not written

**Check:**
1. `torana.enabled=true` in configuration
2. Schema exists (if `schema-mode: none`)
3. Transaction is actually committing (if `after_commit` policy)
4. Check logs for audit errors

### Business transactions failing unexpectedly

**Check:**
1. `audit-error-policy` - if set to `fail_transaction`, audit errors will fail business operations
2. Check audit error logs to identify root cause
3. Consider changing to `log_and_continue` if audit is not critical

## See Also

- [Schema Management Guide](./schema-management.md) - How to set up the audit table
- [Database Migrations](./migrations.md) - Schema evolution across versions
- [Configuration Reference](./configuration-reference.md) - Full property documentation
