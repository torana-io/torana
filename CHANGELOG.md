# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.8](https://github.com/torana-io/torana/compare/v0.1.7...v0.1.8) (2026-04-09)


### Features

* Add manual publish workflow for Maven Central ([45daa9f](https://github.com/torana-io/torana/commit/45daa9fd66d28d3faac3b34c5a26e00471aab63d))

## [0.1.7](https://github.com/torana-io/torana/compare/v0.1.6...v0.1.7) (2026-04-09)


### Features

* Improve ci/cd pipelines for better release ([711571d](https://github.com/torana-io/torana/commit/711571d2e56ff6085e940362686148cc39ffe1e2))

## [0.1.6](https://github.com/torana-io/torana/compare/v0.1.5...v0.1.6) (2026-04-09)


### Features

* Update README with new documentation status ([#27](https://github.com/torana-io/torana/issues/27)) ([0c374ab](https://github.com/torana-io/torana/commit/0c374ab56dafc047581d0d17dcbcbc88c665eaf6))


### Bug Fixes

* Edit pom.xml ([f928e1f](https://github.com/torana-io/torana/commit/f928e1fd5f1d5c119da3e303569bf88d7439e134))

## [0.1.5](https://github.com/torana-io/torana/compare/v0.1.4...v0.1.5) (2026-03-15)


### Features

* add automatic Spring Security and WebMVC integration ([c752db4](https://github.com/torana-io/torana/commit/c752db4c122d72aa92055a8c9654a1c5ac5bd849))

## [0.1.4](https://github.com/torana-io/torana/compare/v0.1.3...v0.1.4) (2026-03-15)


### Features

* Conditional wiring on spring security and telemetry ([9884436](https://github.com/torana-io/torana/commit/988443622d950f2f2411a2d742e9485b663f1c3d))

## [0.1.3](https://github.com/torana-io/torana/compare/v0.1.2...v0.1.3) (2026-03-15)


### Features

* Have the database already created ([dc8e3fd](https://github.com/torana-io/torana/commit/dc8e3fdec3e53921be458947ea027a0741d368db))

## [0.1.2](https://github.com/torana-io/torana/compare/v0.1.1...v0.1.2) (2026-03-15)


### Bug Fixes

* use Central Portal publishing instead of legacy OSSRH ([7e168b7](https://github.com/torana-io/torana/commit/7e168b7655cac71a005bbfb531c51a3f5a336cfb))

## [0.1.1](https://github.com/torana-io/torana/compare/v0.1.0...v0.1.1) (2026-03-15)


### Features

* initial release of Torana audit framework ([77bef22](https://github.com/torana-io/torana/commit/77bef22816c5701703f39d9b239a0753b0a8780e))
* initial release of Torana audit framework ([27b3988](https://github.com/torana-io/torana/commit/27b3988171369b6aa909de132a95bac736d48f37))
* Update actions token for release ([99efc93](https://github.com/torana-io/torana/commit/99efc93f131efa39e4174eba1aa147fa131b054e))

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
