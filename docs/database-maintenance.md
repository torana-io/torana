# Database Maintenance Guide

This guide covers maintaining the Torana audit trail database over time, including monitoring table growth, archiving strategies, partitioning, performance optimization, and routine maintenance tasks.

## Table of Contents

1. [Overview](#overview)
2. [Monitoring Table Growth](#monitoring-table-growth)
3. [Data Retention Policies](#data-retention-policies)
4. [Archival Strategies](#archival-strategies)
5. [Table Partitioning](#table-partitioning)
6. [Performance Optimization](#performance-optimization)
7. [Index Maintenance](#index-maintenance)
8. [Vacuum and Analyze](#vacuum-and-analyze)
9. [Backup and Recovery](#backup-and-recovery)
10. [Capacity Planning](#capacity-planning)
11. [Troubleshooting](#troubleshooting)

---

## Overview

### Why Maintenance Matters

Audit trails grow continuously and can become very large:
- **Volume:** 1M entries/day = ~365M entries/year
- **Size:** ~2KB per entry = ~2GB/million entries
- **Growth:** Linear or exponential depending on activity

Without proper maintenance:
- Queries slow down over time
- Disk space runs out
- Database backups take hours
- Index performance degrades

### Maintenance Schedule

| Task | Frequency | Duration | Impact |
|------|-----------|----------|--------|
| Monitor table size | Daily | 1 min | None |
| Analyze tables | Weekly | 5-10 min | Low |
| Vacuum (auto) | Automatic | Varies | Low |
| Archive old data | Monthly | 1-4 hours | Medium |
| Re-index | Quarterly | 30-60 min | High |
| Partitioning setup | One-time | 2-4 hours | High |

---

## Monitoring Table Growth

### Daily Monitoring Queries

#### PostgreSQL

```sql
-- Table size with indexes
SELECT
    pg_size_pretty(pg_total_relation_size('audit_entries')) AS total_size,
    pg_size_pretty(pg_relation_size('audit_entries')) AS table_size,
    pg_size_pretty(pg_total_relation_size('audit_entries') - pg_relation_size('audit_entries')) AS index_size;

-- Row count and growth rate
SELECT
    COUNT(*) as total_rows,
    COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '1 day') as rows_last_day,
    COUNT(*) FILTER (WHERE created_at > NOW() - INTERVAL '7 days') as rows_last_week,
    MIN(occurred_at) as oldest_entry,
    MAX(occurred_at) as newest_entry
FROM audit_entries;

-- Growth projection
WITH daily_growth AS (
    SELECT
        DATE(created_at) as date,
        COUNT(*) as entries,
        SUM(LENGTH(metadata::text) + LENGTH(before_snapshot) + LENGTH(after_snapshot)) as bytes
    FROM audit_entries
    WHERE created_at > NOW() - INTERVAL '30 days'
    GROUP BY DATE(created_at)
)
SELECT
    ROUND(AVG(entries)) as avg_entries_per_day,
    pg_size_pretty(ROUND(AVG(bytes))::bigint) as avg_bytes_per_day,
    pg_size_pretty(ROUND(AVG(bytes) * 365)::bigint) as projected_yearly_growth
FROM daily_growth;
```

#### MySQL

```sql
-- Table size with indexes
SELECT
    table_name,
    ROUND(data_length / 1024 / 1024, 2) AS table_size_mb,
    ROUND(index_length / 1024 / 1024, 2) AS index_size_mb,
    ROUND((data_length + index_length) / 1024 / 1024, 2) AS total_size_mb
FROM information_schema.TABLES
WHERE table_schema = DATABASE()
  AND table_name = 'audit_entries';

-- Row count and growth
SELECT
    COUNT(*) as total_rows,
    SUM(CASE WHEN created_at > DATE_SUB(NOW(), INTERVAL 1 DAY) THEN 1 ELSE 0 END) as rows_last_day,
    SUM(CASE WHEN created_at > DATE_SUB(NOW(), INTERVAL 7 DAY) THEN 1 ELSE 0 END) as rows_last_week,
    MIN(occurred_at) as oldest_entry,
    MAX(occurred_at) as newest_entry
FROM audit_entries;
```

### Automated Monitoring Script

```bash
#!/bin/bash
# monitor-audit-table.sh

DB_HOST="localhost"
DB_NAME="myapp"
DB_USER="monitor_user"

# PostgreSQL monitoring
psql -h $DB_HOST -U $DB_USER -d $DB_NAME -c "
SELECT
    'audit_entries' as table_name,
    pg_size_pretty(pg_total_relation_size('audit_entries')) AS total_size,
    COUNT(*) as row_count,
    MAX(occurred_at) as latest_entry
FROM audit_entries;
" | tee -a /var/log/torana/table-size.log

# Alert if > 100GB
SIZE_BYTES=$(psql -h $DB_HOST -U $DB_USER -d $DB_NAME -t -c \
    "SELECT pg_total_relation_size('audit_entries')")

if [ $SIZE_BYTES -gt 107374182400 ]; then
    echo "ALERT: audit_entries table exceeds 100GB" | \
        mail -s "Audit Table Size Alert" ops@example.com
fi
```

### Grafana Dashboard Query

```promql
# Table size metric (requires custom exporter)
audit_table_size_bytes{table="audit_entries"}

# Row count
audit_table_rows{table="audit_entries"}

# Growth rate (entries per second)
rate(audit_table_rows[1h])
```

---

## Data Retention Policies

### Defining Retention Periods

Factors to consider:
1. **Compliance requirements** (GDPR, SOX, HIPAA, etc.)
2. **Business needs** (dispute resolution, analytics)
3. **Storage costs**
4. **Query performance**

Common retention policies:
- **Financial data:** 7 years
- **Healthcare data:** 6 years
- **User activity:** 1-2 years
- **System events:** 90 days

### Implementing Retention Policies

```sql
-- PostgreSQL: Delete entries older than retention period
DELETE FROM audit_entries
WHERE occurred_at < NOW() - INTERVAL '2 years'
  AND action NOT IN ('security.breach', 'compliance.violation');  -- Keep critical events

-- Run in batches to avoid lock contention
DO $$
DECLARE
    deleted_count INTEGER := 1;
    batch_size INTEGER := 10000;
BEGIN
    WHILE deleted_count > 0 LOOP
        DELETE FROM audit_entries
        WHERE id IN (
            SELECT id FROM audit_entries
            WHERE occurred_at < NOW() - INTERVAL '2 years'
            LIMIT batch_size
        );
        GET DIAGNOSTICS deleted_count = ROW_COUNT;
        COMMIT;
        PERFORM pg_sleep(1);  -- Avoid overwhelming the database
    END LOOP;
END$$;
```

### Retention Policy Configuration

```yaml
# application.yml
torana:
  retention:
    default-retention-days: 730  # 2 years
    retention-exceptions:
      - action-pattern: "security.*"
        retention-days: 2555  # 7 years
      - action-pattern: "compliance.*"
        retention-days: 2555
      - action-pattern: "financial.*"
        retention-days: 2555
    deletion-batch-size: 10000
    deletion-schedule: "0 2 * * *"  # 2 AM daily
```

---

## Archival Strategies

### Strategy 1: Archive to Cold Storage

**When to use:** Need to retain data for compliance but rarely accessed

```sql
-- Create archive table
CREATE TABLE audit_entries_archive (LIKE audit_entries INCLUDING ALL);

-- Move old data to archive
INSERT INTO audit_entries_archive
SELECT * FROM audit_entries
WHERE occurred_at < NOW() - INTERVAL '1 year';

-- Verify row count matches
SELECT
    (SELECT COUNT(*) FROM audit_entries WHERE occurred_at < NOW() - INTERVAL '1 year') as source_count,
    (SELECT COUNT(*) FROM audit_entries_archive) as archive_count;

-- Delete from main table after verification
DELETE FROM audit_entries
WHERE occurred_at < NOW() - INTERVAL '1 year';
```

### Strategy 2: Export to Data Lake

**When to use:** Long-term analytics, data warehouse integration

```bash
#!/bin/bash
# export-to-s3.sh

YEAR_AGO=$(date -d '1 year ago' +%Y-%m-%d)

# Export to CSV
psql -h localhost -U postgres -d myapp -c "\COPY (
    SELECT * FROM audit_entries
    WHERE occurred_at < '$YEAR_AGO'
) TO '/tmp/audit_archive.csv' WITH CSV HEADER"

# Compress
gzip /tmp/audit_archive.csv

# Upload to S3
aws s3 cp /tmp/audit_archive.csv.gz \
    s3://mycompany-audit-archives/$(date +%Y)/audit_archive_$(date +%Y%m%d).csv.gz

# Verify upload
aws s3 ls s3://mycompany-audit-archives/$(date +%Y)/

# Delete from database
psql -h localhost -U postgres -d myapp -c "
    DELETE FROM audit_entries
    WHERE occurred_at < '$YEAR_AGO'
"
```

### Strategy 3: Partition-Based Archival

**When to use:** Time-series partitioning is in place

```sql
-- Detach old partition
ALTER TABLE audit_entries DETACH PARTITION audit_entries_2024;

-- Move to archive tablespace (optional)
ALTER TABLE audit_entries_2024 SET TABLESPACE archive_tablespace;

-- Or drop entirely if exported
DROP TABLE audit_entries_2024;
```

---

## Table Partitioning

### Why Partition?

Benefits:
- **Faster queries:** Scan only relevant partitions
- **Easier archival:** Drop entire partitions instantly
- **Better vacuum performance:** Smaller partitions
- **Parallel processing:** Query multiple partitions concurrently

### PostgreSQL Partitioning (Recommended)

#### Initial Setup (New Table)

```sql
-- Create partitioned table
CREATE TABLE audit_entries (
    id UUID NOT NULL,
    action VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    -- ... other columns
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id, occurred_at)  -- Partition key must be in PK
) PARTITION BY RANGE (occurred_at);

-- Create monthly partitions
CREATE TABLE audit_entries_2026_01 PARTITION OF audit_entries
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

CREATE TABLE audit_entries_2026_02 PARTITION OF audit_entries
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');

-- Create indexes on each partition
CREATE INDEX idx_audit_2026_01_action ON audit_entries_2026_01(action);
CREATE INDEX idx_audit_2026_01_actor ON audit_entries_2026_01(actor_id);
-- ... repeat for other partitions
```

#### Migrating Existing Table

```sql
-- Step 1: Rename existing table
ALTER TABLE audit_entries RENAME TO audit_entries_old;

-- Step 2: Create partitioned table
CREATE TABLE audit_entries (
    -- Same structure as above
) PARTITION BY RANGE (occurred_at);

-- Step 3: Create partitions for historical data
CREATE TABLE audit_entries_2025 PARTITION OF audit_entries
    FOR VALUES FROM ('2025-01-01') TO ('2026-01-01');

CREATE TABLE audit_entries_2026 PARTITION OF audit_entries
    FOR VALUES FROM ('2026-01-01') TO ('2027-01-01');

-- Step 4: Migrate data (in batches to avoid locks)
INSERT INTO audit_entries
SELECT * FROM audit_entries_old
WHERE occurred_at >= '2025-01-01' AND occurred_at < '2026-01-01';

INSERT INTO audit_entries
SELECT * FROM audit_entries_old
WHERE occurred_at >= '2026-01-01' AND occurred_at < '2027-01-01';

-- Step 5: Verify and drop old table
SELECT COUNT(*) FROM audit_entries;
SELECT COUNT(*) FROM audit_entries_old;

DROP TABLE audit_entries_old;
```

#### Automatic Partition Creation

```sql
-- Create function to automatically create monthly partitions
CREATE OR REPLACE FUNCTION create_monthly_partition()
RETURNS void AS $$
DECLARE
    partition_date DATE := DATE_TRUNC('month', CURRENT_DATE);
    partition_name TEXT := 'audit_entries_' || TO_CHAR(partition_date, 'YYYY_MM');
    next_month DATE := partition_date + INTERVAL '1 month';
BEGIN
    -- Check if partition already exists
    IF NOT EXISTS (
        SELECT 1 FROM pg_class WHERE relname = partition_name
    ) THEN
        EXECUTE format('
            CREATE TABLE %I PARTITION OF audit_entries
            FOR VALUES FROM (%L) TO (%L)',
            partition_name,
            partition_date,
            next_month
        );

        -- Create indexes
        EXECUTE format('CREATE INDEX %I ON %I(action)', 'idx_' || partition_name || '_action', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I(actor_id)', 'idx_' || partition_name || '_actor', partition_name);
        EXECUTE format('CREATE INDEX %I ON %I(occurred_at)', 'idx_' || partition_name || '_occurred', partition_name);

        RAISE NOTICE 'Created partition %', partition_name;
    END IF;
END;
$$ LANGUAGE plpgsql;

-- Schedule with cron (requires pg_cron extension)
SELECT cron.schedule('create_partition', '0 0 1 * *', 'SELECT create_monthly_partition()');
```

### MySQL Partitioning

```sql
-- Create partitioned table (MySQL 8.0+)
CREATE TABLE audit_entries (
    id CHAR(36) NOT NULL,
    action VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMP NOT NULL,
    -- ... other columns
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (YEAR(occurred_at) * 100 + MONTH(occurred_at)) (
    PARTITION p_2026_01 VALUES LESS THAN (202602),
    PARTITION p_2026_02 VALUES LESS THAN (202603),
    PARTITION p_2026_03 VALUES LESS THAN (202604),
    PARTITION p_2026_04 VALUES LESS THAN (202605),
    PARTITION p_2026_05 VALUES LESS THAN (202606),
    PARTITION p_2026_06 VALUES LESS THAN (202607),
    PARTITION p_2026_07 VALUES LESS THAN (202608),
    PARTITION p_2026_08 VALUES LESS THAN (202609),
    PARTITION p_2026_09 VALUES LESS THAN (202610),
    PARTITION p_2026_10 VALUES LESS THAN (202611),
    PARTITION p_2026_11 VALUES LESS THAN (202612),
    PARTITION p_2026_12 VALUES LESS THAN (202701),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- Add new partition for next month
ALTER TABLE audit_entries
REORGANIZE PARTITION p_future INTO (
    PARTITION p_2027_01 VALUES LESS THAN (202702),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- Drop old partition
ALTER TABLE audit_entries DROP PARTITION p_2024_01;
```

---

## Performance Optimization

### Query Performance Tuning

```sql
-- Identify slow queries
SELECT
    query,
    calls,
    total_exec_time / 1000 as total_time_sec,
    mean_exec_time / 1000 as avg_time_sec,
    max_exec_time / 1000 as max_time_sec
FROM pg_stat_statements
WHERE query LIKE '%audit_entries%'
ORDER BY total_exec_time DESC
LIMIT 10;

-- Analyze specific query
EXPLAIN (ANALYZE, BUFFERS)
SELECT * FROM audit_entries
WHERE action = 'order.created'
  AND occurred_at > NOW() - INTERVAL '7 days'
ORDER BY occurred_at DESC
LIMIT 100;
```

### Connection Pooling

```yaml
# Optimize connection pool for audit writes
spring:
  datasource:
    hikari:
      minimum-idle: 10
      maximum-pool-size: 50
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      pool-name: AuditPool
```

### Write Performance Optimization

```sql
-- Batch inserts are much faster
-- Instead of:
INSERT INTO audit_entries VALUES (...);
INSERT INTO audit_entries VALUES (...);
INSERT INTO audit_entries VALUES (...);

-- Use:
INSERT INTO audit_entries VALUES
    (...),
    (...),
    (...);

-- Or COPY for bulk loads
COPY audit_entries FROM '/path/to/data.csv' WITH CSV HEADER;
```

---

## Index Maintenance

### Index Bloat Detection (PostgreSQL)

```sql
-- Check for index bloat
SELECT
    schemaname,
    tablename,
    indexname,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
  AND tablename = 'audit_entries'
ORDER BY pg_relation_size(indexrelid) DESC;

-- Identify unused indexes
SELECT
    schemaname,
    tablename,
    indexname,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
    idx_scan
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
  AND tablename = 'audit_entries'
  AND idx_scan = 0
  AND indexname NOT LIKE '%_pkey';
```

### Re-indexing

```sql
-- Concurrent reindex (doesn't lock table)
REINDEX INDEX CONCURRENTLY idx_audit_action;
REINDEX INDEX CONCURRENTLY idx_audit_occurred_at;

-- Reindex entire table (during maintenance window)
REINDEX TABLE CONCURRENTLY audit_entries;
```

### MySQL Index Optimization

```sql
-- Optimize table (rebuilds indexes)
OPTIMIZE TABLE audit_entries;

-- Analyze table statistics
ANALYZE TABLE audit_entries;

-- Check index cardinality
SHOW INDEX FROM audit_entries;
```

---

## Vacuum and Analyze

### PostgreSQL Autovacuum

```sql
-- Check autovacuum settings
SELECT name, setting, unit
FROM pg_settings
WHERE name LIKE '%autovacuum%';

-- Tune autovacuum for large tables
ALTER TABLE audit_entries SET (
    autovacuum_vacuum_scale_factor = 0.05,  -- Vacuum when 5% of rows change
    autovacuum_analyze_scale_factor = 0.02,  -- Analyze when 2% change
    autovacuum_vacuum_cost_delay = 10  -- Reduce I/O impact
);

-- Check last vacuum/analyze
SELECT
    schemaname,
    relname,
    last_vacuum,
    last_autovacuum,
    last_analyze,
    last_autoanalyze,
    n_dead_tup,
    n_live_tup
FROM pg_stat_user_tables
WHERE relname = 'audit_entries';
```

### Manual Vacuum

```sql
-- Vacuum (reclaim space from deleted rows)
VACUUM VERBOSE audit_entries;

-- Vacuum with analyze
VACUUM ANALYZE audit_entries;

-- Full vacuum (requires exclusive lock, use during maintenance)
VACUUM FULL audit_entries;
```

### MySQL Table Optimization

```sql
-- Optimize table (similar to vacuum)
OPTIMIZE TABLE audit_entries;

-- Update statistics
ANALYZE TABLE audit_entries;
```

---

## Backup and Recovery

### Backup Strategies

#### Full Database Backup

```bash
# PostgreSQL
pg_dump -h localhost -U postgres -d myapp \
    -F c -f /backups/myapp_$(date +%Y%m%d).backup

# With compression
pg_dump -h localhost -U postgres -d myapp \
    -F c -Z 9 -f /backups/myapp_$(date +%Y%m%d).backup.gz

# MySQL
mysqldump -h localhost -u root -p myapp \
    | gzip > /backups/myapp_$(date +%Y%m%d).sql.gz
```

#### Table-Only Backup

```bash
# PostgreSQL
pg_dump -h localhost -U postgres -d myapp \
    -t audit_entries \
    -F c -f /backups/audit_entries_$(date +%Y%m%d).backup

# MySQL
mysqldump -h localhost -u root -p myapp audit_entries \
    | gzip > /backups/audit_entries_$(date +%Y%m%d).sql.gz
```

#### Point-in-Time Recovery (PostgreSQL)

```bash
# Enable WAL archiving
# In postgresql.conf:
wal_level = replica
archive_mode = on
archive_command = 'cp %p /archive/%f'

# Take base backup
pg_basebackup -h localhost -U postgres -D /backups/base -P

# Restore to specific point
pg_restore -h localhost -U postgres -d myapp \
    -t audit_entries /backups/audit_entries_20260606.backup
```

### Disaster Recovery Testing

```bash
# 1. Restore to test environment
psql -h test-db -U postgres -c "CREATE DATABASE myapp_restore"
pg_restore -h test-db -U postgres -d myapp_restore \
    /backups/myapp_20260606.backup

# 2. Verify row count
psql -h test-db -U postgres -d myapp_restore -c \
    "SELECT COUNT(*) FROM audit_entries"

# 3. Test queries
psql -h test-db -U postgres -d myapp_restore -c \
    "SELECT * FROM audit_entries ORDER BY occurred_at DESC LIMIT 10"

# 4. Clean up
psql -h test-db -U postgres -c "DROP DATABASE myapp_restore"
```

---

## Capacity Planning

### Sizing Estimates

```
Assumptions:
- Average entry size: 2 KB
- Growth rate: 1 million entries/month
- Retention: 2 years

Storage calculation:
- Total entries: 1M/month × 24 months = 24M entries
- Storage needed: 24M × 2 KB = 48 GB
- With indexes (3x): 48 GB × 3 = 144 GB
- With overhead (20%): 144 GB × 1.2 = ~175 GB

Recommendation: Provision 250 GB for 2-year retention
```

### Monitoring Disk Usage

```bash
# PostgreSQL data directory
du -sh /var/lib/postgresql/data/

# Specific database
psql -c "SELECT pg_size_pretty(pg_database_size('myapp'))"

# Table growth over time
SELECT
    DATE(created_at) as date,
    COUNT(*) as entries,
    pg_size_pretty(SUM(
        LENGTH(metadata::text) +
        LENGTH(before_snapshot) +
        LENGTH(after_snapshot)
    )::bigint) as estimated_size
FROM audit_entries
WHERE created_at > NOW() - INTERVAL '30 days'
GROUP BY DATE(created_at)
ORDER BY date;
```

### Alerting Thresholds

```yaml
# Prometheus alerts
- alert: AuditTableLarge
  expr: audit_table_size_bytes > 100e9  # 100GB
  annotations:
    summary: "Audit table exceeds 100GB"

- alert: AuditTableGrowthHigh
  expr: rate(audit_table_size_bytes[7d]) > 1e9  # 1GB/day
  annotations:
    summary: "Audit table growing > 1GB/day"

- alert: DiskSpaceLow
  expr: node_filesystem_avail_bytes{mountpoint="/var/lib/postgresql"} < 50e9
  annotations:
    summary: "Less than 50GB free on database disk"
```

---

## Troubleshooting

### Problem: Table is very large

**Diagnosis:**
```sql
-- Check table size
SELECT pg_size_pretty(pg_total_relation_size('audit_entries'));

-- Check oldest entry
SELECT MIN(occurred_at) FROM audit_entries;
```

**Solutions:**
1. Archive old data (see [Archival Strategies](#archival-strategies))
2. Implement partitioning
3. Adjust retention policy
4. Run `VACUUM FULL` during maintenance window

### Problem: Queries are slow

**Diagnosis:**
```sql
-- Check for missing indexes
EXPLAIN ANALYZE SELECT ...;

-- Check index usage
SELECT * FROM pg_stat_user_indexes WHERE relname = 'audit_entries';
```

**Solutions:**
1. Add missing indexes
2. Update table statistics: `ANALYZE audit_entries`
3. Consider partitioning
4. Add time range to queries

### Problem: Disk space running out

**Immediate action:**
```sql
-- Quick cleanup of very old data
DELETE FROM audit_entries
WHERE occurred_at < NOW() - INTERVAL '3 years'
LIMIT 100000;

VACUUM audit_entries;
```

**Long-term solution:**
1. Implement automated archival
2. Set up partitioning
3. Adjust retention policy
4. Add disk space monitoring

---

## Best Practices Checklist

- [ ] Monitor table size weekly
- [ ] Set up automated archival
- [ ] Configure retention policies
- [ ] Implement partitioning for large tables (>100GB)
- [ ] Run VACUUM ANALYZE regularly (or enable autovacuum)
- [ ] Monitor index bloat quarterly
- [ ] Test backup restoration monthly
- [ ] Review slow queries monthly
- [ ] Plan for 2x growth capacity
- [ ] Document archival procedures
- [ ] Set up disk space alerts

---

## Next Steps

- **[Deployment Guide](deployment-guide.md)** - Deploy Torana in production
- **[Monitoring and Metrics](monitoring-and-metrics.md)** - Monitor database performance
- **[Operational Runbook](operational-runbook.md)** - Day-to-day operations
- **[Querying Audit Data](querying-audit-data.md)** - Optimize queries

---

**Version:** 0.2.0
**Last Updated:** 2026-06-06
**Maintained By:** Torana Team
