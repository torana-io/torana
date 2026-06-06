# Torana Deployment Guide

This guide covers deploying Torana audit trail library across different environments, from development to production, including configuration, validation, and rollback procedures.

## Table of Contents

1. [Overview](#overview)
2. [Pre-Deployment Checklist](#pre-deployment-checklist)
3. [Environment Configurations](#environment-configurations)
4. [Deployment Steps](#deployment-steps)
5. [Post-Deployment Validation](#post-deployment-validation)
6. [Rollback Procedures](#rollback-procedures)
7. [Database Migration](#database-migration)
8. [Configuration Management](#configuration-management)
9. [Security Considerations](#security-considerations)
10. [Troubleshooting](#troubleshooting)

---

## Overview

### Deployment Models

Torana supports multiple deployment models:

1. **Spring Boot Embedded** - Default, audit library runs within your application
2. **Microservices** - Each service has its own audit configuration
3. **Centralized** - Multiple services write to a shared audit database

### Supported Platforms

- **Container Platforms:** Docker, Kubernetes, OpenShift
- **Cloud Platforms:** AWS, Azure, GCP
- **Application Servers:** Embedded (Spring Boot), Tomcat, JBoss
- **Databases:** PostgreSQL 12+, MySQL 8.0+, H2 (dev only)

---

## Pre-Deployment Checklist

### 1. Database Readiness

- [ ] Database version meets minimum requirements
  - PostgreSQL 12+ or MySQL 8.0+
- [ ] Database user created with appropriate permissions
  - `CREATE`, `ALTER`, `SELECT`, `INSERT`, `UPDATE`, `DELETE` on audit schema
- [ ] Connection pooling configured
  - Minimum 5 connections, maximum based on load
- [ ] Network connectivity verified
  - Application can reach database
  - Firewall rules configured
- [ ] Database backup strategy in place
  - Before migration
  - Regular ongoing backups

### 2. Application Configuration

- [ ] Dependency version verified
  ```xml
  <dependency>
      <groupId>io.torana</groupId>
      <artifactId>torana-spring-boot-starter</artifactId>
      <version>0.2.0</version>
  </dependency>
  ```

- [ ] Configuration externalized
  - Not hardcoded in application.yml
  - Using environment variables or config server
- [ ] Secrets management configured
  - Database passwords
  - Encryption keys (if using)
- [ ] Logging configuration reviewed
  - Appropriate log levels
  - Log aggregation configured

### 3. Infrastructure Readiness

- [ ] Resource allocation verified
  - CPU: Minimal overhead (<1%)
  - Memory: ~50MB per application instance
  - Disk: Based on retention policy
- [ ] Monitoring tools configured
  - Prometheus endpoint exposed
  - Grafana dashboards imported
  - Alert rules deployed
- [ ] Network policies configured
  - Database access allowed
  - Health check endpoints accessible

### 4. Testing Completed

- [ ] Integration tests pass on target database
- [ ] Load testing completed
  - Simulated production load
  - Circuit breaker tested
  - Fallback mechanism verified
- [ ] Security scanning completed
  - Dependency vulnerabilities checked
  - Redaction patterns validated

### 5. Documentation Ready

- [ ] Runbook reviewed by operations team
- [ ] Rollback procedure tested
- [ ] Contact information updated
- [ ] Change management ticket created

---

## Environment Configurations

### Development Environment

```yaml
# application-dev.yml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/myapp_dev
    username: dev_user
    password: dev_password
  jpa:
    show-sql: true

torana:
  enabled: true
  schema-mode: auto  # Automatic schema creation for dev

  # Minimal configuration for development
  transaction:
    success-write-policy: after_commit
    failure-write-policy: requires_new
    audit-error-policy: log_and_continue

  # Relaxed redaction for debugging
  redaction:
    enabled: false

  # Disable resilience features in dev
  resilience:
    circuit-breaker:
      enabled: false
    retry:
      enabled: false
    fallback:
      enabled: false

  # Enable detailed metrics for debugging
  metrics:
    enabled: true
    include-detailed-tags: true

logging:
  level:
    io.torana: DEBUG
```

### Staging Environment

```yaml
# application-staging.yml
spring:
  datasource:
    url: jdbc:postgresql://staging-db:5432/myapp_staging
    username: staging_user
    password: ${DB_PASSWORD}  # From environment variable
    hikari:
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 30000

torana:
  enabled: true
  schema-mode: none  # Use Flyway/Liquibase for schema management
  table-name: audit_entries

  # Production-like transaction behavior
  transaction:
    success-write-policy: after_commit
    failure-write-policy: requires_new
    audit-error-policy: callback

  # Enable redaction (staging may have production-like data)
  redaction:
    enabled: true
    placeholder: "[REDACTED]"
    patterns:
      - "(?i)password"
      - "(?i)secret"
      - "(?i)token"
      - "(?i)ssn"
      - "(?i)credit.?card"

  # Enable resilience features for testing
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
      exponential-backoff-multiplier: 2.0

    fallback:
      enabled: true
      type: logging  # Log-based fallback for staging

  # Moderate metrics detail
  metrics:
    enabled: true
    include-detailed-tags: false

logging:
  level:
    io.torana: INFO
```

### Production Environment

```yaml
# application-prod.yml
spring:
  datasource:
    url: jdbc:postgresql://prod-db.internal:5432/myapp
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
    hikari:
      minimum-idle: 10
      maximum-pool-size: 50
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000

torana:
  enabled: true
  schema-mode: none  # Schema managed by Flyway/Liquibase
  table-name: audit_entries

  # Production transaction behavior
  transaction:
    success-write-policy: after_commit
    failure-write-policy: requires_new
    audit-error-policy: callback

  # Strict redaction
  redaction:
    enabled: true
    placeholder: "[REDACTED]"
    patterns:
      - "(?i)password"
      - "(?i)secret"
      - "(?i)token"
      - "(?i)ssn"
      - "(?i)social.?security"
      - "(?i)credit.?card"
      - "(?i)cvv"
      - "(?i)api.?key"

  # Snapshot limits (prevent large objects)
  snapshot:
    max-depth: 3
    max-string-length: 1000

  # Full resilience stack
  resilience:
    circuit-breaker:
      enabled: true
      failure-rate-threshold: 50
      minimum-number-of-calls: 10
      wait-duration-in-open-state-seconds: 60
      permitted-number-of-calls-in-half-open-state: 5
      sliding-window-size: 100

    retry:
      enabled: true
      max-attempts: 3
      wait-duration-millis: 1000
      exponential-backoff-multiplier: 2.0

    fallback:
      enabled: true
      type: file_based
      file-based-directory: /var/lib/torana/fallback  # Must exist and be writable

  # Production metrics
  metrics:
    enabled: true
    include-detailed-tags: false  # Cardinality control
    health-check-window-seconds: 60
    error-rate-threshold: 0.1

# Actuator configuration for monitoring
management:
  endpoints:
    web:
      exposure:
        include: health,metrics,prometheus,auditTrail
      base-path: /actuator

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
    io.torana.resilience: DEBUG  # For troubleshooting circuit breaker
```

---

## Deployment Steps

### Step 1: Prepare Database

#### Option A: Flyway Migration (Recommended)

1. **Add Flyway dependency:**
   ```xml
   <dependency>
       <groupId>org.flywaydb</groupId>
       <artifactId>flyway-core</artifactId>
   </dependency>
   ```

2. **Create migration file:**
   `src/main/resources/db/migration/V1__create_audit_entries_table.sql`

   ```sql
   -- PostgreSQL
   CREATE TABLE audit_entries (
       id UUID PRIMARY KEY,
       action VARCHAR(255) NOT NULL,
       occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
       outcome VARCHAR(20) NOT NULL,
       actor_id VARCHAR(255),
       actor_type VARCHAR(100),
       actor_name VARCHAR(255),
       tenant_id VARCHAR(255),
       tenant_name VARCHAR(255),
       target_type VARCHAR(100),
       target_id VARCHAR(255),
       target_display_name VARCHAR(500),
       request_id VARCHAR(255),
       request_method VARCHAR(10),
       request_path VARCHAR(1000),
       client_ip VARCHAR(45),
       user_agent VARCHAR(500),
       trace_id VARCHAR(255),
       span_id VARCHAR(255),
       parent_span_id VARCHAR(255),
       metadata TEXT,
       before_snapshot TEXT,
       after_snapshot TEXT,
       error_message TEXT,
       schema_version INT DEFAULT 1,
       created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
   );

   -- Indexes
   CREATE INDEX idx_audit_action ON audit_entries(action);
   CREATE INDEX idx_audit_actor ON audit_entries(actor_id);
   CREATE INDEX idx_audit_target ON audit_entries(target_type, target_id);
   CREATE INDEX idx_audit_occurred_at ON audit_entries(occurred_at DESC);
   CREATE INDEX idx_audit_request_id ON audit_entries(request_id);
   CREATE INDEX idx_audit_trace_id ON audit_entries(trace_id);
   CREATE INDEX idx_audit_tenant ON audit_entries(tenant_id);
   CREATE INDEX idx_audit_metadata_gin ON audit_entries USING GIN (metadata);
   ```

3. **Configure Flyway:**
   ```yaml
   spring:
     flyway:
       enabled: true
       locations: classpath:db/migration
       baseline-on-migrate: true
   ```

#### Option B: Liquibase Migration

1. **Add Liquibase dependency:**
   ```xml
   <dependency>
       <groupId>org.liquibase</groupId>
       <artifactId>liquibase-core</artifactId>
   </dependency>
   ```

2. **Create changelog:**
   `src/main/resources/db/changelog/db.changelog-master.yaml`

   ```yaml
   databaseChangeLog:
     - changeSet:
         id: 1
         author: torana
         changes:
           - createTable:
               tableName: audit_entries
               columns:
                 - column:
                     name: id
                     type: uuid
                     constraints:
                       primaryKey: true
                 - column:
                     name: action
                     type: varchar(255)
                     constraints:
                       nullable: false
                 # ... other columns
           - createIndex:
               indexName: idx_audit_action
               tableName: audit_entries
               columns:
                 - column:
                     name: action
           # ... other indexes
   ```

#### Option C: Manual SQL Execution

For controlled production deployments:

```bash
# Connect to database
psql -h prod-db.internal -U admin -d myapp

# Run migration script
\i /path/to/V1__create_audit_entries_table.sql

# Verify
\d audit_entries
\di audit_entries*
```

### Step 2: Create Fallback Directory (Production Only)

```bash
# Create directory for file-based fallback
sudo mkdir -p /var/lib/torana/fallback
sudo chown app-user:app-group /var/lib/torana/fallback
sudo chmod 750 /var/lib/torana/fallback

# Verify permissions
ls -la /var/lib/torana/
```

### Step 3: Deploy Application

#### Docker Deployment

```dockerfile
# Dockerfile
FROM eclipse-temurin:21-jre-alpine

# Create app user
RUN addgroup -S app && adduser -S app -G app

# Create fallback directory
RUN mkdir -p /var/lib/torana/fallback && \
    chown app:app /var/lib/torana/fallback

# Copy application
COPY --chown=app:app target/myapp.jar /app/myapp.jar

USER app
WORKDIR /app

# Health check
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "myapp.jar"]
```

```bash
# Build and push
docker build -t myapp:0.2.0 .
docker push myregistry.com/myapp:0.2.0

# Run container
docker run -d \
  --name myapp \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_USERNAME=prod_user \
  -e DB_PASSWORD=secret \
  -v /var/lib/torana:/var/lib/torana \
  myregistry.com/myapp:0.2.0
```

#### Kubernetes Deployment

```yaml
# deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: myapp
  labels:
    app: myapp
    version: 0.2.0
spec:
  replicas: 3
  selector:
    matchLabels:
      app: myapp
  template:
    metadata:
      labels:
        app: myapp
        version: 0.2.0
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      containers:
      - name: myapp
        image: myregistry.com/myapp:0.2.0
        ports:
        - containerPort: 8080
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: DB_USERNAME
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: username
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: db-credentials
              key: password
        volumeMounts:
        - name: fallback-storage
          mountPath: /var/lib/torana/fallback
        livenessProbe:
          httpGet:
            path: /actuator/health/liveness
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 3
        readinessProbe:
          httpGet:
            path: /actuator/health/readiness
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
        resources:
          requests:
            memory: "512Mi"
            cpu: "500m"
          limits:
            memory: "1Gi"
            cpu: "1000m"
      volumes:
      - name: fallback-storage
        persistentVolumeClaim:
          claimName: torana-fallback-pvc
---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: torana-fallback-pvc
spec:
  accessModes:
    - ReadWriteMany
  resources:
    requests:
      storage: 10Gi
```

```bash
# Deploy
kubectl apply -f deployment.yaml

# Verify
kubectl get pods -l app=myapp
kubectl logs -f deployment/myapp
```

---

## Post-Deployment Validation

### 1. Health Checks

```bash
# Application health
curl http://app-host:8080/actuator/health

# Expected response
{
  "status": "UP",
  "components": {
    "auditTrail": {
      "status": "UP",
      "details": {
        "database": "connected",
        "recentErrorRate": 0.0,
        "circuitBreakerState": "CLOSED"
      }
    },
    "db": { "status": "UP" }
  }
}

# Liveness check
curl http://app-host:8080/actuator/health/liveness

# Readiness check
curl http://app-host:8080/actuator/health/readiness
```

### 2. Database Verification

```sql
-- Check table exists
SELECT COUNT(*) FROM audit_entries;

-- Verify indexes
SELECT indexname FROM pg_indexes
WHERE tablename = 'audit_entries';

-- Check schema version
SELECT schema_version FROM audit_entries LIMIT 1;
```

### 3. Functional Testing

```bash
# Trigger an audited action
curl -X POST http://app-host:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": "123", "amount": 100}'

# Verify audit entry created
curl http://app-host:8080/actuator/auditTrail/recent?limit=5

# Query via API
curl "http://app-host:8080/api/audit/search?action=order.created&limit=10"
```

### 4. Metrics Verification

```bash
# Check Prometheus metrics
curl http://app-host:8080/actuator/prometheus | grep torana

# Expected metrics
torana_audit_write_latency_seconds_count
torana_audit_write_success_total
torana_audit_write_error_total
resilience4j_circuitbreaker_state
```

### 5. Resilience Testing

```bash
# Stop database temporarily
docker stop postgres

# Trigger audit action (should use fallback)
curl -X POST http://app-host:8080/api/orders \
  -H "Content-Type: application/json" \
  -d '{"customerId": "123", "amount": 100}'

# Check fallback files created
ls -la /var/lib/torana/fallback/

# Check circuit breaker opened
curl http://app-host:8080/actuator/health

# Restart database
docker start postgres

# Verify recovery (fallback files replayed and deleted)
ls -la /var/lib/torana/fallback/  # Should be empty
SELECT COUNT(*) FROM audit_entries;  # Should include recovered entries
```

---

## Rollback Procedures

### Scenario 1: Application Rollback Only

If audit library causes issues but database schema is compatible:

```bash
# Kubernetes
kubectl rollout undo deployment/myapp

# Docker
docker stop myapp
docker run -d --name myapp myregistry.com/myapp:0.1.11 ...

# Verify previous version running
curl http://app-host:8080/actuator/info
```

### Scenario 2: Database Schema Rollback

If schema changes need reverting:

```bash
# Flyway rollback (requires paid version or manual)
# Option 1: Manual rollback
psql -h db-host -U admin -d myapp <<EOF
DROP INDEX IF EXISTS idx_audit_metadata_gin;
-- Revert other schema changes
EOF

# Option 2: Restore from backup
pg_restore -h db-host -U admin -d myapp /backups/pre_migration_backup.sql
```

### Scenario 3: Full Rollback

Complete rollback to previous version:

```yaml
# rollback-plan.md
1. Stop traffic to new version
   - Update load balancer / service mesh
   - Route traffic to old pods

2. Rollback application
   - kubectl rollout undo deployment/myapp
   - Verify health checks pass

3. Rollback database (if needed)
   - Restore from backup
   - OR manually revert schema changes

4. Verify functionality
   - Run smoke tests
   - Check audit entries being created

5. Monitor for 1 hour
   - Check error rates
   - Verify no data loss
```

---

## Database Migration

### Zero-Downtime Migration

```yaml
# Step 1: Add new columns (nullable)
ALTER TABLE audit_entries ADD COLUMN new_field VARCHAR(255);

# Step 2: Deploy application v0.2.0 (writes to both old and new)
# Application handles null values gracefully

# Step 3: Backfill data (run during low traffic)
UPDATE audit_entries SET new_field = ... WHERE new_field IS NULL;

# Step 4: Add NOT NULL constraint (if needed)
ALTER TABLE audit_entries ALTER COLUMN new_field SET NOT NULL;

# Step 5: Deploy application v0.2.1 (only uses new field)
```

### Blue-Green Deployment

```yaml
# Blue environment (current production)
- Running version 0.1.11
- Database schema version 1

# Green environment (new version)
- Deploy version 0.2.0
- Run migration on green database
- Validate functionality
- Switch traffic to green
- Keep blue running for rollback

# Switchover
kubectl patch service myapp -p '{"spec":{"selector":{"version":"0.2.0"}}}'
```

---

## Configuration Management

### Environment Variables

```bash
# Required
export DB_USERNAME=prod_user
export DB_PASSWORD=prod_secret
export SPRING_PROFILES_ACTIVE=prod

# Optional
export TORANA_FALLBACK_DIR=/var/lib/torana/fallback
export TORANA_METRICS_ENABLED=true
```

### Kubernetes ConfigMap

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: torana-config
data:
  application-prod.yml: |
    torana:
      enabled: true
      schema-mode: none
      resilience:
        circuit-breaker:
          enabled: true
        fallback:
          type: file_based
          file-based-directory: /var/lib/torana/fallback
```

### Secret Management

```yaml
# Kubernetes Secret
apiVersion: v1
kind: Secret
metadata:
  name: db-credentials
type: Opaque
stringData:
  username: prod_user
  password: super_secret_password
```

```bash
# AWS Secrets Manager
aws secretsmanager create-secret \
  --name prod/myapp/db-credentials \
  --secret-string '{"username":"prod_user","password":"secret"}'

# Reference in application
spring:
  cloud:
    aws:
      secretsmanager:
        enabled: true
        prefix: /secret
```

---

## Security Considerations

### 1. Database Credentials

- **Never** hardcode passwords
- Use secrets management (Kubernetes Secrets, AWS Secrets Manager, HashiCorp Vault)
- Rotate credentials regularly
- Use least-privilege database user

### 2. Network Security

```yaml
# Kubernetes NetworkPolicy
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: myapp-network-policy
spec:
  podSelector:
    matchLabels:
      app: myapp
  policyTypes:
  - Egress
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: postgres
    ports:
    - protocol: TCP
      port: 5432
```

### 3. Data Encryption

```yaml
# At rest (database)
# PostgreSQL: Enable pgcrypto extension
CREATE EXTENSION IF NOT EXISTS pgcrypto;

# In transit (TLS)
spring:
  datasource:
    url: jdbc:postgresql://db-host:5432/myapp?ssl=true&sslmode=require
```

### 4. Audit Data Access Control

```sql
-- Create read-only role for audit queries
CREATE ROLE audit_reader;
GRANT SELECT ON audit_entries TO audit_reader;

-- Application user (read/write)
CREATE USER app_user WITH PASSWORD 'secret';
GRANT SELECT, INSERT, UPDATE, DELETE ON audit_entries TO app_user;

-- Analytics user (read-only)
CREATE USER analytics_user WITH PASSWORD 'secret';
GRANT audit_reader TO analytics_user;
```

---

## Troubleshooting

### Issue: Application won't start

**Symptoms:**
```
Error creating bean 'auditTrail': Unable to connect to database
```

**Solutions:**
1. Check database connectivity:
   ```bash
   telnet db-host 5432
   ```

2. Verify credentials:
   ```bash
   psql -h db-host -U app_user -d myapp
   ```

3. Check network policies (Kubernetes):
   ```bash
   kubectl describe networkpolicy
   ```

### Issue: Audit entries not being created

**Diagnosis:**
```bash
# Check health endpoint
curl http://app-host:8080/actuator/health/auditTrail

# Check circuit breaker state
curl http://app-host:8080/actuator/metrics/resilience4j.circuitbreaker.state

# Check logs
kubectl logs -f deployment/myapp | grep "io.torana"
```

**Solutions:**
1. Circuit breaker open → Check database health
2. Fallback active → Check `/var/lib/torana/fallback/` for files
3. Errors in logs → Check stack traces

### Issue: High latency after deployment

**Diagnosis:**
```bash
# Check metrics
curl http://app-host:8080/actuator/metrics/torana.audit.write.latency

# Check database query performance
SELECT COUNT(*), AVG(EXTRACT(EPOCH FROM (created_at - occurred_at))) as avg_delay
FROM audit_entries
WHERE created_at > NOW() - INTERVAL '1 hour';
```

**Solutions:**
1. Check database connection pool size
2. Verify indexes exist
3. Review slow query logs
4. Consider async audit processing

---

## Next Steps

- **[Operational Runbook](operational-runbook.md)** - Day-to-day operations guide
- **[Database Maintenance](database-maintenance.md)** - Maintain audit tables over time
- **[Monitoring and Metrics](monitoring-and-metrics.md)** - Set up monitoring
- **[Troubleshooting Guide](troubleshooting-guide.md)** - Resolve common issues

---

**Version:** 0.2.0
**Last Updated:** 2026-06-06
**Maintained By:** Torana Team
