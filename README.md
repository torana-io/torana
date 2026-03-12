# Torana

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

## Example direction

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

## Repository layout

```text
/docs
  /00-vision
  /01-product
  /02-architecture
  /03-domain
  /04-api
  /05-competitive-analysis
  /06-roadmap
  /07-decisions
```

## Current status

Torana is currently in the design and architecture phase.

The initial goals are:

- define the public API and extension model
- build a plain-Java core
- add Spring Boot integration modules
- provide a JDBC-based append-only writer
- support explicit action auditing through annotations and programmatic APIs

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

TBD
