# Database Migrations

This guide explains how Torana database schema evolves across versions and what compatibility guarantees you can expect.

## Migration Philosophy

Torana follows these principles for schema evolution:

1. **Stability First**: Schema changes are rare and conservative
2. **Backward Compatibility**: New versions read old schemas when possible
3. **Explicit Migrations**: Changes are documented and versioned
4. **No Silent Changes**: Schema modifications require explicit opt-in
5. **Version Tracking**: `schema_version` column tracks evolution

## Versioning and Compatibility

### Schema Version vs Torana Version

| Schema Version | Torana Versions | Description |
|----------------|-----------------|-------------|
| 1 | 0.1.x - 0.2.x | Initial production schema |
| 2 | (future) | Reserved for first schema evolution |

### Compatibility Matrix

| Torana Version | Can Read Schema | Can Write Schema | Migration Required |
|----------------|-----------------|------------------|--------------------|
| 0.1.x | v1 | v1 | No (initial) |
| 0.2.x | v1 | v1 | No |
| 0.3.x (planned) | v1, v2 | v2 | Yes (additive) |

**Compatibility guarantee:**

> Torana will always be able to read audit entries written by any previous version within the same major version (0.x).

Major version bumps (1.0.0) may introduce breaking schema changes that require data migration.

## Migration Files

Official migration files are maintained in:
```
torana-jdbc/src/main/resources/db/migration/
├── postgresql/
│   └── V1__create_audit_entries_table.sql
├── mysql/
│   └── V1__create_audit_entries_table.sql
└── h2/
    └── V1__create_audit_entries_table.sql
```

When upgrading Torana, check for new migration files (V2__, V3__, etc.) in the release.

## Schema Change Policy

### Patch Versions (0.1.x)

**Guarantee:** No schema changes.

Example: 0.1.10 → 0.1.11

**Safe to upgrade:** Yes, no database changes required.

### Minor Versions (0.x.0)

**Guarantee:** Only additive, backward-compatible changes.

Allowed changes:
- Adding new nullable columns
- Adding new indexes
- Adding new tables (for future features)

Not allowed:
- Removing columns
- Changing column types
- Adding NOT NULL columns without defaults
- Renaming columns or tables

Example: 0.1.x → 0.2.0

**Upgrade process:**
1. Check CHANGELOG.md for schema changes
2. Review and apply new migration files (if any)
3. Upgrade application code
4. Old and new versions can coexist during rollout

### Major Versions (x.0.0)

**Guarantee:** Breaking changes allowed with migration path.

Example: 0.x.x → 1.0.0

**Upgrade process:**
1. Read upgrade guide
2. Test migration in non-production environment
3. Backup production database
4. Apply migration
5. Upgrade application
6. Verify functionality

Breaking changes will be documented at least one minor version in advance.

## Current Schema: Version 1

The initial schema (version 1) includes:

### Core Fields

| Column | Type | Nullable | Description |
|--------|------|----------|-------------|
| `id` | UUID/CHAR(36) | NOT NULL | Unique identifier |
| `action` | VARCHAR(255) | NOT NULL | Business action name |
| `occurred_at` | TIMESTAMP(TZ) | NOT NULL | When action occurred |
| `outcome` | VARCHAR(20) | NOT NULL | SUCCESS, FAILURE, or PARTIAL |
| `schema_version` | INT | NOT NULL | Default: 1 |
| `created_at` | TIMESTAMP(TZ) | NOT NULL | Record insertion time |

### Actor Context

| Column | Type | Nullable |
|--------|------|----------|
| `actor_id` | VARCHAR(255) | NULL |
| `actor_type` | VARCHAR(50) | NULL |
| `actor_name` | VARCHAR(255) | NULL |

### Tenant Context

| Column | Type | Nullable |
|--------|------|----------|
| `tenant_id` | VARCHAR(255) | NULL |
| `tenant_name` | VARCHAR(255) | NULL |

### Target Context

| Column | Type | Nullable |
|--------|------|----------|
| `target_type` | VARCHAR(255) | NULL |
| `target_id` | VARCHAR(255) | NULL |
| `target_name` | VARCHAR(255) | NULL |

### Request Context

| Column | Type | Nullable |
|--------|------|----------|
| `request_id` | VARCHAR(255) | NULL |
| `request_method` | VARCHAR(10) | NULL |
| `request_path` | VARCHAR(2048) | NULL |
| `client_ip` | VARCHAR(45) | NULL |
| `user_agent` | VARCHAR(512) | NULL |

### Tracing Context

| Column | Type | Nullable |
|--------|------|----------|
| `trace_id` | VARCHAR(64) | NULL |
| `span_id` | VARCHAR(64) | NULL |
| `parent_span_id` | VARCHAR(64) | NULL |

### Structured Data

| Column | Type | Nullable | Notes |
|--------|------|----------|-------|
| `metadata` | JSONB/JSON/CLOB | NULL | Additional context |
| `changes` | JSONB/JSON/CLOB | NULL | Before/after snapshots |
| `error_message` | TEXT/CLOB | NULL | Failure details |

### Indexes

- `idx_audit_entries_action` on `action`
- `idx_audit_entries_occurred_at` on `occurred_at`
- `idx_audit_entries_actor_id` on `actor_id`
- `idx_audit_entries_tenant_id` on `tenant_id`
- `idx_audit_entries_target` on `(target_type, target_id)`

## Future Migration Examples

These are hypothetical examples to illustrate the migration process.

### Example: Adding a New Column (Minor Version)

**Scenario:** Torana 0.3.0 adds an optional `correlation_id` field.

**Migration file:** `V2__add_correlation_id.sql`

**PostgreSQL:**
```sql
ALTER TABLE audit_entries ADD COLUMN correlation_id VARCHAR(255);
CREATE INDEX idx_audit_entries_correlation_id ON audit_entries (correlation_id);
```

**Backward compatibility:**
- Torana 0.2.x can still read the table (ignores new column)
- Torana 0.3.x writes to new column and reads both old and new records
- No data migration required

### Example: Partitioning (Major Version)

**Scenario:** Torana 2.0.0 restructures the table for partitioning.

**Migration file:** `V3__partition_by_occurred_at.sql`

**PostgreSQL:**
```sql
-- Create new partitioned table
CREATE TABLE audit_entries_new (LIKE audit_entries INCLUDING ALL)
PARTITION BY RANGE (occurred_at);

-- Create initial partitions
CREATE TABLE audit_entries_2026_q1 PARTITION OF audit_entries_new
    FOR VALUES FROM ('2026-01-01') TO ('2026-04-01');

-- Migrate data
INSERT INTO audit_entries_new SELECT * FROM audit_entries;

-- Swap tables
ALTER TABLE audit_entries RENAME TO audit_entries_old;
ALTER TABLE audit_entries_new RENAME TO audit_entries;

-- Drop old table after verification
-- DROP TABLE audit_entries_old;
```

**Breaking change:**
- Torana 1.x cannot use partitioned schema
- Requires major version bump
- Migration guide provided in advance

## Applying Migrations

### With Flyway

Flyway automatically applies migrations in order:

```
V1__create_audit_entries_table.sql
V2__add_correlation_id.sql
V3__partition_by_occurred_at.sql
```

Copy new migration files from the Torana release to your project:
```
src/main/resources/db/migration/
```

Spring Boot runs Flyway on startup and applies pending migrations.

### With Liquibase

Add changesets to your changelog:

```yaml
databaseChangeLog:
  - changeSet:
      id: 1
      author: torana
      changes:
        - sqlFile:
            path: V1__create_audit_entries_table.sql

  - changeSet:
      id: 2
      author: torana
      changes:
        - sqlFile:
            path: V2__add_correlation_id.sql
```

### Manual Migration

For manual migration:

1. Check current schema version:
```sql
SELECT MAX(schema_version) FROM audit_entries;
```

2. Review migration files in new Torana version

3. Apply migrations in order:
```bash
psql -U user -d db -f V2__add_correlation_id.sql
```

4. Verify:
```sql
SELECT column_name FROM information_schema.columns
WHERE table_name = 'audit_entries' ORDER BY ordinal_position;
```

## Rolling Back Migrations

### Development

If a migration fails in development, you can:

**Flyway:**
```bash
./mvnw flyway:repair
./mvnw flyway:clean  # WARNING: Deletes all data
./mvnw flyway:migrate
```

**Manual:**
```sql
DROP TABLE audit_entries;
-- Recreate from V1
```

### Production

Production rollbacks are complex. Instead:

1. **Test migrations in staging first**
2. **Backup before migrating**
3. **Use transactions** (when possible)
4. **Have a rollback plan**

For additive migrations (new nullable columns), rollback is often:
```sql
ALTER TABLE audit_entries DROP COLUMN correlation_id;
DROP INDEX idx_audit_entries_correlation_id;
```

For breaking migrations, follow the upgrade guide's rollback instructions.

## Zero-Downtime Migrations

For high-availability systems, follow this pattern:

### Phase 1: Additive Change
Deploy new schema (add column, don't require it yet):
```sql
ALTER TABLE audit_entries ADD COLUMN new_field VARCHAR(255);
```

Deploy Torana version that optionally writes to new column.

### Phase 2: Backfill (if needed)
Gradually backfill existing records:
```sql
UPDATE audit_entries SET new_field = derived_value
WHERE new_field IS NULL AND occurred_at > NOW() - INTERVAL '30 days'
LIMIT 10000;
```

### Phase 3: Enforcement
Make column required:
```sql
ALTER TABLE audit_entries ALTER COLUMN new_field SET NOT NULL;
```

This approach allows zero-downtime upgrades.

## Compatibility Testing

Before upgrading in production:

1. **Restore production backup to staging**
2. **Apply migration to staging**
3. **Run smoke tests**
4. **Verify queries still work**
5. **Check index usage**
6. **Measure query performance**

Example test:
```java
@Test
void testSchemaV2Compatibility() {
    // Insert with Torana 0.2.x format
    AuditEntry entry = AuditEntry.builder()
        .action("test.action")
        .occurredAt(Instant.now())
        .build();

    auditWriter.write(entry);

    // Verify readable with Torana 0.3.x
    AuditEntry read = auditReader.findById(entry.getId());
    assertThat(read.getAction()).isEqualTo("test.action");
}
```

## Migration Checklist

Before upgrading Torana:

- [ ] Read CHANGELOG.md for schema changes
- [ ] Review migration files in new version
- [ ] Check compatibility matrix above
- [ ] Backup production database
- [ ] Test migration in staging
- [ ] Verify application still works
- [ ] Plan rollback approach
- [ ] Schedule maintenance window (if needed)
- [ ] Apply migration
- [ ] Monitor application logs
- [ ] Verify audit entries are being written
- [ ] Check query performance

## Getting Help

If you encounter migration issues:

1. Check [GitHub Issues](https://github.com/torana-io/torana/issues)
2. Review [CHANGELOG.md](../CHANGELOG.md)
3. Check database logs for errors
4. Open an issue with:
   - Torana version (old and new)
   - Database type and version
   - Migration tool (Flyway/Liquibase/Manual)
   - Error messages
   - Migration files used

## Next Steps

- [Schema Management Guide](./schema-management.md) - How to manage schemas
- [CONTRIBUTING.md](../CONTRIBUTING.md) - Proposing schema improvements
- [CHANGELOG.md](../CHANGELOG.md) - Version history and changes
