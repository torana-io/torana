# Torana Operational Runbook

**Version:** 0.2.0
**Last Updated:** 2026-06-06
**Audience:** Operations, SRE, DevOps teams

This runbook provides operational procedures for deploying, monitoring, and maintaining Torana audit trail in production environments.

---

## Table of Contents

- [Quick Reference](#quick-reference)
- [System Architecture](#system-architecture)
- [Deployment](#deployment)
- [Monitoring & Alerting](#monitoring--alerting)
- [Troubleshooting](#troubleshooting)
- [Maintenance](#maintenance)
- [Disaster Recovery](#disaster-recovery)
- [Escalation](#escalation)

---

## Quick Reference

### Emergency Contacts

- **Primary:** Development team via incident management system
- **Secondary:** Database team for audit table issues
- **Escalation:** Platform engineering lead

### Critical Metrics

| Metric | Threshold | Action |
|--------|-----------|--------|
| `torana.audit.write.error` rate | > 10% | Investigate database connectivity |
| `torana.audit.write.latency` p95 | > 500ms | Check database performance |
| Health check status | DOWN | Alert oncall, check database |
| Disk space (`/var/lib/torana/fallback`) | > 80% | Circuit breaker may be open |

### Common Alert Resolutions

| Alert | Immediate Action | Documentation |
|-------|-----------------|---------------|
| High Error Rate | Check database connectivity, review logs | [Troubleshooting](#audit-entries-not-appearing) |
| Health Check Failing | Verify database connection, check table existence | [Health Check Issues](#health-check-issues) |
| High Latency | Check database load, review slow query log | [Performance Issues](#performance-issues) |
| Circuit Breaker Open | Check fallback directory, verify recovery service | [Circuit Breaker](#circuit-breaker-issues) |

---

## System Architecture

### Components

```
Application Layer
    ↓
@AuditedAction Aspect (Spring AOP)
    ↓
AuditPipeline
    ├→ Context Collection (Actor, Tenant, Request, Trace)
    ├→ Entry Creation (Snapshots, Metadata)
    ├→ Redaction (PII removal)
    └→ Persistence
        ↓
TransactionAwareWriter (Spring Transaction)
    ↓
MetricsAuditWriter (Micrometer - optional)
    ↓
CircuitBreakerAuditWriter (Resilience4j - optional)
    ↓
JdbcAuditWriter
    ↓
Database (PostgreSQL / MySQL / H2)
```

### Data Flow

1. Business method with `@AuditedAction` executes
2. AOP aspect intercepts method invocation
3. Context collected from resolvers (actor, tenant, request, trace)
4. Audit entry created with metadata and optional snapshots
5. Sensitive data redacted based on configured patterns
6. Entry persisted to database (timing controlled by transaction policy)
7. Metrics recorded (if enabled)

### Dependencies

- **Database:** PostgreSQL 12+, MySQL 8+, or H2 (dev only)
- **Spring Boot:** 4.0.5+
- **Java:** 21+
- **Optional:** Micrometer (metrics), Resilience4j (circuit breaker)

---

## Deployment

### Pre-Deployment Checklist

- [ ] Database migrations applied (Flyway/Liquibase)
- [ ] `audit_entries` table exists with correct schema version
- [ ] Database user has INSERT, SELECT permissions on audit_entries
- [ ] Configuration reviewed and validated
- [ ] Metrics dashboards created (Grafana)
- [ ] Health check endpoints configured
- [ ] Alerts configured (Prometheus/CloudWatch)
- [ ] Fallback directory exists (if using file-based fallback)

### Environment-Specific Configuration

#### Development

```yaml
torana:
  enabled: true
  schema-mode: create  # Auto-create table

  transaction:
    success-write-policy: immediate
    failure-write-policy: immediate
    audit-error-policy: log_and_continue

  metrics:
    enabled: true
    include-detailed-tags: true  # OK for dev
```

#### Staging

```yaml
torana:
  enabled: true
  schema-mode: none  # Use Flyway

  transaction:
    success-write-policy: after_commit
    failure-write-policy: requires_new
    audit-error-policy: log_and_continue

  metrics:
    enabled: true
    include-detailed-tags: false
    error-rate-threshold: 0.1

  resilience:
    circuit-breaker:
      enabled: true
      failure-rate-threshold: 50
    fallback:
      enabled: true
      type: logging
```

#### Production

```yaml
spring:
  datasource:
    url: jdbc:postgresql://prod-db:5432/app_db
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 20
      connection-timeout: 5000

torana:
  enabled: true
  table-name: audit_entries
  schema-mode: none  # CRITICAL: Use Flyway only

  transaction:
    success-write-policy: after_commit  # Prevents orphaned records
    failure-write-policy: requires_new  # Survives parent rollback
    audit-error-policy: callback  # Custom handler

  redaction:
    enabled: true
    patterns:
      - "(?i)password"
      - "(?i)secret"
      - "(?i)token"
      - "(?i)ssn"
      - "(?i)credit.?card"

  snapshot:
    max-depth: 3

  metrics:
    enabled: true
    include-detailed-tags: false  # Prevents cardinality explosion
    health-check-window-seconds: 60
    error-rate-threshold: 0.05  # 5% threshold for production

  resilience:
    circuit-breaker:
      enabled: true
      failure-rate-threshold: 50
      minimum-number-of-calls: 10
      wait-duration-in-open-state-seconds: 60

    retry:
      enabled: true
      max-attempts: 3
      wait-duration-millis: 1000

    fallback:
      enabled: true
      type: file_based
      file-based-directory: /var/lib/torana/fallback

management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus
  health:
    audit-trail:
      enabled: true
  metrics:
    export:
      prometheus:
        enabled: true

logging:
  level:
    io.torana: INFO
    io.torana.resilience: DEBUG  # For circuit breaker monitoring
```

### Deployment Steps

#### 1. Database Preparation

```bash
# Verify database connectivity
psql -h prod-db -U app_user -d app_db -c "SELECT 1;"

# Apply migrations (Flyway example)
./gradlew flywayMigrate -Pflyway.url=jdbc:postgresql://prod-db:5432/app_db

# Verify audit table exists
psql -h prod-db -U app_user -d app_db -c "\d audit_entries"

# Check indexes
psql -h prod-db -U app_user -d app_db -c "\di audit_entries*"
```

#### 2. Configuration Validation

```bash
# Validate YAML syntax
yamllint application-production.yml

# Test database connection with configuration
java -jar app.jar --spring.config.location=application-production.yml \
  --spring.profiles.active=production --dry-run
```

#### 3. Create Fallback Directory (if using file-based fallback)

```bash
# Create directory
sudo mkdir -p /var/lib/torana/fallback

# Set ownership
sudo chown app-user:app-group /var/lib/torana/fallback

# Set permissions
sudo chmod 755 /var/lib/torana/fallback

# Verify
ls -ld /var/lib/torana/fallback
```

#### 4. Deploy Application

```bash
# Blue-green deployment example
kubectl apply -f kubernetes/torana-deployment-v2.yaml

# Verify deployment
kubectl rollout status deployment/app-deployment

# Check health
curl https://app.example.com/actuator/health/auditTrail
```

#### 5. Post-Deployment Validation

```bash
# Check application logs
kubectl logs -f deployment/app-deployment | grep -i torana

# Verify metrics endpoint
curl https://app.example.com/actuator/prometheus | grep torana_audit

# Test audit write (trigger business action)
# Monitor for audit entry in database
psql -h prod-db -U app_user -d app_db -c \
  "SELECT * FROM audit_entries ORDER BY occurred_at DESC LIMIT 5;"
```

### Rollback Procedure

```bash
# 1. Identify last known good version
kubectl rollout history deployment/app-deployment

# 2. Rollback
kubectl rollout undo deployment/app-deployment

# 3. Verify rollback
kubectl rollout status deployment/app-deployment

# 4. Check health
curl https://app.example.com/actuator/health
```

**Note:** Audit data is append-only. Rollback does not affect existing audit entries.

---

## Monitoring & Alerting

### Critical Metrics to Monitor

#### 1. Write Latency

**Metric:** `torana.audit.write.latency` (Timer)

- **p50:** Should be < 50ms
- **p95:** Should be < 200ms
- **p99:** Should be < 500ms

**Alert Rule:**
```yaml
- alert: ToranaHighWriteLatency
  expr: histogram_quantile(0.95, torana_audit_write_latency_seconds) > 0.5
  for: 10m
  annotations:
    summary: "Audit write p95 latency exceeds 500ms"
    description: "p95 latency: {{ $value }}s"
```

#### 2. Error Rate

**Metric:** `torana.audit.write.error` (Counter)

- **Threshold:** < 5% error rate
- **Critical:** > 10% error rate

**Alert Rule:**
```yaml
- alert: ToranaHighErrorRate
  expr: rate(torana_audit_write_error_total[5m]) / rate(torana_audit_write_success_total[5m]) > 0.1
  for: 5m
  annotations:
    summary: "Audit error rate exceeds 10%"
```

#### 3. Health Check Status

**Endpoint:** `/actuator/health/auditTrail`

- **Expected:** `{"status":"UP"}`
- **Warning:** `{"status":"WARNING"}`
- **Critical:** `{"status":"DOWN"}`

**Alert Rule:**
```yaml
- alert: ToranaHealthCheckDown
  expr: up{job="spring-boot-app", endpoint="health/auditTrail"} == 0
  for: 2m
  annotations:
    summary: "Torana health check is DOWN"
```

#### 4. Circuit Breaker State (if enabled)

**Metrics:**
- `resilience4j_circuitbreaker_state` (Gauge)
- `resilience4j_circuitbreaker_failure_rate` (Gauge)

**Alert Rule:**
```yaml
- alert: ToranaCircuitBreakerOpen
  expr: resilience4j_circuitbreaker_state{name="toranaAuditWriter"} == 1
  for: 5m
  annotations:
    summary: "Torana circuit breaker is OPEN"
    description: "Audit writes are failing to fallback"
```

### Grafana Dashboard

Import the dashboard from `docs/grafana-dashboard.json` (see Sprint 4).

Key panels:
- Write throughput (ops/sec)
- Write latency percentiles
- Error rate
- Circuit breaker state
- Health check status
- Fallback directory size

### Log Monitoring

**Key log patterns:**

```
# Success
INFO  i.t.core.AuditPipeline - Audit entry persisted: action=order.created, target=Order:123

# Warning
WARN  i.t.resilience.CircuitBreakerAuditWriter - Circuit breaker transitioning to OPEN state

# Error
ERROR i.t.jdbc.JdbcAuditWriter - Failed to write audit entry: java.sql.SQLException
```

**Alert on patterns:**
- `ERROR.*AuditWriter` - Database write failures
- `Circuit breaker.*OPEN` - Circuit opened
- `Fallback.*file.*full` - Fallback directory full

---

## Troubleshooting

See [troubleshooting-guide.md](troubleshooting-guide.md) for detailed troubleshooting procedures.

### Quick Diagnostics

```bash
# Check health endpoint
curl -s https://app.example.com/actuator/health/auditTrail | jq .

# Check recent audit entries
psql -c "SELECT action, outcome, occurred_at FROM audit_entries ORDER BY occurred_at DESC LIMIT 10;"

# Check application logs
kubectl logs -f deployment/app --tail=100 | grep -i audit

# Check metrics
curl -s https://app.example.com/actuator/prometheus | grep torana_audit
```

---

## Maintenance

### Regular Maintenance Tasks

#### Weekly

- **Monitor table growth**
  ```sql
  SELECT pg_size_pretty(pg_total_relation_size('audit_entries')) AS total_size;
  ```

- **Review error logs**
  ```bash
  kubectl logs deployment/app --since=7d | grep -i "ERROR.*torana" | wc -l
  ```

#### Monthly

- **Analyze query performance**
  ```sql
  -- Check for slow queries
  SELECT query, mean_exec_time, calls
  FROM pg_stat_statements
  WHERE query LIKE '%audit_entries%'
  ORDER BY mean_exec_time DESC
  LIMIT 10;
  ```

- **Vacuum and analyze** (PostgreSQL)
  ```sql
  VACUUM ANALYZE audit_entries;
  ```

#### Quarterly

- **Review and adjust partitioning** (if implemented)
- **Archive old audit data** (> 1 year)
- **Review and update retention policies**

### Database Maintenance

See [database-maintenance.md](database-maintenance.md) for detailed procedures.

---

## Disaster Recovery

### Backup Strategy

**Audit table backups:**
```bash
# PostgreSQL
pg_dump -h prod-db -U app_user -d app_db -t audit_entries \
  --file=audit_entries_backup_$(date +%Y%m%d).sql

# Compress
gzip audit_entries_backup_*.sql
```

**Retention:**
- Daily backups: 7 days
- Weekly backups: 4 weeks
- Monthly backups: 12 months

### Restore Procedure

```bash
# 1. Verify backup integrity
gunzip -t audit_entries_backup_20260606.sql.gz

# 2. Extract
gunzip audit_entries_backup_20260606.sql.gz

# 3. Restore (to test environment first!)
psql -h test-db -U app_user -d app_db < audit_entries_backup_20260606.sql

# 4. Verify
psql -h test-db -U app_user -d app_db -c \
  "SELECT COUNT(*), MIN(occurred_at), MAX(occurred_at) FROM audit_entries;"
```

### Data Loss Scenarios

| Scenario | Impact | Recovery |
|----------|--------|----------|
| Database failure during write | Partial audit entry loss | Check fallback files, replay if available |
| Application crash | No data loss (transactions handle) | No action needed |
| Audit table corruption | Complete audit loss | Restore from backup |
| Disk full | Writes fail, fallback may activate | Free disk space, replay fallback |

---

## Escalation

### Severity Levels

**P1 - Critical**
- Health check DOWN for > 5 minutes
- Error rate > 50%
- Database unavailable
- **Action:** Page oncall immediately

**P2 - High**
- Error rate 10-50%
- Latency p95 > 1 second
- Circuit breaker open > 10 minutes
- **Action:** Create incident, notify team

**P3 - Medium**
- Error rate 5-10%
- Latency p95 > 500ms
- Circuit breaker state changes
- **Action:** Log issue, investigate during business hours

**P4 - Low**
- Error rate < 5%
- Minor configuration issues
- **Action:** Create backlog item

### Escalation Path

1. **Level 1:** Oncall engineer
2. **Level 2:** Development team lead
3. **Level 3:** Platform engineering lead
4. **Level 4:** CTO

---

## Appendices

### A. Configuration Reference

See `docs/configuration-reference.md` (future)

### B. Metrics Catalog

See `docs/monitoring-and-metrics.md` (Sprint 4)

### C. Database Schema

See `docs/schema-management.md`

### D. Security Considerations

See `docs/security-guide.md` (future)

---

**Document Maintenance:**
- Review quarterly
- Update after major version changes
- Validate procedures annually
