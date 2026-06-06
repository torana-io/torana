# Torana Troubleshooting Guide

**Version:** 0.2.0
**Last Updated:** 2026-06-06
**Audience:** Operations, SRE, DevOps teams

This guide provides step-by-step troubleshooting procedures for common issues with the Torana audit trail system.

---

## Table of Contents

- [Quick Diagnostics](#quick-diagnostics)
- [Common Issues](#common-issues)
  - [Audit Entries Not Appearing](#audit-entries-not-appearing)
  - [High Latency Issues](#high-latency-issues)
  - [Health Check Failing](#health-check-failing)
  - [Performance Issues](#performance-issues)
  - [Circuit Breaker Issues](#circuit-breaker-issues)
  - [Configuration Issues](#configuration-issues)
- [Error Message Reference](#error-message-reference)
- [Diagnostic Commands](#diagnostic-commands)
- [Escalation](#escalation)

---

## Quick Diagnostics

Run these commands first to gather initial diagnostic information:

```bash
# 1. Check application health
curl -s https://app.example.com/actuator/health/auditTrail | jq .

# 2. Check recent audit entries
psql -h prod-db -U app_user -d app_db -c \
  "SELECT action, outcome, occurred_at FROM audit_entries ORDER BY occurred_at DESC LIMIT 10;"

# 3. Check application logs for errors
kubectl logs -f deployment/app --tail=100 | grep -i "torana\|audit"

# 4. Check metrics
curl -s https://app.example.com/actuator/prometheus | grep torana_audit

# 5. Check database connectivity
psql -h prod-db -U app_user -d app_db -c "SELECT 1;"
```

---

## Common Issues

### Audit Entries Not Appearing

**Symptoms:**
- Business actions execute successfully but no audit entries in database
- No errors in application logs
- Health check shows UP

**Diagnostic Steps:**

1. **Verify Torana is enabled:**
   ```bash
   # Check application.yml or environment variables
   grep -i "torana.enabled" application*.yml
   ```

   Expected: `torana.enabled: true`

2. **Check if method has @AuditedAction annotation:**
   ```bash
   # Search for the method in source code
   grep -r "@AuditedAction\|@AuditedCreate\|@AuditedUpdate\|@AuditedDelete" src/
   ```

3. **Verify Spring AOP is enabled:**
   ```bash
   # Check logs for Spring AOP initialization
   kubectl logs deployment/app | grep -i "aop\|aspect"
   ```

   Expected: `Creating instance of bean 'auditedActionAspect'`

4. **Check transaction configuration:**
   ```yaml
   torana:
     transaction:
       success-write-policy: after_commit  # Change to 'immediate' for testing
   ```

   If set to `after_commit`, audit entries only appear after transaction commits.

5. **Enable debug logging:**
   ```yaml
   logging:
     level:
       io.torana: DEBUG
   ```

   Restart application and look for:
   ```
   DEBUG i.t.core.AuditPipeline - Processing audit entry: action=order.created
   DEBUG i.t.core.AuditPipeline - Audit entry persisted: id=123
   ```

6. **Check database table exists:**
   ```sql
   -- PostgreSQL
   SELECT * FROM information_schema.tables WHERE table_name = 'audit_entries';

   -- MySQL
   SHOW TABLES LIKE 'audit_entries';
   ```

7. **Check database permissions:**
   ```sql
   -- PostgreSQL
   SELECT has_table_privilege('app_user', 'audit_entries', 'INSERT');

   -- MySQL
   SHOW GRANTS FOR 'app_user'@'%';
   ```

**Common Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| `torana.enabled=false` | Set to `true` in configuration |
| Missing `@AuditedAction` annotation | Add annotation to business method |
| Transaction rollback | Check business logic for exceptions |
| Database permissions | Grant INSERT on audit_entries table |
| Schema not created | Run migrations or set `torana.schema-mode: create` (dev only) |
| AOP not triggered (final methods, internal calls) | Refactor to use Spring proxies correctly |

---

### High Latency Issues

**Symptoms:**
- Audit write p95 latency > 500ms
- Business operations slower than expected
- Metrics show `torana.audit.write.latency` increasing

**Diagnostic Steps:**

1. **Check current latency metrics:**
   ```bash
   curl -s http://localhost:8080/actuator/prometheus | \
     grep torana_audit_write_latency_seconds
   ```

2. **Check database performance:**
   ```sql
   -- PostgreSQL: Check slow queries
   SELECT query, mean_exec_time, calls
   FROM pg_stat_statements
   WHERE query LIKE '%audit_entries%'
   ORDER BY mean_exec_time DESC
   LIMIT 10;

   -- Check table bloat
   SELECT pg_size_pretty(pg_total_relation_size('audit_entries')) AS total_size,
          pg_size_pretty(pg_relation_size('audit_entries')) AS table_size,
          pg_size_pretty(pg_indexes_size('audit_entries')) AS indexes_size;
   ```

3. **Check database connection pool:**
   ```yaml
   spring:
     datasource:
       hikari:
         maximum-pool-size: 20  # Increase if needed
         connection-timeout: 5000
   ```

4. **Profile snapshot creation:**
   ```yaml
   torana:
     snapshot:
       max-depth: 3  # Reduce to 2 or 1 if deep object graphs are slow
   ```

5. **Check for lock contention:**
   ```sql
   -- PostgreSQL: Check locks on audit_entries
   SELECT * FROM pg_locks WHERE relation = 'audit_entries'::regclass;
   ```

**Common Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| Database overloaded | Scale database, add read replicas |
| Missing indexes | Verify indexes exist (see schema-management.md) |
| Connection pool exhausted | Increase `maximum-pool-size` |
| Deep snapshot serialization | Reduce `snapshot.max-depth` or exclude expensive fields |
| Table bloat (PostgreSQL) | Run `VACUUM ANALYZE audit_entries` |
| Network latency | Move application closer to database |
| Large metadata payloads | Reduce metadata size, use references instead |

**Immediate Mitigation:**

```yaml
# Temporary: Write async (separate transaction)
torana:
  transaction:
    success-write-policy: requires_new  # Decouples from parent transaction
```

**Performance Tuning:**

```yaml
# Disable detailed tags to reduce cardinality
torana:
  metrics:
    include-detailed-tags: false

# Reduce snapshot depth
torana:
  snapshot:
    max-depth: 2
```

---

### Health Check Failing

**Symptoms:**
- `/actuator/health/auditTrail` returns `DOWN` or `WARNING`
- Alerts firing for health check failures

**Diagnostic Steps:**

1. **Check health endpoint directly:**
   ```bash
   curl -s http://localhost:8080/actuator/health/auditTrail | jq .
   ```

   Example output:
   ```json
   {
     "status": "DOWN",
     "details": {
       "database": "PostgreSQL",
       "tableName": "audit_entries",
       "error": "Failed to query audit table: Table 'audit_entries' doesn't exist"
     }
   }
   ```

2. **Verify database connectivity:**
   ```bash
   psql -h prod-db -U app_user -d app_db -c "SELECT 1;"
   ```

3. **Check audit table exists:**
   ```sql
   SELECT COUNT(*) FROM audit_entries;
   ```

4. **Check error rate (if status is WARNING):**
   ```json
   {
     "status": "WARNING",
     "details": {
       "totalWrites": 100,
       "totalErrors": 15,
       "errorRate": "15.00%",
       "errorRateThreshold": "10.00%",
       "message": "Error rate exceeds threshold"
     }
   }
   ```

**Common Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| Database down | Restart database, check connectivity |
| Table not created | Run Flyway migrations |
| Permissions issue | Grant SELECT on audit_entries |
| High error rate | Investigate root cause of write failures (see logs) |
| Network partition | Check network connectivity to database |

**Resolution for WARNING Status:**

```bash
# 1. Check application logs for write errors
kubectl logs deployment/app --tail=200 | grep -i "error.*audit"

# 2. Common error: Database connection timeout
# Solution: Check database load, connection pool

# 3. Common error: Constraint violation
# Solution: Check for schema mismatches, data validation issues

# 4. Wait for health check window to reset (default 60s)
# Error counters reset automatically after configured window
```

---

### Performance Issues

**Symptoms:**
- Application throughput degraded
- Increased response times for business operations
- CPU or memory usage spikes

**Diagnostic Steps:**

1. **Check if metrics are enabled:**
   ```yaml
   torana:
     metrics:
       enabled: true
       include-detailed-tags: false  # IMPORTANT: Should be false in production
   ```

   **Issue:** `include-detailed-tags: true` can cause high cardinality metrics explosion.

2. **Profile metrics overhead:**
   ```bash
   # Compare latency with and without metrics
   # 1. Disable metrics temporarily
   torana.metrics.enabled=false

   # 2. Restart and measure baseline
   # 3. Re-enable metrics and compare
   ```

3. **Check snapshot serialization:**
   ```bash
   # Enable profiling logs
   logging.level.io.torana.core.snapshot: DEBUG
   ```

   Look for slow serialization:
   ```
   DEBUG i.t.c.s.SnapshotSerializer - Serialized entity in 250ms (depth=5, size=50KB)
   ```

4. **Check database write latency:**
   ```sql
   -- PostgreSQL: Enable query logging temporarily
   ALTER SYSTEM SET log_min_duration_statement = 100;  -- Log queries > 100ms
   SELECT pg_reload_conf();
   ```

**Common Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| High-cardinality metrics | Set `include-detailed-tags: false` |
| Deep object graphs | Reduce `snapshot.max-depth` or use `@JsonIgnore` |
| Synchronous writes blocking | Use `success-write-policy: requires_new` |
| Excessive redaction patterns | Optimize regex patterns, reduce pattern count |
| Large metadata | Reduce metadata size, use compact formats |

**Quick Fix:**

```yaml
# Most aggressive performance optimization
torana:
  metrics:
    enabled: false  # Disable metrics temporarily
  snapshot:
    enabled: false  # Disable snapshots if not needed
  transaction:
    success-write-policy: requires_new  # Async write
```

---

### Circuit Breaker Issues

**Symptoms:**
- Circuit breaker state is OPEN
- Fallback mechanism activated
- Audit entries written to fallback (logs or files)

**Prerequisites:**
- Circuit breaker must be enabled in configuration:
  ```yaml
  torana:
    resilience:
      circuit-breaker:
        enabled: true
  ```

**Diagnostic Steps:**

1. **Check circuit breaker state:**
   ```bash
   curl -s http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state | jq .
   ```

   States:
   - `0` = CLOSED (normal)
   - `1` = OPEN (failing, using fallback)
   - `2` = HALF_OPEN (testing recovery)

2. **Check circuit breaker metrics:**
   ```bash
   curl -s http://localhost:8080/actuator/prometheus | grep resilience4j_circuitbreaker
   ```

3. **Check fallback mechanism:**

   **Logging fallback:**
   ```bash
   # Check for fallback log entries
   kubectl logs deployment/app | grep -i "fallback\|circuit.*open"
   ```

   Expected:
   ```
   WARN  i.t.r.CircuitBreakerAuditWriter - Circuit breaker OPEN, using fallback
   INFO  i.t.r.LoggingFallbackWriter - Fallback audit: {"action":"order.created",...}
   ```

   **File-based fallback:**
   ```bash
   # Check fallback directory
   ls -lh /var/lib/torana/fallback/

   # Count pending entries
   find /var/lib/torana/fallback -name "*.json" | wc -l
   ```

4. **Check database availability:**
   ```bash
   psql -h prod-db -U app_user -d app_db -c "SELECT 1;"
   ```

**Common Causes & Solutions:**

| Cause | Solution |
|-------|----------|
| Database down | Restore database, circuit will auto-recover |
| High database latency | Optimize queries, scale database |
| Network issues | Check connectivity, firewall rules |
| Connection pool exhausted | Increase `maximum-pool-size` |
| Disk full (database) | Free disk space |

**Recovery Procedure:**

1. **Fix root cause** (database connectivity, performance issue)

2. **Verify database is healthy:**
   ```bash
   psql -h prod-db -U app_user -d app_db -c \
     "INSERT INTO audit_entries (id, action, outcome, occurred_at) \
      VALUES (gen_random_uuid(), 'test.action', 'success', NOW());"
   ```

3. **Circuit breaker will automatically transition:**
   ```
   OPEN (60s wait) → HALF_OPEN (test calls) → CLOSED (recovered)
   ```

4. **Monitor recovery:**
   ```bash
   # Watch circuit breaker state
   watch -n 5 'curl -s http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state'
   ```

5. **Verify fallback entries are recovered** (if using file-based fallback):
   ```bash
   # AuditRecoveryService runs every 5 minutes (default)
   # Check logs for recovery activity
   kubectl logs deployment/app | grep -i "recovery\|replaying"
   ```

   Expected:
   ```
   INFO  i.t.r.AuditRecoveryService - Starting audit recovery: 47 entries pending
   INFO  i.t.r.AuditRecoveryService - Replayed 47 entries successfully
   ```

**Manual Recovery (if automatic recovery fails):**

```bash
# 1. List fallback files
ls /var/lib/torana/fallback/*.json

# 2. Manually insert entries (example script)
for file in /var/lib/torana/fallback/*.json; do
  psql -h prod-db -U app_user -d app_db -c \
    "INSERT INTO audit_entries SELECT * FROM json_populate_record(NULL::audit_entries, '$(cat $file)');"
  rm $file
done
```

**Tuning Circuit Breaker:**

```yaml
torana:
  resilience:
    circuit-breaker:
      failure-rate-threshold: 50  # Open circuit if >50% failures
      minimum-number-of-calls: 10  # Need at least 10 calls before evaluating
      wait-duration-in-open-state-seconds: 60  # Wait 60s before testing recovery
      permitted-number-of-calls-in-half-open-state: 5  # Test with 5 calls
      sliding-window-size: 100  # Evaluate over last 100 calls
```

---

### Configuration Issues

**Symptoms:**
- Application fails to start
- Unexpected audit behavior
- Configuration not taking effect

**Diagnostic Steps:**

1. **Validate YAML syntax:**
   ```bash
   yamllint application.yml
   ```

2. **Check effective configuration:**
   ```bash
   # Spring Boot Actuator exposes configuration
   curl -s http://localhost:8080/actuator/configprops | jq '.contexts.application.beans.toranaProperties'
   ```

3. **Check for configuration warnings in logs:**
   ```bash
   kubectl logs deployment/app | grep -i "warn.*torana\|configuration"
   ```

**Common Configuration Errors:**

| Error | Cause | Solution |
|-------|-------|----------|
| `torana.enabled=true` but audit not working | Schema not created | Set `schema-mode: create` (dev) or run migrations |
| Health check always DOWN | Missing actuator dependency | Add `spring-boot-starter-actuator` |
| Metrics not visible | Micrometer not configured | Add `micrometer-registry-prometheus` |
| Circuit breaker not working | Missing Resilience4j dependency | Add `resilience4j-spring-boot3` |
| Transaction policy ignored | Incorrect property name | Use `success-write-policy`, not `successWritePolicy` |

**Configuration Validation Checklist:**

```yaml
# Minimal production configuration
torana:
  enabled: true  # REQUIRED
  table-name: audit_entries
  schema-mode: none  # Use Flyway in production

  transaction:
    success-write-policy: after_commit  # Recommended
    failure-write-policy: requires_new
    audit-error-policy: log_and_continue  # Or 'callback' with custom handler

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/app_db  # REQUIRED
    username: app_user
    password: ${DB_PASSWORD}
```

---

## Error Message Reference

### Database Errors

**Error:** `Table 'audit_entries' doesn't exist`
- **Cause:** Schema not created
- **Solution:** Run Flyway migrations or set `schema-mode: create` (dev only)

**Error:** `Permission denied for table audit_entries`
- **Cause:** Database user lacks INSERT/SELECT permissions
- **Solution:**
  ```sql
  GRANT INSERT, SELECT ON audit_entries TO app_user;
  ```

**Error:** `Connection refused` / `Connection timeout`
- **Cause:** Database not reachable
- **Solution:** Check database is running, verify network connectivity, check firewall rules

**Error:** `Connection pool exhausted`
- **Cause:** Too many concurrent database operations
- **Solution:** Increase `spring.datasource.hikari.maximum-pool-size`

### Application Errors

**Error:** `AuditedActionAspect not found` / `No bean of type AuditWriter`
- **Cause:** Spring Boot auto-configuration not triggered
- **Solution:** Ensure `torana-spring-boot-starter` is in dependencies

**Error:** `Circular dependency detected`
- **Cause:** Custom error handler or resolver misconfigured
- **Solution:** Check custom beans don't inject `AuditTrail` or `AuditWriter`

**Error:** `Failed to serialize snapshot`
- **Cause:** Entity has circular references or is not serializable
- **Solution:** Add `@JsonIgnore` to circular reference fields or reduce `snapshot.max-depth`

### Configuration Errors

**Error:** `Property 'torana.enabled' is invalid`
- **Cause:** Typo in property name
- **Solution:** Verify property name matches documented configuration

**Error:** `Could not resolve placeholder 'torana.table-name'`
- **Cause:** Property not defined
- **Solution:** Add property to application.yml or use default

---

## Diagnostic Commands

### Database Queries

```sql
-- Recent audit entries
SELECT action, outcome, occurred_at, actor_id, target_type, target_id
FROM audit_entries
ORDER BY occurred_at DESC
LIMIT 20;

-- Audit entries by actor
SELECT actor_id, COUNT(*) as action_count
FROM audit_entries
WHERE occurred_at > NOW() - INTERVAL '24 hours'
GROUP BY actor_id
ORDER BY action_count DESC;

-- Error rate (entries with errors)
SELECT
  COUNT(CASE WHEN outcome = 'failure' THEN 1 END) as errors,
  COUNT(*) as total,
  ROUND(100.0 * COUNT(CASE WHEN outcome = 'failure' THEN 1 END) / COUNT(*), 2) as error_rate_pct
FROM audit_entries
WHERE occurred_at > NOW() - INTERVAL '1 hour';

-- Table size and growth
SELECT
  pg_size_pretty(pg_total_relation_size('audit_entries')) as total_size,
  pg_size_pretty(pg_relation_size('audit_entries')) as table_size,
  pg_size_pretty(pg_indexes_size('audit_entries')) as index_size,
  (SELECT COUNT(*) FROM audit_entries) as row_count;

-- Slow queries (PostgreSQL)
SELECT query, mean_exec_time, calls, total_exec_time
FROM pg_stat_statements
WHERE query LIKE '%audit_entries%'
ORDER BY mean_exec_time DESC
LIMIT 10;
```

### Application Logs

```bash
# Error logs
kubectl logs deployment/app --tail=500 | grep -i "error.*torana\|error.*audit"

# Audit activity logs
kubectl logs deployment/app --tail=200 | grep -i "audit entry\|audited action"

# Circuit breaker state changes
kubectl logs deployment/app | grep -i "circuit.*transition\|circuit.*open\|circuit.*closed"

# Performance warnings
kubectl logs deployment/app | grep -i "slow\|latency\|timeout"
```

### Metrics Queries (Prometheus)

```promql
# Write latency p95
histogram_quantile(0.95, rate(torana_audit_write_latency_seconds_bucket[5m]))

# Error rate
rate(torana_audit_write_error_total[5m]) / rate(torana_audit_write_success_total[5m])

# Throughput (writes per second)
rate(torana_audit_write_success_total[5m])

# Circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
resilience4j_circuitbreaker_state{name="toranaAuditWriter"}
```

---

## Escalation

### When to Escalate

**Immediate Escalation (P1):**
- Health check DOWN for > 5 minutes
- Error rate > 50%
- Circuit breaker OPEN for > 30 minutes with no recovery
- Data loss suspected

**Escalation within 1 hour (P2):**
- Error rate 10-50%
- Latency p95 > 1 second
- Fallback directory growing unbounded
- Configuration issues blocking deployment

**Escalation during business hours (P3):**
- Error rate 5-10%
- Latency p95 > 500ms
- Health check intermittently WARNING
- Questions about configuration or behavior

### Escalation Path

1. **Level 1:** Oncall engineer (use runbook procedures)
2. **Level 2:** Development team lead (if runbook insufficient)
3. **Level 3:** Platform engineering lead (architectural issues)
4. **Level 4:** CTO (critical production impact)

### Information to Gather Before Escalating

1. **Symptoms:**
   - What is failing? (health check, writes, performance)
   - When did it start?
   - What changed recently? (deployment, config, database)

2. **Environment:**
   - Environment name (production, staging)
   - Application version
   - Database type and version

3. **Diagnostics:**
   - Health check output
   - Recent error logs (last 100 lines)
   - Metrics snapshot (latency, error rate)
   - Database connectivity test results

4. **Impact:**
   - Business operations affected?
   - Number of users impacted?
   - Workarounds in place?

---

**Document Maintenance:**
- Review quarterly
- Update after major incidents
- Validate procedures in staging

**Feedback:**
- Report issues: https://github.com/torana-io/torana/issues
- Suggest improvements: Pull requests welcome
