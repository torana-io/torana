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

## Installation

Add the starter dependency to your project:

**Maven:**
```xml
<dependency>
    <groupId>io.torana</groupId>
    <artifactId>torana-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

**Gradle:**
```groovy
implementation 'io.torana:torana-spring-boot-starter:0.1.0'
```

That's it! The starter includes all necessary modules.

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

Torana has completed its initial development phase and is ready for production use.

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

Start here:

- `docs/00-vision/vision.md`
- `docs/00-vision/design-principles.md`
- `docs/02-architecture/architecture-overview.md`
- `docs/02-architecture/module-structure.md`
- `docs/05-competitive-analysis/versus-envers.md`
- `docs/05-competitive-analysis/versus-jpa-auditing.md`

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

Apache License 2.0 - see [LICENSE](LICENSE) for details.
