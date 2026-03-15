# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Initial release of Torana audit framework
- Core API with `@AuditedAction` annotation and `AuditRecord` builder
- SPI interfaces: `ActorResolver`, `TenantResolver`, `RequestContextResolver`, `TraceResolver`, `SnapshotProvider`, `DiffEngine`, `RedactionPolicy`, `AuditWriter`
- JDBC-based audit writer with PostgreSQL, MySQL, and H2 support
- Spring Boot auto-configuration
- Spring Security integration for actor resolution
- Spring Web MVC integration for request context
- Micrometer integration for trace context
- Diff engine for change tracking
- Configurable redaction policy with sensible defaults
- Testing utilities (`InMemoryAuditWriter`, `FakeActorResolver`, `FakeTenantResolver`, `AuditAssertions`)

### Security

- Default redaction patterns for sensitive fields (password, token, ssn, cvv, etc.)
