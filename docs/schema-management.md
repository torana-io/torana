# Schema Management

This guide explains how to manage the Torana audit database schema in different environments.

## Overview

Torana stores audit entries in a single table called `audit_entries` (configurable via `torana.table-name`). The schema must be created before Torana can record audit events.

## Recommended Approach by Environment

| Environment | Schema Mode | Migration Tool | Rationale |
|-------------|-------------|----------------|-----------|
| **Production** | `none` | Flyway or Liquibase | Version control, review process, rollback capability |
| **Staging** | `none` | Flyway or Liquibase | Mirror production setup |
| **Development** | `create` or `none` | Optional (Torana or Flyway) | Convenience vs consistency tradeoff |
| **Testing** | `create-drop` | Torana auto-management | Clean state per test run |

## Schema Modes

Torana supports three schema initialization modes via `torana.schema-mode`:

### `none` (default)

No automatic schema management. You are responsible for creating and maintaining the table.

**When to use:**
- Production environments
- Staging environments
- Any environment where schema changes require review and approval

**Configuration:**
```yaml
torana:
  schema-mode: none
```

**You must:**
- Create the table manually or via migration tool
- Handle schema evolution when upgrading Torana versions
- Ensure the table exists before the application starts

### `create`

Automatically creates the table if it doesn't exist. Does not modify existing tables.

**When to use:**
- Local development for quick iteration
- Proof-of-concept projects
- Demos and prototypes

**Configuration:**
```yaml
torana:
  schema-mode: create
```

**Limitations:**
- No migration history tracking
- Cannot evolve existing tables
- No rollback mechanism
- Schema drift between environments
- Not suitable for production

### `create-drop`

Creates the table on application startup and drops it on shutdown.

**When to use:**
- Integration tests
- Test containers
- Temporary environments

**Configuration:**
```yaml
torana:
  schema-mode: create-drop
```

**Warning:** All audit data is deleted when the application stops.

## Production Schema Management

For production systems, use a proper migration tool.

### Option 1: Flyway (Recommended)

Flyway is the most common choice for Spring Boot applications.

#### 1. Add Flyway dependency

**Maven:**
```xml
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-core</artifactId>
</dependency>
<!-- Add database-specific Flyway module -->
<dependency>
    <groupId>org.flywaydb</groupId>
    <artifactId>flyway-database-postgresql</artifactId>
</dependency>
```

**Gradle:**
```groovy
implementation 'org.flywaydb:flyway-core'
implementation 'org.flywaydb:flyway-database-postgresql'
```

#### 2. Copy the migration file

Torana provides official migration files for each supported database in:
```
torana-jdbc/src/main/resources/db/migration/
├── postgresql/V1__create_audit_entries_table.sql
├── mysql/V1__create_audit_entries_table.sql
└── h2/V1__create_audit_entries_table.sql
```

Copy the appropriate file to your project:
```
src/main/resources/db/migration/V1__create_audit_entries_table.sql
```

#### 3. Configure Flyway (if needed)

Spring Boot auto-configures Flyway. For custom configuration:
```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true  # For existing databases
```

#### 4. Verify

On application startup, Flyway will:
- Create the `flyway_schema_history` table
- Execute `V1__create_torana_audit_entries.sql`
- Track the migration in history

Check logs for:
```
Successfully applied 1 migration to schema "public", now at version v1
```

### Option 2: Liquibase

Liquibase is an alternative to Flyway.

#### 1. Add Liquibase dependency

**Maven:**
```xml
<dependency>
    <groupId>org.liquibase</groupId>
    <artifactId>liquibase-core</artifactId>
</dependency>
```

#### 2. Create changeset

Create `src/main/resources/db/changelog/db.changelog-master.yaml`:
```yaml
databaseChangeLog:
  - changeSet:
      id: 1
      author: torana
      changes:
        - sqlFile:
            path: db/migration/postgresql/V1__create_audit_entries_table.sql
            relativeToChangelogFile: false
```

Or convert the SQL to Liquibase XML/YAML/JSON format.

#### 3. Configure Liquibase

```yaml
spring:
  liquibase:
    enabled: true
    change-log: classpath:db/changelog/db.changelog-master.yaml
```

### Option 3: Manual Schema Creation

For environments without a migration tool, you can execute the SQL directly.

#### PostgreSQL
```bash
psql -U username -d database -f torana-jdbc/src/main/resources/db/migration/postgresql/V1__create_audit_entries_table.sql
```

#### MySQL
```bash
mysql -u username -p database < torana-jdbc/src/main/resources/db/migration/mysql/V1__create_audit_entries_table.sql
```

## Schema Evolution

When upgrading Torana to a new version, check the [migrations guide](./migrations.md) for schema changes.

### Schema Versioning

The `audit_entries` table includes a `schema_version` column (default: 1) to track compatibility:

- **schema_version = 1**: Torana 0.1.x - 0.2.x
- Future versions may increment this field

Torana is designed to be forward-compatible: newer versions can read older schema versions.

### Breaking Changes Policy

Torana follows semantic versioning for schema changes:

- **Patch versions (0.1.x)**: No schema changes
- **Minor versions (0.x.0)**: Backward-compatible schema additions
- **Major versions (x.0.0)**: May include breaking schema changes

Before upgrading, always:
1. Check the [CHANGELOG.md](../CHANGELOG.md) for schema changes
2. Review migration files in the new version
3. Test in a non-production environment first

## Custom Table Name

You can customize the table name:

```yaml
torana:
  table-name: my_audit_log
```

If using migrations, update the table name in your SQL file before running it.

## Multi-Tenant Schema Strategies

For multi-tenant applications, consider:

### Option 1: Shared Table with tenant_id (Recommended)

Use a single `audit_entries` table with indexed `tenant_id` column:

```sql
-- Already included in standard migration
CREATE INDEX idx_audit_entries_tenant_id ON audit_entries (tenant_id);
```

**Pros:**
- Simple schema management
- Efficient for moderate tenant counts
- Standard Torana configuration

**Cons:**
- All tenants share the same table
- Requires careful access controls

### Option 2: Schema per Tenant

Create separate database schemas for each tenant:

```sql
CREATE SCHEMA tenant_abc;
CREATE TABLE tenant_abc.audit_entries (...);

CREATE SCHEMA tenant_xyz;
CREATE TABLE tenant_xyz.audit_entries (...);
```

Configure Torana per tenant (dynamic DataSource setup required).

**Pros:**
- Strong isolation
- Independent scaling

**Cons:**
- Complex migration management
- Schema proliferation

### Option 3: Database per Tenant

Use a separate database for each tenant.

**Pros:**
- Complete isolation
- Independent backups

**Cons:**
- Most complex to manage
- Higher infrastructure cost

## Index Management

The standard migration creates these indexes:

| Index | Columns | Purpose |
|-------|---------|---------|
| `idx_audit_entries_action` | `action` | Filter by action type |
| `idx_audit_entries_occurred_at` | `occurred_at` | Time-range queries |
| `idx_audit_entries_actor_id` | `actor_id` | Filter by user |
| `idx_audit_entries_tenant_id` | `tenant_id` | Multi-tenant queries |
| `idx_audit_entries_target` | `target_type, target_id` | Entity-specific audit trail |

### Additional Indexes (PostgreSQL)

For JSON queries on PostgreSQL, consider GIN indexes:

```sql
CREATE INDEX idx_audit_entries_metadata_gin ON audit_entries USING GIN (metadata);
CREATE INDEX idx_audit_entries_changes_gin ON audit_entries USING GIN (changes);
```

Only add these if you frequently query inside JSON fields:
```sql
WHERE metadata @> '{"order_id": "12345"}'
```

### Index Monitoring

Monitor index usage and table size:

**PostgreSQL:**
```sql
SELECT schemaname, tablename, indexname, idx_scan, idx_tup_read
FROM pg_stat_user_indexes
WHERE tablename = 'audit_entries';
```

**MySQL:**
```sql
SELECT table_name, index_name, cardinality
FROM information_schema.statistics
WHERE table_name = 'audit_entries';
```

## Partitioning (Advanced)

For high-volume audit trails, consider table partitioning by `occurred_at`.

### PostgreSQL Partitioning Example

```sql
-- Recreate as partitioned table
CREATE TABLE audit_entries (
    id UUID NOT NULL,
    action VARCHAR(255) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    -- ... other columns
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- Create monthly partitions
CREATE TABLE audit_entries_2026_01 PARTITION OF audit_entries
    FOR VALUES FROM ('2026-01-01') TO ('2026-02-01');

CREATE TABLE audit_entries_2026_02 PARTITION OF audit_entries
    FOR VALUES FROM ('2026-02-01') TO ('2026-03-01');

-- Automate partition creation with pg_partman or custom scripts
```

### MySQL Partitioning Example

```sql
ALTER TABLE audit_entries
PARTITION BY RANGE (YEAR(occurred_at) * 100 + MONTH(occurred_at)) (
    PARTITION p_202601 VALUES LESS THAN (202602),
    PARTITION p_202602 VALUES LESS THAN (202603),
    -- ... add more partitions
    PARTITION p_future VALUES LESS THAN MAXVALUE
);
```

### Benefits
- Faster queries on recent data
- Easy archival of old partitions
- Improved vacuum/optimization performance

### Tradeoffs
- More complex schema management
- Primary key must include partition key
- Not all databases support partitioning

## Troubleshooting

### Table Not Found

**Error:**
```
Table 'audit_entries' doesn't exist
```

**Solution:**
- Ensure `torana.schema-mode` is set correctly
- If using `none`, verify the migration has run
- Check `flyway_schema_history` or `databasechangelog` table

### Permission Denied

**Error:**
```
Permission denied for table audit_entries
```

**Solution:**
Grant necessary permissions:
```sql
-- PostgreSQL
GRANT INSERT, SELECT ON audit_entries TO app_user;

-- MySQL
GRANT INSERT, SELECT ON database.audit_entries TO 'app_user'@'%';
```

### Schema Version Mismatch

**Error:**
```
Unsupported schema_version: 2
```

**Solution:**
- Upgrade Torana to a version that supports schema_version 2
- Check migration guide for compatibility

## Next Steps

- [Migration Guide](./migrations.md) - How migrations evolve between versions
- [RELEASING.md](../RELEASING.md) - Release process and version management
- [CONTRIBUTING.md](../CONTRIBUTING.md) - How to contribute schema improvements
