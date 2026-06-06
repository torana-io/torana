# Torana Monitoring & Metrics Guide

**Version:** 0.2.0
**Last Updated:** 2026-06-06
**Audience:** Operations, SRE, DevOps teams

This guide provides comprehensive documentation for monitoring the Torana audit trail system using Micrometer metrics, Spring Boot Actuator health checks, and observability integrations.

---

## Table of Contents

- [Overview](#overview)
- [Metrics Catalog](#metrics-catalog)
- [Health Checks](#health-checks)
- [Prometheus Integration](#prometheus-integration)
- [Grafana Dashboards](#grafana-dashboards)
- [Alert Rules](#alert-rules)
- [Performance Tuning](#performance-tuning)

---

## Overview

Torana provides comprehensive observability through:

1. **Micrometer Metrics** - Write latency, throughput, error rates
2. **Spring Boot Actuator Health Checks** - Database connectivity, error rate monitoring
3. **Circuit Breaker Metrics** - Resilience4j integration for failure tracking
4. **Custom Actuator Endpoints** - Audit system configuration and status

### Quick Start

```yaml
# Minimal monitoring configuration
torana:
  metrics:
    enabled: true
    include-detailed-tags: false

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
```

---

## Metrics Catalog

### Core Audit Metrics

#### 1. `torana.audit.write.latency` (Timer)

**Description:** Latency of audit write operations

**Type:** Timer (histogram + percentiles)

**Tags:**
- `operation` - Always "write" or "writeBatch"
- `action` - Audit action name (only if `include-detailed-tags: true`)
- `outcome` - success/failure (only if `include-detailed-tags: true`)

**Percentiles:**
- p50 (median)
- p75
- p95
- p99
- p999

**Example Queries:**

```promql
# p95 write latency
histogram_quantile(0.95, rate(torana_audit_write_latency_seconds_bucket[5m]))

# p99 write latency over time
histogram_quantile(0.99, rate(torana_audit_write_latency_seconds_bucket[5m]))

# Average write latency
rate(torana_audit_write_latency_seconds_sum[5m]) / rate(torana_audit_write_latency_seconds_count[5m])
```

**Expected Values:**
- p50: < 20ms
- p95: < 100ms
- p99: < 200ms

**Alerts:**
- WARNING: p95 > 200ms for 10 minutes
- CRITICAL: p95 > 500ms for 5 minutes

---

#### 2. `torana.audit.write.success` (Counter)

**Description:** Count of successful audit write operations

**Type:** Counter

**Tags:**
- `operation` - "write" or "writeBatch"
- `action` - Audit action name (only if `include-detailed-tags: true`)

**Example Queries:**

```promql
# Writes per second
rate(torana_audit_write_success_total[5m])

# Total writes in last hour
increase(torana_audit_write_success_total[1h])

# Writes by action (if detailed tags enabled)
sum by (action) (rate(torana_audit_write_success_total[5m]))
```

**Expected Values:**
- Varies by application load
- Should correlate with business transaction volume

---

#### 3. `torana.audit.write.error` (Counter)

**Description:** Count of failed audit write operations

**Type:** Counter

**Tags:**
- `operation` - "write" or "writeBatch"
- `exception` - Exception class name (only if `include-detailed-tags: true`)

**Example Queries:**

```promql
# Error rate percentage
100 * rate(torana_audit_write_error_total[5m]) /
  (rate(torana_audit_write_error_total[5m]) + rate(torana_audit_write_success_total[5m]))

# Errors per minute
rate(torana_audit_write_error_total[1m]) * 60

# Errors by exception type (if detailed tags enabled)
sum by (exception) (rate(torana_audit_write_error_total[5m]))
```

**Expected Values:**
- Normal: < 1% error rate
- WARNING: 1-5% error rate
- CRITICAL: > 5% error rate

**Alerts:**
- WARNING: Error rate > 5% for 5 minutes
- CRITICAL: Error rate > 10% for 2 minutes

---

### Circuit Breaker Metrics

When resilience patterns are enabled, Resilience4j provides additional metrics:

#### 4. `resilience4j.circuitbreaker.state` (Gauge)

**Description:** Current circuit breaker state

**Type:** Gauge

**Values:**
- `0` - CLOSED (normal operation)
- `1` - OPEN (failing, using fallback)
- `2` - HALF_OPEN (testing recovery)
- `3` - DISABLED
- `4` - FORCED_OPEN

**Tags:**
- `name` - Always "toranaAuditWriter"

**Example Queries:**

```promql
# Current state
resilience4j_circuitbreaker_state{name="toranaAuditWriter"}

# State changes (transitions to OPEN)
changes(resilience4j_circuitbreaker_state{name="toranaAuditWriter"}[1h])
```

**Alerts:**
- WARNING: State == 1 (OPEN) for > 5 minutes
- CRITICAL: State == 1 (OPEN) for > 30 minutes

---

#### 5. `resilience4j.circuitbreaker.failure_rate` (Gauge)

**Description:** Current failure rate (percentage)

**Type:** Gauge

**Tags:**
- `name` - Always "toranaAuditWriter"

**Example Queries:**

```promql
# Current failure rate
resilience4j_circuitbreaker_failure_rate{name="toranaAuditWriter"}

# Failure rate trend
avg_over_time(resilience4j_circuitbreaker_failure_rate{name="toranaAuditWriter"}[5m])
```

**Expected Values:**
- Normal: < 10%
- WARNING: 10-50%
- CRITICAL: > 50% (circuit will open)

---

#### 6. `resilience4j.circuitbreaker.calls` (Counter)

**Description:** Circuit breaker call statistics

**Type:** Counter

**Tags:**
- `name` - Always "toranaAuditWriter"
- `kind` - "successful", "failed", "not_permitted"

**Example Queries:**

```promql
# Successful calls per second
rate(resilience4j_circuitbreaker_calls_total{name="toranaAuditWriter",kind="successful"}[5m])

# Failed calls per second
rate(resilience4j_circuitbreaker_calls_total{name="toranaAuditWriter",kind="failed"}[5m])

# Calls rejected due to open circuit
rate(resilience4j_circuitbreaker_calls_total{name="toranaAuditWriter",kind="not_permitted"}[5m])
```

---

### Health Check Metrics

#### 7. Health Check Status

**Endpoint:** `/actuator/health/auditTrail`

**Response Format:**

```json
{
  "status": "UP",
  "details": {
    "database": "PostgreSQL",
    "tableName": "audit_entries",
    "totalWrites": 1234,
    "totalErrors": 5,
    "errorRate": "0.41%",
    "errorRateThreshold": "10.00%",
    "windowSeconds": 60
  }
}
```

**Status Values:**
- `UP` - System healthy, error rate below threshold
- `WARNING` - Error rate exceeds threshold but database accessible
- `DOWN` - Database unavailable or table inaccessible

**Prometheus Metric:**

```promql
# Health status (1=UP, 0=DOWN)
up{job="spring-boot-app", path="/actuator/health/auditTrail"}
```

---

## Health Checks

### Audit Trail Health Indicator

**Configuration:**

```yaml
management:
  health:
    audit-trail:
      enabled: true

torana:
  metrics:
    health-check-window-seconds: 60
    error-rate-threshold: 0.1  # 10%
```

**Health Check Logic:**

1. **Database Connectivity Test**
   - Executes: `SELECT COUNT(*) FROM audit_entries`
   - Fails if table doesn't exist or database unreachable

2. **Error Rate Calculation**
   - Tracks writes and errors within configured window (default 60s)
   - Calculates: `errorRate = totalErrors / totalWrites`
   - Returns WARNING if errorRate > threshold

3. **Status Determination**
   ```
   if (database unreachable) → DOWN
   else if (errorRate > threshold && writes > 10) → WARNING
   else → UP
   ```

**Monitoring Health Checks:**

```bash
# Check health
curl -s http://localhost:8080/actuator/health/auditTrail | jq .

# Monitor health status
watch -n 5 'curl -s http://localhost:8080/actuator/health/auditTrail | jq .status'
```

---

## Prometheus Integration

### Setup

**1. Add Prometheus dependency:**

```xml
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**2. Enable Prometheus endpoint:**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: prometheus
  metrics:
    export:
      prometheus:
        enabled: true
```

**3. Configure Prometheus scraping:**

```yaml
# prometheus.yml
scrape_configs:
  - job_name: 'spring-boot-app'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 15s
    static_configs:
      - targets: ['localhost:8080']
```

### Metrics Endpoint

**URL:** `http://localhost:8080/actuator/prometheus`

**Sample Output:**

```
# HELP torana_audit_write_latency_seconds Audit write latency
# TYPE torana_audit_write_latency_seconds histogram
torana_audit_write_latency_seconds_bucket{operation="write",le="0.001",} 50.0
torana_audit_write_latency_seconds_bucket{operation="write",le="0.005",} 150.0
torana_audit_write_latency_seconds_bucket{operation="write",le="0.01",} 200.0
torana_audit_write_latency_seconds_bucket{operation="write",le="0.05",} 250.0
torana_audit_write_latency_seconds_bucket{operation="write",le="0.1",} 280.0
torana_audit_write_latency_seconds_bucket{operation="write",le="+Inf",} 300.0
torana_audit_write_latency_seconds_count{operation="write",} 300.0
torana_audit_write_latency_seconds_sum{operation="write",} 1.234

# HELP torana_audit_write_success_total Successful audit writes
# TYPE torana_audit_write_success_total counter
torana_audit_write_success_total{operation="write",} 295.0

# HELP torana_audit_write_error_total Failed audit writes
# TYPE torana_audit_write_error_total counter
torana_audit_write_error_total{operation="write",} 5.0
```

---

## Grafana Dashboards

### Using the Template

Import the dashboard from `docs/grafana-dashboard.json` (see below for template).

**Key Panels:**

1. **Write Latency** - p50, p95, p99 over time
2. **Write Throughput** - Ops/sec (successful + failed)
3. **Error Rate** - Percentage over time
4. **Circuit Breaker State** - Visual state indicator
5. **Health Check Status** - UP/WARNING/DOWN timeline
6. **Database Query Performance** - Histogram

**Dashboard Variables:**

- `$datasource` - Prometheus data source
- `$interval` - Time interval for aggregation
- `$job` - Application job name

### Example Panel Queries

**Write Latency (p95):**

```promql
histogram_quantile(0.95,
  sum(rate(torana_audit_write_latency_seconds_bucket{job="$job"}[5m])) by (le, operation)
)
```

**Throughput:**

```promql
sum(rate(torana_audit_write_success_total{job="$job"}[5m])) +
sum(rate(torana_audit_write_error_total{job="$job"}[5m]))
```

**Error Rate:**

```promql
100 * sum(rate(torana_audit_write_error_total{job="$job"}[5m])) /
  (sum(rate(torana_audit_write_error_total{job="$job"}[5m])) +
   sum(rate(torana_audit_write_success_total{job="$job"}[5m])))
```

---

## Alert Rules

See `docs/prometheus-alerts.yml` for complete alert definitions.

### Critical Alerts

#### High Error Rate

```yaml
- alert: ToranaHighErrorRate
  expr: |
    100 * sum(rate(torana_audit_write_error_total[5m])) /
      (sum(rate(torana_audit_write_error_total[5m])) +
       sum(rate(torana_audit_write_success_total[5m]))) > 10
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "Torana audit error rate exceeds 10%"
    description: "Error rate: {{ $value | humanizePercentage }}"
```

#### Circuit Breaker Open

```yaml
- alert: ToranaCircuitBreakerOpen
  expr: resilience4j_circuitbreaker_state{name="toranaAuditWriter"} == 1
  for: 5m
  labels:
    severity: critical
  annotations:
    summary: "Torana circuit breaker is OPEN"
    description: "Audit writes failing to fallback mechanism"
```

### Warning Alerts

#### High Latency

```yaml
- alert: ToranaHighLatency
  expr: |
    histogram_quantile(0.95,
      sum(rate(torana_audit_write_latency_seconds_bucket[5m])) by (le)
    ) > 0.2
  for: 10m
  labels:
    severity: warning
  annotations:
    summary: "Torana p95 latency exceeds 200ms"
    description: "p95 latency: {{ $value | humanizeDuration }}"
```

---

## Performance Tuning

### Reducing Metric Cardinality

**Problem:** High cardinality from detailed tags can overwhelm Prometheus

**Solution:** Disable detailed tags in production

```yaml
torana:
  metrics:
    include-detailed-tags: false  # Recommended for production
```

**Impact:**
- ✅ Reduced memory usage
- ✅ Faster query performance
- ❌ No per-action metrics
- ❌ No per-exception-type metrics

**When to Enable Detailed Tags:**
- Development environments
- Low-traffic staging
- Debugging specific issues
- Applications with < 10 unique action names

### Metric Collection Overhead

**Measured Overhead:**
- Latency: < 1ms per write operation
- Memory: ~10MB for 1M writes
- CPU: < 1% additional usage

**Disabling Metrics:**

```yaml
torana:
  metrics:
    enabled: false
```

Only disable if:
- Performance is critical (< 5ms p99 SLA)
- Monitoring via alternative means
- Development/testing environment

### Health Check Tuning

**Adjusting Window and Threshold:**

```yaml
torana:
  metrics:
    health-check-window-seconds: 300  # 5 minutes (more stable)
    error-rate-threshold: 0.05  # 5% (more sensitive)
```

**Trade-offs:**

| Window | Threshold | Behavior |
|--------|-----------|----------|
| 60s | 10% | Fast detection, may be noisy |
| 300s | 10% | Stable, slower detection |
| 60s | 5% | Very sensitive, many warnings |
| 300s | 5% | Balanced for production |

**Recommendation:** 300s window, 5% threshold for production

---

## Integration Examples

### CloudWatch

```yaml
management:
  metrics:
    export:
      cloudwatch:
        enabled: true
        namespace: Torana
        batch-size: 20
```

### Datadog

```yaml
management:
  metrics:
    export:
      datadog:
        enabled: true
        api-key: ${DATADOG_API_KEY}
        application-key: ${DATADOG_APP_KEY}
```

### New Relic

```yaml
management:
  metrics:
    export:
      newrelic:
        enabled: true
        api-key: ${NEWRELIC_API_KEY}
        account-id: ${NEWRELIC_ACCOUNT_ID}
```

---

## Troubleshooting

### Metrics Not Appearing

**Check:**
1. Metrics enabled: `torana.metrics.enabled=true`
2. Micrometer dependency present
3. Prometheus endpoint exposed: `/actuator/prometheus`
4. Prometheus scraping correctly

**Verify:**
```bash
# Check if metrics endpoint is accessible
curl http://localhost:8080/actuator/prometheus | grep torana

# Check Prometheus targets
curl http://prometheus:9090/api/v1/targets | jq '.data.activeTargets[] | select(.job=="spring-boot-app")'
```

### Health Check Always DOWN

**Check:**
1. Database connectivity: Can app connect to database?
2. Table exists: Does `audit_entries` table exist?
3. Permissions: Does user have SELECT permission?

**Debug:**
```bash
# Check database
psql -h db-host -U app_user -d app_db -c "SELECT COUNT(*) FROM audit_entries;"

# Check health details
curl -s http://localhost:8080/actuator/health/auditTrail | jq .details
```

---

**Related Documentation:**
- [Operational Runbook](operational-runbook.md)
- [Troubleshooting Guide](troubleshooting-guide.md)
- [Resilience & Error Recovery](resilience-and-error-recovery.md)
