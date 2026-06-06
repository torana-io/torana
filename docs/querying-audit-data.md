# Querying Audit Data

This guide covers querying audit entries with Torana's powerful query API, including basic filtering, metadata queries, pagination, and performance optimization.

## Table of Contents

1. [Quick Start](#quick-start)
2. [Query Builder API](#query-builder-api)
3. [Basic Filtering](#basic-filtering)
4. [Metadata Filtering](#metadata-filtering)
5. [Pagination](#pagination)
6. [Ordering Results](#ordering-results)
7. [Performance Optimization](#performance-optimization)
8. [Database-Specific Features](#database-specific-features)
9. [Common Query Patterns](#common-query-patterns)
10. [Index Recommendations](#index-recommendations)
11. [Troubleshooting](#troubleshooting)

---

## Quick Start

### Basic Query Example

```java
@Autowired
private AuditTrail auditTrail;

public List<AuditEntryView> findRecentCancellations() {
    return auditTrail.query()
        .action("order.cancelled")
        .from(Instant.now().minus(Duration.ofDays(7)))
        .limit(100)
        .execute();
}
```

### Paginated Query Example

```java
public AuditQueryResult findOrderHistory(String customerId, int page, int size) {
    return auditTrail.query()
        .actionPrefix("order.")
        .metadata("customerId", customerId)
        .orderBy("occurred_at", OrderDirection.DESC)
        .limit(size)
        .offset(page * size)
        .executeWithPagination();
}
```

---

## Query Builder API

The `AuditQuery` interface provides a fluent API for building queries:

```java
public interface AuditQuery {
    // Filtering
    AuditQuery action(String action);
    AuditQuery actionPrefix(String prefix);
    AuditQuery actor(String actorId);
    AuditQuery tenant(String tenantId);
    AuditQuery target(String type, String id);
    AuditQuery targetType(String type);
    AuditQuery from(Instant from);
    AuditQuery to(Instant to);
    AuditQuery outcome(AuditOutcome outcome);
    AuditQuery requestId(String requestId);
    AuditQuery traceId(String traceId);

    // Metadata filtering (Sprint 3 feature)
    AuditQuery metadata(String key, Object value);
    AuditQuery hasMetadata(String key);

    // Ordering and pagination
    AuditQuery orderBy(String field, OrderDirection direction);
    AuditQuery limit(int limit);
    AuditQuery offset(int offset);

    // Execution
    List<AuditEntryView> execute();
    AuditQueryResult executeWithPagination();
    long count();
}
```

---

## Basic Filtering

### Filter by Action

```java
// Exact action match
List<AuditEntryView> entries = auditTrail.query()
    .action("user.login")
    .execute();

// Action prefix (wildcard-like)
List<AuditEntryView> entries = auditTrail.query()
    .actionPrefix("order.")  // Matches order.created, order.cancelled, etc.
    .execute();
```

### Filter by Actor

```java
// All actions by a specific user
List<AuditEntryView> entries = auditTrail.query()
    .actor("alice@example.com")
    .from(Instant.now().minus(Duration.ofDays(30)))
    .execute();
```

### Filter by Target

```java
// All changes to a specific entity
List<AuditEntryView> entries = auditTrail.query()
    .target("Order", "12345")
    .execute();

// All changes to a type of entity
List<AuditEntryView> entries = auditTrail.query()
    .targetType("Order")
    .outcome(AuditOutcome.SUCCESS)
    .limit(100)
    .execute();
```

### Filter by Time Range

```java
// Last 7 days
List<AuditEntryView> entries = auditTrail.query()
    .from(Instant.now().minus(Duration.ofDays(7)))
    .execute();

// Specific date range
Instant start = LocalDate.of(2026, 6, 1)
    .atStartOfDay(ZoneOffset.UTC).toInstant();
Instant end = LocalDate.of(2026, 6, 30)
    .atTime(23, 59, 59).atZone(ZoneOffset.UTC).toInstant();

List<AuditEntryView> entries = auditTrail.query()
    .from(start)
    .to(end)
    .execute();
```

### Filter by Outcome

```java
// All failed operations
List<AuditEntryView> entries = auditTrail.query()
    .outcome(AuditOutcome.FAILURE)
    .from(Instant.now().minus(Duration.ofHours(1)))
    .execute();
```

### Filter by Tracing IDs

```java
// All audit entries for a specific request
List<AuditEntryView> entries = auditTrail.query()
    .requestId("req-12345")
    .execute();

// All audit entries for a distributed trace
List<AuditEntryView> entries = auditTrail.query()
    .traceId("trace-abc123")
    .execute();
```

---

## Metadata Filtering

**New in Sprint 3:** Query audit entries by custom metadata fields.

### Filter by Metadata Value

```java
// Find all orders for a specific customer
List<AuditEntryView> entries = auditTrail.query()
    .actionPrefix("order.")
    .metadata("customerId", "12345")
    .execute();

// Multiple metadata filters (AND logic)
List<AuditEntryView> entries = auditTrail.query()
    .action("order.cancelled")
    .metadata("reason", "customer-request")
    .metadata("priority", "high")
    .execute();
```

### Check for Metadata Key Existence

```java
// Find all entries that have an error code
List<AuditEntryView> entries = auditTrail.query()
    .outcome(AuditOutcome.FAILURE)
    .hasMetadata("errorCode")
    .execute();
```

### Metadata Types

Metadata filtering supports different value types:

```java
// String values
query.metadata("status", "approved");

// Numeric values
query.metadata("amount", 100.50);
query.metadata("quantity", 5);

// Boolean values
query.metadata("isVerified", true);

// Null values
query.metadata("cancelledBy", null);
```

### Performance Considerations

**Important:** Metadata filtering requires JSON query support and may be slower than indexed field queries.

For frequently queried metadata fields:
1. **PostgreSQL:** Create GIN indexes on the metadata column
2. **MySQL:** Use generated columns with indexes
3. **H2:** Avoid metadata filtering in production (uses substring search)

See [Index Recommendations](#index-recommendations) for details.

---

## Pagination

### Basic Pagination

```java
// Page 1 (entries 0-49)
List<AuditEntryView> entries = auditTrail.query()
    .actionPrefix("order.")
    .limit(50)
    .offset(0)
    .execute();

// Page 2 (entries 50-99)
List<AuditEntryView> entries = auditTrail.query()
    .actionPrefix("order.")
    .limit(50)
    .offset(50)
    .execute();
```

### Pagination with Metadata

```java
@GetMapping("/api/audit/orders/{customerId}")
public AuditQueryResult getCustomerOrderHistory(
        @PathVariable String customerId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {

    return auditTrail.query()
        .actionPrefix("order.")
        .metadata("customerId", customerId)
        .orderBy("occurred_at", OrderDirection.DESC)
        .limit(size)
        .offset(page * size)
        .executeWithPagination();
}
```

### AuditQueryResult

The `AuditQueryResult` record provides rich pagination metadata:

```java
AuditQueryResult result = auditTrail.query()
    .actionPrefix("order.")
    .limit(50)
    .offset(100)
    .executeWithPagination();

// Access results
List<AuditEntryView> entries = result.entries();
long totalCount = result.totalCount();
int pageNumber = result.pageNumber();  // Current page (0-indexed)
int pageSize = result.pageSize();      // Requested page size
int totalPages = result.totalPages();  // Total number of pages

// Navigation helpers
boolean hasNext = result.hasNext();    // Are there more results?
boolean isFirst = result.isFirst();    // Is this the first page?
boolean isLast = result.isLast();      // Is this the last page?
int size = result.size();              // Entries in this page
boolean isEmpty = result.isEmpty();    // No entries in result
```

### REST API Example

```java
@RestController
@RequestMapping("/api/audit")
public class AuditQueryController {

    @Autowired
    private AuditTrail auditTrail;

    @GetMapping("/search")
    public ResponseEntity<PagedResponse<AuditEntryView>> search(
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        AuditQuery query = auditTrail.query();

        if (action != null) {
            query.actionPrefix(action);
        }
        if (actor != null) {
            query.actor(actor);
        }

        AuditQueryResult result = query
            .orderBy("occurred_at", OrderDirection.DESC)
            .limit(size)
            .offset(page * size)
            .executeWithPagination();

        return ResponseEntity.ok(new PagedResponse<>(
            result.entries(),
            result.pageNumber(),
            result.pageSize(),
            result.totalCount(),
            result.totalPages()
        ));
    }
}
```

---

## Ordering Results

### Default Ordering

By default, results are ordered by `occurred_at DESC` (most recent first).

### Custom Ordering

```java
// Order by action name (alphabetically)
List<AuditEntryView> entries = auditTrail.query()
    .orderBy("action", OrderDirection.ASC)
    .execute();

// Order by actor
List<AuditEntryView> entries = auditTrail.query()
    .orderBy("actor_id", OrderDirection.ASC)
    .execute();

// Order by outcome (then by default occurred_at DESC)
List<AuditEntryView> entries = auditTrail.query()
    .orderBy("outcome", OrderDirection.DESC)
    .execute();
```

### Supported Order Fields

The following fields are supported for ordering:
- `occurred_at` (default)
- `action`
- `actor_id`
- `tenant_id`
- `target_type`
- `target_id`
- `outcome`

**Note:** Ordering by metadata fields is not currently supported. Consider extracting frequently sorted metadata fields to dedicated columns.

---

## Performance Optimization

### Query Performance Tips

1. **Always use indexed fields for filtering**
   ```java
   // GOOD: Uses indexed field
   query.action("order.created").from(recentDate)

   // SLOWER: Metadata filtering without index
   query.metadata("orderType", "subscription")
   ```

2. **Limit result sets**
   ```java
   // GOOD: Limited results
   query.actionPrefix("order.").limit(100)

   // BAD: Unbounded query
   query.actionPrefix("order.").execute()
   ```

3. **Use time ranges**
   ```java
   // GOOD: Bounded time range
   query.from(Instant.now().minus(Duration.ofDays(30)))

   // BAD: Unbounded (queries all history)
   query.execute()
   ```

4. **Use `count()` sparingly**
   ```java
   // Counting is expensive for large datasets
   long total = query.actionPrefix("order.").count();

   // Consider using pagination instead
   AuditQueryResult result = query.executeWithPagination();
   ```

### Query Execution Plan

For complex queries, analyze the execution plan:

```sql
-- PostgreSQL
EXPLAIN ANALYZE
SELECT * FROM audit_entries
WHERE action LIKE 'order.%'
  AND occurred_at >= '2026-05-01'
  AND metadata->>'customerId' = '12345'
ORDER BY occurred_at DESC
LIMIT 50;

-- MySQL
EXPLAIN
SELECT * FROM audit_entries
WHERE action LIKE 'order.%'
  AND occurred_at >= '2026-05-01'
  AND JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.customerId')) = '12345'
ORDER BY occurred_at DESC
LIMIT 50;
```

### Caching Query Results

For frequently accessed, slow-changing queries, consider caching:

```java
@Service
public class AuditQueryService {

    @Autowired
    private AuditTrail auditTrail;

    @Cacheable(value = "auditQueries", key = "#actor + '-' + #days")
    public List<AuditEntryView> findRecentActivityByActor(String actor, int days) {
        return auditTrail.query()
            .actor(actor)
            .from(Instant.now().minus(Duration.ofDays(days)))
            .limit(100)
            .execute();
    }
}
```

---

## Database-Specific Features

### PostgreSQL

**JSON Operators:**
- Uses `->>` operator for JSON text extraction
- Uses `?` operator for key existence checks
- Supports GIN indexes for fast JSON queries

**Example:**
```sql
-- Generated SQL for metadata filtering
SELECT * FROM audit_entries
WHERE metadata->>'customerId' = ?
  AND metadata ? 'priority'
ORDER BY occurred_at DESC;
```

**Recommended Indexes:**
```sql
-- GIN index for metadata queries
CREATE INDEX idx_audit_metadata_gin ON audit_entries USING GIN (metadata);

-- Composite index for common queries
CREATE INDEX idx_audit_action_time ON audit_entries(action, occurred_at DESC);
```

### MySQL

**JSON Functions:**
- Uses `JSON_EXTRACT()` and `JSON_UNQUOTE()` for value extraction
- Uses `JSON_CONTAINS_PATH()` for key existence
- Supports functional indexes (MySQL 8.0+)

**Example:**
```sql
-- Generated SQL for metadata filtering
SELECT * FROM audit_entries
WHERE JSON_UNQUOTE(JSON_EXTRACT(metadata, '$.customerId')) = ?
  AND JSON_CONTAINS_PATH(metadata, 'one', '$.priority')
ORDER BY occurred_at DESC;
```

**Recommended Indexes:**
```sql
-- Generated column + index for frequently queried metadata
ALTER TABLE audit_entries
  ADD customer_id VARCHAR(255)
    GENERATED ALWAYS AS (metadata->>'$.customerId') STORED;

CREATE INDEX idx_customer_id ON audit_entries(customer_id);

-- Functional index (MySQL 8.0.13+)
CREATE INDEX idx_metadata_customer
  ON audit_entries((CAST(metadata->>'$.customerId' AS CHAR(255))));
```

### H2 (Development/Testing)

**JSON Support:**
- Uses CLOB substring search (best-effort)
- Not recommended for production metadata queries
- Suitable for development and testing

**Example:**
```sql
-- Generated SQL (substring search)
SELECT * FROM audit_entries
WHERE metadata LIKE '%"customerId":"12345"%'
ORDER BY occurred_at DESC;
```

**Recommendation:** Use H2 for development, but test metadata queries on your production database (PostgreSQL or MySQL).

---

## Common Query Patterns

### 1. User Activity Timeline

```java
public List<AuditEntryView> getUserActivity(String userId, int days) {
    return auditTrail.query()
        .actor(userId)
        .from(Instant.now().minus(Duration.ofDays(days)))
        .orderBy("occurred_at", OrderDirection.DESC)
        .limit(100)
        .execute();
}
```

### 2. Entity Change History

```java
public List<AuditEntryView> getEntityHistory(String type, String id) {
    return auditTrail.query()
        .target(type, id)
        .orderBy("occurred_at", OrderDirection.ASC)  // Chronological order
        .execute();
}
```

### 3. Failed Operations Report

```java
public List<AuditEntryView> getFailedOperations(Instant since) {
    return auditTrail.query()
        .outcome(AuditOutcome.FAILURE)
        .from(since)
        .orderBy("occurred_at", OrderDirection.DESC)
        .execute();
}
```

### 4. Multi-Tenant Query

```java
public AuditQueryResult getTenantActivity(
        String tenantId, int page, int size) {
    return auditTrail.query()
        .tenant(tenantId)
        .from(Instant.now().minus(Duration.ofDays(90)))
        .orderBy("occurred_at", OrderDirection.DESC)
        .limit(size)
        .offset(page * size)
        .executeWithPagination();
}
```

### 5. Compliance Audit Trail

```java
public List<AuditEntryView> getDataAccessAudit(
        String dataType, LocalDate date) {

    Instant start = date.atStartOfDay(ZoneOffset.UTC).toInstant();
    Instant end = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();

    return auditTrail.query()
        .actionPrefix("data.access.")
        .metadata("dataType", dataType)
        .from(start)
        .to(end)
        .orderBy("occurred_at", OrderDirection.ASC)
        .execute();
}
```

### 6. Distributed Trace Query

```java
public List<AuditEntryView> getTraceAuditEntries(String traceId) {
    return auditTrail.query()
        .traceId(traceId)
        .orderBy("occurred_at", OrderDirection.ASC)
        .execute();
}
```

### 7. Customer Journey Analysis

```java
public List<AuditEntryView> getCustomerJourney(String customerId) {
    return auditTrail.query()
        .metadata("customerId", customerId)
        .from(Instant.now().minus(Duration.ofDays(30)))
        .orderBy("occurred_at", OrderDirection.ASC)
        .execute();
}
```

### 8. Security Event Query

```java
public List<AuditEntryView> getSecurityEvents(Duration window) {
    return auditTrail.query()
        .actionPrefix("security.")
        .from(Instant.now().minus(window))
        .orderBy("occurred_at", OrderDirection.DESC)
        .execute();
}
```

---

## Index Recommendations

### Standard Indexes (All Databases)

These indexes are created automatically by Torana's schema management:

```sql
-- Primary key
CREATE UNIQUE INDEX idx_audit_id ON audit_entries(id);

-- Action queries
CREATE INDEX idx_audit_action ON audit_entries(action);

-- Actor queries
CREATE INDEX idx_audit_actor ON audit_entries(actor_id);

-- Target queries
CREATE INDEX idx_audit_target ON audit_entries(target_type, target_id);

-- Time range queries
CREATE INDEX idx_audit_occurred_at ON audit_entries(occurred_at DESC);

-- Request correlation
CREATE INDEX idx_audit_request_id ON audit_entries(request_id);

-- Trace correlation
CREATE INDEX idx_audit_trace_id ON audit_entries(trace_id);

-- Multi-tenant queries
CREATE INDEX idx_audit_tenant ON audit_entries(tenant_id);
```

### Composite Indexes for Common Queries

```sql
-- Action + time range (very common)
CREATE INDEX idx_audit_action_time
  ON audit_entries(action, occurred_at DESC);

-- Actor + time range
CREATE INDEX idx_audit_actor_time
  ON audit_entries(actor_id, occurred_at DESC);

-- Tenant + action + time
CREATE INDEX idx_audit_tenant_action_time
  ON audit_entries(tenant_id, action, occurred_at DESC);

-- Outcome + time (for failure analysis)
CREATE INDEX idx_audit_outcome_time
  ON audit_entries(outcome, occurred_at DESC);
```

### Metadata Indexes

#### PostgreSQL

```sql
-- GIN index for all metadata queries (recommended)
CREATE INDEX idx_audit_metadata_gin ON audit_entries USING GIN (metadata);

-- Specific JSON path index (faster for single key)
CREATE INDEX idx_audit_metadata_customer
  ON audit_entries((metadata->>'customerId'));

-- Partial index for specific conditions
CREATE INDEX idx_audit_metadata_priority_high
  ON audit_entries((metadata->>'priority'))
  WHERE metadata->>'priority' = 'high';
```

#### MySQL 8.0+

```sql
-- Generated column approach (recommended)
ALTER TABLE audit_entries
  ADD customer_id VARCHAR(255)
    GENERATED ALWAYS AS (metadata->>'$.customerId') STORED,
  ADD priority VARCHAR(50)
    GENERATED ALWAYS AS (metadata->>'$.priority') STORED;

CREATE INDEX idx_customer_id ON audit_entries(customer_id);
CREATE INDEX idx_priority ON audit_entries(priority);

-- Functional index (alternative, MySQL 8.0.13+)
CREATE INDEX idx_metadata_customer
  ON audit_entries((CAST(metadata->>'$.customerId' AS CHAR(255))));
```

### Index Monitoring

#### PostgreSQL

```sql
-- Check index usage
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
WHERE schemaname = 'public' AND tablename = 'audit_entries'
ORDER BY idx_scan DESC;

-- Find unused indexes
SELECT
    schemaname,
    tablename,
    indexname
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
  AND tablename = 'audit_entries'
  AND idx_scan = 0
  AND indexname NOT LIKE '%_pkey';
```

#### MySQL

```sql
-- Check index usage
SELECT
    TABLE_NAME,
    INDEX_NAME,
    SEQ_IN_INDEX,
    COLUMN_NAME,
    CARDINALITY
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
  AND TABLE_NAME = 'audit_entries'
ORDER BY INDEX_NAME, SEQ_IN_INDEX;

-- Analyze table statistics
ANALYZE TABLE audit_entries;
```

---

## Troubleshooting

### Query Performance Issues

**Problem:** Queries are slow

**Diagnosis:**
1. Check if indexes exist:
   ```sql
   -- PostgreSQL
   SELECT indexname FROM pg_indexes
   WHERE tablename = 'audit_entries';

   -- MySQL
   SHOW INDEX FROM audit_entries;
   ```

2. Analyze query execution plan:
   ```sql
   -- PostgreSQL
   EXPLAIN ANALYZE <your query>;

   -- MySQL
   EXPLAIN <your query>;
   ```

**Solutions:**
- Add missing indexes (see [Index Recommendations](#index-recommendations))
- Add time range filtering (`from()` method)
- Use `limit()` to bound result sets
- Consider archiving old data

### Metadata Queries Not Working

**Problem:** Metadata filtering returns no results or errors

**Diagnosis:**
1. Check database dialect support:
   ```java
   // H2 uses substring search (limited)
   // PostgreSQL uses native JSON operators
   // MySQL uses JSON functions
   ```

2. Verify metadata structure:
   ```sql
   SELECT metadata FROM audit_entries LIMIT 1;
   ```

3. Check for typos in metadata keys:
   ```java
   // Case-sensitive!
   .metadata("customerId", "123")   // Correct
   .metadata("customerid", "123")   // Won't match
   ```

**Solutions:**
- Ensure metadata is stored as valid JSON
- Use exact key names (case-sensitive)
- Test queries directly in SQL first
- Check database-specific syntax in logs

### Pagination Issues

**Problem:** Incorrect page counts or missing entries

**Diagnosis:**
1. Check if ordering is deterministic:
   ```java
   // BAD: Non-deterministic ordering
   query.orderBy("action", OrderDirection.ASC)

   // GOOD: Deterministic ordering
   query.orderBy("occurred_at", OrderDirection.DESC)
   ```

2. Verify offset calculation:
   ```java
   int pageNumber = 0;  // First page
   int pageSize = 20;
   int offset = pageNumber * pageSize;  // 0 * 20 = 0 ✓

   int pageNumber = 1;  // Second page
   int offset = pageNumber * pageSize;  // 1 * 20 = 20 ✓
   ```

**Solutions:**
- Always use deterministic ordering (include `occurred_at`)
- Validate page number and size
- Use `executeWithPagination()` for automatic calculation

### SQL Injection Concerns

**Problem:** Worried about SQL injection in metadata queries

**Answer:** Torana prevents SQL injection through:

1. **Parameterized queries:** All user values are bound as parameters
   ```java
   // User input is safely bound as parameter
   query.metadata("key", userInput);
   ```

2. **Key validation:** JSON keys are validated and escaped
   ```java
   // Only alphanumeric, underscore, dash, period allowed
   query.metadata("customer-id.v2", "123");  // ✓ Valid
   query.metadata("'; DROP TABLE--", "123"); // ✗ Throws exception
   ```

3. **Field name whitelisting:** ORDER BY fields are whitelisted
   ```java
   // Only allowed fields accepted
   query.orderBy("occurred_at", OrderDirection.DESC);  // ✓ Valid
   query.orderBy("'); DROP TABLE--", OrderDirection.DESC); // ✗ Throws exception
   ```

---

## Configuration

### Query Limits

Configure default and maximum limits:

```yaml
torana:
  query:
    default-limit: 100          # Default if limit() not called
    max-limit: 1000             # Maximum allowed limit
    enable-metadata-filtering: true  # Enable/disable metadata queries
```

### Example Configuration

```java
@Configuration
public class AuditQueryConfiguration {

    @Bean
    public AuditQueryCustomizer queryCustomizer() {
        return query -> query
            .limit(50)  // Default limit for all queries
            .orderBy("occurred_at", OrderDirection.DESC);
    }
}
```

---

## Best Practices

### 1. Always Use Time Bounds

```java
// GOOD: Bounded time range
query.from(Instant.now().minus(Duration.ofDays(90)))

// BAD: Unbounded (queries entire history)
query.execute()
```

### 2. Limit Result Sets

```java
// GOOD: Explicit limit
query.limit(100)

// BAD: No limit (could return millions of rows)
query.execute()
```

### 3. Use Indexed Fields for Filtering

```java
// GOOD: Filters on indexed fields first
query.action("order.created")
     .from(recentDate)
     .metadata("customerId", "123")

// LESS EFFICIENT: Metadata filter before indexed filters
query.metadata("customerId", "123")
     .action("order.created")
```

### 4. Prefer Pagination Over Large Limits

```java
// GOOD: Paginated results
AuditQueryResult result = query
    .limit(50)
    .offset(page * 50)
    .executeWithPagination();

// BAD: Large limit
List<AuditEntryView> entries = query
    .limit(10000)
    .execute();
```

### 5. Use Specific Queries Over Broad Ones

```java
// GOOD: Specific action
query.action("order.cancelled")

// LESS EFFICIENT: Broad prefix
query.actionPrefix("order.")
```

### 6. Cache Expensive Queries

```java
@Cacheable("auditQueries")
public List<AuditEntryView> getPopularReport() {
    // Expensive query cached for performance
    return auditTrail.query()...execute();
}
```

### 7. Monitor Query Performance

```java
@Component
public class AuditQueryMonitor {

    @Autowired
    private MeterRegistry meterRegistry;

    public List<AuditEntryView> monitoredQuery(AuditQuery query) {
        Timer.Sample sample = Timer.start(meterRegistry);

        try {
            return query.execute();
        } finally {
            sample.stop(meterRegistry.timer("audit.query.duration"));
        }
    }
}
```

---

## Next Steps

- **[Deployment Guide](deployment-guide.md)** - Deploy and configure Torana in production
- **[Database Maintenance](database-maintenance.md)** - Maintain audit tables over time
- **[Monitoring and Metrics](monitoring-and-metrics.md)** - Monitor query performance
- **[Resilience and Error Recovery](resilience-and-error-recovery.md)** - Handle query failures

---

**Version:** 0.2.0 (Sprint 3+4)
**Last Updated:** 2026-06-06
