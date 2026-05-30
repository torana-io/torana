# Torana

[![Maven Central](https://img.shields.io/maven-central/v/io.torana/torana-spring-boot-starter)](https://central.sonatype.com/artifact/io.torana/torana-spring-boot-starter)
[![Build Status](https://github.com/torana-io/torana/workflows/CI/badge.svg)](https://github.com/torana-io/torana/actions)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

**Structured business audit trails for Spring Boot applications.**

Torana is an open-source library for auditing **explicit business actions** in Spring Boot systems.  
It is designed for teams that want to answer questions like:

- Who cancelled this order?
- Who approved this invoice?
- Which tenant did this happen in?
- From which request or trace did it originate?
- What changed?
- Can we query or export it later?

Torana is **not** an entity-history tool and **not** a generic logging framework.  
It focuses on **intentional, business-action-first auditing**.

## Why Torana exists

Existing tools usually solve different problems:

- **Spring Data JPA Auditing** gives entity authorship and timestamps.
- **Hibernate Envers** gives entity revision history.
- **Logs and traces** help debugging and observability.

Torana fills a different gap:

> recording meaningful business actions with structured context, optional diffs, redaction, and searchable/exportable audit records.

## Core principles

- Audit **business actions**, not low-level persistence events.
- Audit only **explicitly chosen critical actions**.
- Keep records **structured, queryable, and exportable**.
- Apply **redaction before persistence**.
- Provide **strong defaults with pluggable extension points**.
- Keep the **core framework-agnostic** and Spring integration natural.
- Keep overhead **predictable and bounded**.

## Quick Start

### 1. Add the dependency

**Maven:**
```xml
<dependency>
    <groupId>io.torana</groupId>
    <artifactId>torana-spring-boot-starter</artifactId>
    <version>0.1.10</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'io.torana:torana-spring-boot-starter:0.1.10'
```

### 2. Create the audit table

Torana requires an `audit_entries` table. Choose your approach:

**Option A: Use official migrations with Flyway or Liquibase (recommended)**

Torana provides official migration files for all supported databases:
- `torana-jdbc/src/main/resources/db/migration/postgresql/V1__create_audit_entries_table.sql`
- `torana-jdbc/src/main/resources/db/migration/mysql/V1__create_audit_entries_table.sql`
- `torana-jdbc/src/main/resources/db/migration/h2/V1__create_audit_entries_table.sql`

Copy the appropriate file to your project's `src/main/resources/db/migration/` directory.

See [Schema Management](docs/schema-management.md) for detailed setup instructions.

**Option B: Let Torana create it (development/testing only)**

```yaml
torana:
  schema-mode: create
```

### 3. That's it!

Torana auto-configures everything else:
- Detects your database (PostgreSQL, MySQL, or H2)
- Starts capturing `@AuditedAction` annotated methods

Just annotate your business methods.

## Example usage

```java
@AuditedAction(
    value = "order.cancelled",
    targetType = "Order",
    targetId = "#command.orderId"
)
@Transactional
public void cancelOrder(CancelOrderCommand command) {
    Order order = orderRepository.getById(command.orderId());
    order.cancel(command.reason());
}
```

The goal is that a team can later query something like:

- action = `order.cancelled`
- target = `Order:123`
- actor = `alice`
- tenant = `acme`
- requestId = `req-456`
- traceId = `trace-789`

## Configuration

Torana works out of the box with sensible defaults. All settings are optional:

```yaml
torana:
  enabled: true           # Enable/disable audit trail (default: true)
  table-name: audit_entries  # Table name (default: audit_entries)
  schema-mode: none       # Schema management: none, create, create-drop (default: none)

  redaction:
    enabled: true         # Enable sensitive data redaction (default: true)
    placeholder: "[REDACTED]"
    patterns:             # Regex patterns for field names to redact
      - "(?i)password"
      - "(?i)secret"
      - "(?i)token"
      - "(?i)ssn"

  snapshot:
    max-depth: 3          # Max object traversal depth (default: 3)
```

### Schema Management

Torana defaults to `schema-mode: none`, expecting you to manage the audit table schema yourself.

**Recommended approach by environment:**

| Environment | Mode | Migration Tool |
|-------------|------|----------------|
| **Production** | `none` | Flyway or Liquibase |
| **Development** | `create` | (optional) Torana auto-create |
| **Testing** | `create-drop` | Torana auto-create/drop |

#### Schema Modes

| Mode | Behavior | Use Case |
|------|----------|----------|
| `none` | No automatic schema management (default) | Production - manage with Flyway/Liquibase |
| `create` | Creates table if it doesn't exist | Local development and exploration |
| `create-drop` | Creates on startup, drops on shutdown | Integration tests |

#### Official Migration Files

Torana provides versioned migration files for all supported databases:

```
torana-jdbc/src/main/resources/db/migration/
├── postgresql/V1__create_audit_entries_table.sql
├── mysql/V1__create_audit_entries_table.sql
└── h2/V1__create_audit_entries_table.sql
```

For detailed setup instructions, migration strategies, and schema evolution policies, see:
- [Schema Management Guide](docs/schema-management.md)
- [Database Migrations](docs/migrations.md)

### Database Support

Torana automatically detects and configures the appropriate SQL dialect:

| Database | Dialect | JSON Storage |
|----------|---------|--------------|
| PostgreSQL | `PostgreSqlDialect` | JSONB |
| MySQL 8+ | `MySqlDialect` | JSON |
| H2 | `H2Dialect` | CLOB |

## Planned module structure

```text
torana-api
torana-spi
torana-core
torana-jdbc
torana-spring-aop
torana-spring-security
torana-spring-webmvc
torana-micrometer
torana-spring-boot-autoconfigure
torana-spring-boot-starter
torana-test
```

## Current status

Torana is an early production-candidate library for Spring Boot teams that want explicit business-action audit trails.

It is suitable for controlled adoption and pilot usage. Before using it in critical systems, review the transaction semantics, schema management, redaction configuration, and migration policy.

Features include:

- Public API with `@AuditedAction` annotation and programmatic `AuditRecord` builder
- Plain-Java core with Spring Boot integration modules
- JDBC-based append-only writer with PostgreSQL, MySQL, and H2 support
- Spring Security, Web MVC, and Micrometer integrations
- Configurable redaction policy for sensitive data
- Testing utilities for audit assertions

## What Torana is not trying to be

Torana is not intended to be:

- a replacement for all logging
- an Envers clone
- a generic compliance platform
- a full dashboard product on day one
- a magical auto-audit-everything library

## Long-term direction

The long-term vision is:

- open-source core library for Spring Boot teams
- optional self-hosted or hosted operational layer
- dashboard, search, governance, exports, and review workflows

## Documentation

- Schema management: [docs/schema-management.md](docs/schema-management.md)
- Database migrations: [docs/migrations.md](docs/migrations.md)
- Release process: [RELEASING.md](RELEASING.md)
- Contribution guide: [CONTRIBUTING.md](CONTRIBUTING.md)

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Apache License 2.0 - see [LICENSE](LICENSE) for details.
