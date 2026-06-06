# Torana Resilience & Error Recovery Guide

**Version:** 0.2.0
**Last Updated:** 2026-06-06
**Audience:** Operations, SRE, DevOps, Platform Engineers

This guide covers resilience patterns, error recovery mechanisms, and failure handling in the Torana audit trail system.

---

## Table of Contents

- [Overview](#overview)
- [Resilience Patterns](#resilience-patterns)
  - [Retry Pattern](#retry-pattern)
  - [Circuit Breaker Pattern](#circuit-breaker-pattern)
  - [Fallback Mechanisms](#fallback-mechanisms)
- [Recovery Service](#recovery-service)
- [Configuration Guide](#configuration-guide)
- [Operational Procedures](#operational-procedures)
- [Monitoring Resilience](#monitoring-resilience)
- [Best Practices](#best-practices)

---

## Overview

Torana implements multiple resilience patterns to ensure audit failures never impact business operations:

```
Business Operation
  ↓
@AuditedAction Aspect
  ↓
┌─────────────────────────────────────┐
│  Resilience Stack (Optional)        │
│  ┌───────────────────────────────┐  │
│  │ RetryableAuditWriter          │  │
│  │  ↓                             │  │
│  │ CircuitBreakerAuditWriter     │  │
│  │  ├─ Primary: JdbcAuditWriter  │  │
│  │  └─ Fallback: File/Logging    │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘
  ↓
Database (or Fallback Storage)
```

**Key Principles:**

1. **Business operations never fail due to audit issues**
2. **Failures are logged, metrics collected, and fallback activated**
3. **Automatic recovery when systems recover**
4. **Zero data loss through durable fallback storage**

---

## Resilience Patterns

### Retry Pattern

**Purpose:** Automatically retry transient failures (network issues, temporary database unavailability)

**Implementation:** `RetryableAuditWriter` (Resilience4j)

#### Configuration

```yaml
torana:
  resilience:
    retry:
      enabled: true
      max-attempts: 3                      # Total attempts (including initial)
      wait-duration-millis: 1000           # Initial wait time
      exponential-backoff-multiplier: 2.0  # Exponential increase
```

#### Retry Sequence

```
Attempt 1: Immediate
  ↓ (fails)
Wait 1000ms
  ↓
Attempt 2: After 1s
  ↓ (fails)
Wait 2000ms (1000 * 2.0)
  ↓
Attempt 3: After 2s more
  ↓ (fails)
→ Propagate failure to circuit breaker
```

#### When Retry Helps

✅ **Good candidates for retry:**
- Connection timeouts
- Temporary network failures
- Database connection pool exhaustion
- Lock timeouts

❌ **Bad candidates for retry:**
- Constraint violations
- Schema errors
- Permission denied
- Invalid SQL syntax

#### Monitoring Retries

**Logs:**

```
WARN  i.t.r.RetryableAuditWriter - Retry attempt 1 for audit write after 1000 ms (last exception: Connection timeout)
WARN  i.t.r.RetryableAuditWriter - Retry attempt 2 for audit write after 2000 ms (last exception: Connection timeout)
INFO  i.t.r.RetryableAuditWriter - Audit write succeeded after 2 retries
```

**Metrics:**

```promql
# Retry events
rate(resilience4j_retry_calls_total{name="toranaAuditWriter",kind="retry"}[5m])

# Retry success rate
rate(resilience4j_retry_calls_total{name="toranaAuditWriter",kind="successful_with_retry"}[5m])
```

---

### Circuit Breaker Pattern

**Purpose:** Prevent cascading failures by failing fast when error rate exceeds threshold

**Implementation:** `CircuitBreakerAuditWriter` (Resilience4j)

#### States

```
         ┌──────────┐
         │  CLOSED  │ ← Normal operation
         └────┬─────┘
              │ Failure rate > 50%
              ↓
         ┌──────────┐
         │   OPEN   │ ← Using fallback
         └────┬─────┘
              │ After 60s wait
              ↓
         ┌──────────┐
     ┌──┤HALF_OPEN │ ← Testing recovery
     │  └────┬─────┘
     │       │ Success → Back to CLOSED
     │       │ Failure → Back to OPEN
     └───────┘
```

#### Configuration

```yaml
torana:
  resilience:
    circuit-breaker:
      enabled: true
      failure-rate-threshold: 50              # 50% failures → OPEN
      minimum-number-of-calls: 10             # Need 10 calls before evaluation
      wait-duration-in-open-state-seconds: 60 # Wait 60s before testing recovery
      permitted-number-of-calls-in-half-open-state: 5  # Test with 5 calls
      sliding-window-size: 100                # Evaluate over last 100 calls
```

#### State Transitions

**CLOSED → OPEN:**
```
Conditions:
  - Minimum 10 calls made
  - Failure rate ≥ 50%

Action:
  - All subsequent calls immediately fail fast
  - Fallback writer activated
  - Metrics/logs record state change
```

**OPEN → HALF_OPEN:**
```
Conditions:
  - 60 seconds elapsed in OPEN state

Action:
  - Allow 5 test calls through
  - Monitor their success/failure
```

**HALF_OPEN → CLOSED:**
```
Conditions:
  - Test calls succeed

Action:
  - Resume normal operation
  - Circuit fully recovered
```

**HALF_OPEN → OPEN:**
```
Conditions:
  - Test calls fail

Action:
  - Return to OPEN state
  - Wait another 60 seconds
```

#### Manual Circuit Breaker Operations

**Get current state:**

```java
@Autowired
CircuitBreakerAuditWriter circuitBreakerWriter;

// Get state
CircuitBreaker.State state = circuitBreakerWriter.getState();
// CLOSED, OPEN, HALF_OPEN, DISABLED, FORCED_OPEN

// Get failure rate
float failureRate = circuitBreakerWriter.getFailureRate();
```

**Force circuit open (maintenance mode):**

```java
// Manually open circuit (all writes go to fallback)
circuitBreakerWriter.transitionToOpenState();
```

**Reset circuit:**

```java
// Manually close circuit (force recovery)
circuitBreakerWriter.reset();
```

#### Monitoring Circuit Breaker

**Logs:**

```
WARN  i.t.r.CircuitBreakerAuditWriter - Circuit breaker state transition: CLOSED -> OPEN (failure rate: 65.0%)
DEBUG i.t.r.CircuitBreakerAuditWriter - Circuit breaker OPEN - call not permitted, using fallback
WARN  i.t.r.CircuitBreakerAuditWriter - Circuit breaker state transition: OPEN -> HALF_OPEN (failure rate: 50.0%)
WARN  i.t.r.CircuitBreakerAuditWriter - Circuit breaker state transition: HALF_OPEN -> CLOSED (failure rate: 5.0%)
```

**Metrics:**

```promql
# Current state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
resilience4j_circuitbreaker_state{name="toranaAuditWriter"}

# Failure rate
resilience4j_circuitbreaker_failure_rate{name="toranaAuditWriter"}

# Calls rejected (circuit open)
rate(resilience4j_circuitbreaker_calls_total{name="toranaAuditWriter",kind="not_permitted"}[5m])
```

---

### Fallback Mechanisms

When the circuit breaker opens, audit entries are routed to a fallback writer.

#### Logging Fallback

**Purpose:** Write audit entries to structured logs for temporary storage

**Configuration:**

```yaml
torana:
  resilience:
    fallback:
      enabled: true
      type: logging
```

**Behavior:**
- Writes JSON to dedicated logger
- Log aggregation systems can capture
- No automatic recovery (manual replay required)
- Minimal overhead
- No disk space concerns

**Log Output:**

```
INFO  i.t.r.LoggingFallbackWriter - AUDIT_FALLBACK: {"id":"123e4567-...","action":"order.created","actor_id":"alice",...}
```

**Use Cases:**
- Low-risk applications
- Short database outages
- Log aggregation infrastructure available

---

#### File-Based Fallback

**Purpose:** Durable local storage with automatic recovery

**Configuration:**

```yaml
torana:
  resilience:
    fallback:
      enabled: true
      type: file_based
      file-based-directory: /var/lib/torana/fallback
```

**Behavior:**
- Each entry written as separate JSON file
- Timestamped filenames for ordering
- Survives application restarts
- Automatic recovery via `AuditRecoveryService`
- Requires disk space monitoring

**File Naming:**

```
/var/lib/torana/fallback/
  ├── audit-20260606T103045.123Z-550e8400-e29b-41d4-a716-446655440000.json
  ├── audit-20260606T103046.456Z-660e9511-f30c-52e5-b827-557766551111.json
  └── audit-20260606T103047.789Z-770f0622-0410-63f6-c938-668877662222.json
```

**Directory Requirements:**
- Writable by application user
- Sufficient disk space (monitor usage)
- Not on temporary filesystem (survives reboots)
- Regular backups recommended

**Use Cases:**
- Production environments
- Extended database outages
- Zero data loss requirements
- Automatic recovery desired

---

## Recovery Service

**Purpose:** Automatically replay fallback entries when database recovers

**Implementation:** `AuditRecoveryService` (Spring Scheduled Task)

### Configuration

```yaml
torana:
  resilience:
    recovery:
      enabled: true  # Auto-enabled with file-based fallback
      initial-delay-seconds: 60   # Wait 1 minute before first run
      fixed-delay-seconds: 300    # Run every 5 minutes
      batch-size: 100             # Process 100 entries per batch
```

### Recovery Process

```
┌─────────────────────────────────────┐
│  Scheduled Task (every 5 minutes)   │
└──────────────┬──────────────────────┘
               ↓
         List fallback files
         (sorted chronologically)
               ↓
         ┌─────────────┐
         │ Process     │
         │ Batch 1-100 │
         └──────┬──────┘
                ↓
         Read JSON files
                ↓
         Convert to AuditEntry
                ↓
         writeBatch() to database
                ↓
         ┌────────────────┐
         │ Success?       │
         └───┬────────┬───┘
             │        │
         Yes │        │ No
             ↓        ↓
    Delete files   Leave files
                   (retry next run)
             ↓
         Log statistics
```

### Monitoring Recovery

**Logs:**

```
INFO  i.t.r.AuditRecoveryService - Starting audit recovery: 473 entries pending
DEBUG i.t.r.AuditRecoveryService - Processing recovery batch: files 0 to 99
INFO  i.t.r.AuditRecoveryService - Successfully recovered batch of 100 entries
INFO  i.t.r.AuditRecoveryService - Audit recovery complete: 473 recovered, 0 failed, 0 still pending
```

**Manual Trigger:**

```java
@Autowired
AuditRecoveryService recoveryService;

// Trigger manual recovery
recoveryService.triggerManualRecovery();

// Get pending count
long pending = recoveryService.getPendingCount();
```

**Health Checks:**

```bash
# Count pending fallback files
ls /var/lib/torana/fallback/*.json | wc -l

# Oldest pending entry
ls -lt /var/lib/torana/fallback/*.json | tail -1

# Disk usage
du -sh /var/lib/torana/fallback/
```

---

## Configuration Guide

### Development Environment

```yaml
torana:
  resilience:
    # Retry enabled for transient failures
    retry:
      enabled: true
      max-attempts: 2           # Fewer retries in dev
      wait-duration-millis: 500

    # Circuit breaker disabled (fast failures better for dev)
    circuit-breaker:
      enabled: false

    # Logging fallback (simpler)
    fallback:
      enabled: false  # Let failures propagate for debugging
```

### Staging Environment

```yaml
torana:
  resilience:
    retry:
      enabled: true
      max-attempts: 3
      wait-duration-millis: 1000

    circuit-breaker:
      enabled: true
      failure-rate-threshold: 50

    fallback:
      enabled: true
      type: logging  # Logs sufficient for staging
```

### Production Environment

```yaml
torana:
  resilience:
    retry:
      enabled: true
      max-attempts: 3
      wait-duration-millis: 1000
      exponential-backoff-multiplier: 2.0

    circuit-breaker:
      enabled: true
      failure-rate-threshold: 50
      minimum-number-of-calls: 10
      wait-duration-in-open-state-seconds: 60
      permitted-number-of-calls-in-half-open-state: 5
      sliding-window-size: 100

    fallback:
      enabled: true
      type: file_based
      file-based-directory: /var/lib/torana/fallback

    recovery:
      initial-delay-seconds: 60
      fixed-delay-seconds: 300  # 5 minutes
      batch-size: 100
```

---

## Operational Procedures

### Handling Circuit Breaker Events

#### Circuit Opens

**Alert:** Circuit breaker OPEN for > 5 minutes

**Immediate Actions:**

1. **Check database health:**
   ```bash
   psql -h prod-db -U app_user -d app_db -c "SELECT 1;"
   ```

2. **Review application logs:**
   ```bash
   kubectl logs -f deployment/app --tail=200 | grep -i "circuit\|torana"
   ```

3. **Check fallback:**
   ```bash
   # Logging fallback
   kubectl logs -f deployment/app | grep "AUDIT_FALLBACK"

   # File-based fallback
   ls -lh /var/lib/torana/fallback/
   ```

4. **Monitor automatic recovery:**
   - Circuit will test recovery after 60s
   - Watch for state transitions in logs

**Resolution:**
- Fix database issue
- Circuit automatically recovers
- Recovery service replays fallback entries

---

#### Manual Circuit Recovery

If automatic recovery is slow or circuit is stuck:

```bash
# Option 1: Restart application (circuit resets)
kubectl rollout restart deployment/app

# Option 2: Manual reset via API (requires custom endpoint)
curl -X POST http://app:8080/actuator/torana/circuit-breaker/reset
```

---

### Fallback Directory Management

#### Monitoring Disk Usage

**Alert:** Fallback directory > 80% capacity

**Check usage:**

```bash
df -h /var/lib/torana/fallback
du -sh /var/lib/torana/fallback
find /var/lib/torana/fallback -name "*.json" | wc -l
```

**If disk full:**

1. **Check recovery service is running:**
   ```bash
   kubectl logs deployment/app | grep AuditRecoveryService
   ```

2. **Trigger manual recovery:**
   ```bash
   # Via application restart (triggers immediate recovery)
   kubectl rollout restart deployment/app
   ```

3. **Emergency: Archive old entries:**
   ```bash
   # Backup files
   tar -czf fallback-backup-$(date +%Y%m%d).tar.gz /var/lib/torana/fallback/*.json

   # Move to archive location
   mv fallback-backup-*.tar.gz /mnt/archive/

   # Clear fallback (only if database is healthy!)
   rm /var/lib/torana/fallback/*.json
   ```

---

#### Recovery Service Not Running

**Symptoms:**
- Fallback files accumulating
- No "AuditRecoveryService" logs

**Check scheduling:**

```yaml
# Ensure scheduling is enabled
spring:
  scheduling:
    enabled: true

# Or via annotation (should be auto-enabled)
@EnableScheduling
```

**Verify service bean:**

```bash
# Check Spring context
curl http://app:8080/actuator/beans | jq '.contexts.application.beans | keys' | grep -i recovery
```

---

## Monitoring Resilience

### Key Metrics to Monitor

| Metric | Threshold | Action |
|--------|-----------|--------|
| Circuit breaker state | OPEN | Investigate database issues |
| Failure rate | > 10% | Check logs for error patterns |
| Fallback file count | > 1000 | Check recovery service |
| Fallback directory size | > 1GB | Trigger manual recovery |
| Recovery success rate | < 90% | Investigate database connectivity |

### Grafana Panels

```json
{
  "panels": [
    {
      "title": "Circuit Breaker State",
      "targets": [{
        "expr": "resilience4j_circuitbreaker_state{name=\"toranaAuditWriter\"}"
      }],
      "valueMappings": [
        {"value": 0, "text": "CLOSED"},
        {"value": 1, "text": "OPEN"},
        {"value": 2, "text": "HALF_OPEN"}
      ]
    }
  ]
}
```

---

## Best Practices

### 1. Always Enable Resilience in Production

```yaml
# CRITICAL: Always use circuit breaker + fallback in production
torana:
  resilience:
    circuit-breaker:
      enabled: true
    fallback:
      enabled: true
      type: file_based
```

### 2. Monitor Fallback Directory Size

```yaml
# Prometheus alert
- alert: ToranaFallbackDirectoryLarge
  expr: node_filesystem_size_bytes{mountpoint="/var/lib/torana/fallback"} > 1073741824
  for: 30m
```

### 3. Test Recovery Process

```bash
# Simulate database failure
# 1. Stop database
# 2. Trigger some audit events
# 3. Verify fallback files created
# 4. Restart database
# 5. Verify automatic recovery
```

### 4. Tune Circuit Breaker for Your SLA

```yaml
# High-availability (fail fast)
failure-rate-threshold: 30
wait-duration-in-open-state-seconds: 30

# Stability (tolerate more failures)
failure-rate-threshold: 70
wait-duration-in-open-state-seconds: 120
```

### 5. Backup Fallback Directory

```bash
# Daily backup via cron
0 2 * * * tar -czf /backup/torana-fallback-$(date +\%Y\%m\%d).tar.gz /var/lib/torana/fallback/
```

---

**Related Documentation:**
- [Operational Runbook](operational-runbook.md)
- [Troubleshooting Guide](troubleshooting-guide.md)
- [Monitoring & Metrics](monitoring-and-metrics.md)
