# Contributing to Torana

Thanks for contributing.

Torana is intended to be a serious infrastructure library, so contributions should preserve clarity, correctness, and long-term maintainability.

## Before contributing

Read these first:

- `docs/00-vision/vision.md`
- `docs/00-vision/design-principles.md`
- `docs/02-architecture/architecture-overview.md`
- `docs/02-architecture/module-structure.md`
- `docs/07-decisions/ADR-001-business-action-first.md`
- `docs/07-decisions/ADR-002-plain-java-core-spring-adapters.md`
- `docs/07-decisions/ADR-003-jdbc-default-writer.md`
- `docs/07-decisions/ADR-004-explicit-opt-in-auditing.md`

## Core contribution rules

### 1. Preserve the product identity

Torana is built for:

- business-action-first auditing
- explicit opt-in auditing
- structured, queryable audit records
- strong defaults with extensibility

Do not introduce features that push the project toward:

- automatic auditing of every persistence event
- opaque magic
- framework-coupled core logic
- hidden performance costs

### 2. Keep the dependency direction clean

The architecture depends on clear module boundaries.

Use these rules:

- `torana-api` exposes user-facing API
- `torana-spi` exposes public extension points
- `torana-core` contains domain logic and orchestration
- adapters depend on core/spi
- Boot modules wire components together

Do not introduce cyclic dependencies.

### 3. Prefer composition over inheritance

Use:

- interfaces for extension points
- composition for orchestration
- immutable domain objects where possible

Avoid deep inheritance trees.

### 4. Keep the core framework-agnostic

The core should not depend directly on:

- Spring MVC
- Spring Security
- Micrometer
- JDBC-specific implementation details unless in adapter modules

Framework integration belongs in adapter modules.

### 5. Optimize for explicitness

Torana should be understandable from code and configuration.

Prefer:

- explicit contracts
- explicit configuration
- explicit action naming
- explicit lifecycle handling

Avoid surprising hidden behavior.

## Types of contributions

Useful contributions include:

- bug fixes
- documentation improvements
- tests
- performance improvements
- new extension points that preserve architecture
- Spring adapter improvements
- JDBC/query/export improvements

More sensitive contributions include:

- new modules
- public API changes
- SPI changes
- persistence model changes
- transaction semantics changes

For those, open an issue or design proposal first.

## Design proposals

If you want to propose a significant change, include:

- problem statement
- motivation
- alternatives considered
- impact on module boundaries
- impact on public API
- impact on performance
- migration considerations
- whether it conflicts with any ADR

## Coding expectations

### Java
- Prefer clear, boring, readable code.
- Avoid unnecessary cleverness.
- Favor immutability for domain objects.
- Keep public APIs small and intentional.
- Keep internal implementation details internal.

### Spring integration
- Use standard Spring Boot conventions.
- Use auto-configuration only for wiring and defaults.
- Use conditions and bean overrides predictably.
- Do not bury business logic inside configuration classes.

### Errors
- Fail clearly.
- Avoid silent fallback behavior unless it is explicitly documented.
- Prefer predictable behavior over magic.

## Testing expectations

Every non-trivial contribution should include tests at the right level.

### Unit tests
For:
- diff logic
- redaction logic
- model conversion
- policies
- small orchestration units

### Integration tests
For:
- Spring Boot auto-configuration
- annotation interception
- JDBC persistence
- transaction behavior
- resolver integration

### Documentation examples
Examples in docs should remain realistic and ideally compile or be easily validated.

## Performance expectations

Torana is an infrastructure library. Performance matters.

Contributions should avoid:

- unnecessary deep object traversal
- accidental lazy-loading explosions
- expensive default behavior
- hidden DB round-trips
- oversized payload capture by default

If a feature adds cost, it should be:

- explicit
- bounded
- configurable
- justified by clear value

## Backward compatibility

Public APIs and SPIs should be treated carefully.

If you change:

- annotations
- public interfaces
- configuration properties
- storage contracts
- extension point semantics

document the rationale and compatibility impact.

## Documentation expectations

If behavior changes, update the relevant docs.

Typical files to update:

- architecture docs
- API docs
- competitive analysis docs if positioning changes
- roadmap if scope changes
- ADRs if a major architectural decision changes

## Pull request checklist

Before opening a pull request, make sure:

- the change aligns with the design principles
- module boundaries remain clean
- no dependency cycles were introduced
- tests were added or updated
- docs were updated if needed
- the change is scoped and understandable
- any major design trade-offs are explained

## Branch naming

Branch names should follow the pattern `<type>/<short-description>` using kebab-case.

### Patterns

| Pattern | Purpose | Example |
|---------|---------|---------|
| `feat/*` | New features | `feat/oauth2-resolver` |
| `fix/*` | Bug fixes | `fix/null-actor-handling` |
| `docs/*` | Documentation | `docs/installation-guide` |
| `chore/*` | Maintenance | `chore/update-spring-boot` |
| `refactor/*` | Refactoring | `refactor/audit-pipeline` |
| `test/*` | Test improvements | `test/jdbc-integration` |
| `perf/*` | Performance | `perf/batch-insert` |

### Rules

- Use lowercase with hyphens (kebab-case)
- Keep descriptions short but descriptive
- Avoid special characters except hyphens
- Branch type should match the primary commit type

### Examples

```
feat/tenant-filtering
fix/postgres-jsonb-serialization
docs/query-executor-examples
chore/upgrade-testcontainers-2
refactor/extract-dialect-interface
```

## Commit messages

We use [Conventional Commits](https://www.conventionalcommits.org/) for automated versioning and changelog generation.

### Format

```
<type>: <description>

[optional body]

[optional footer(s)]
```

### Types

- `feat:` - New features (bumps minor version)
- `fix:` - Bug fixes (bumps patch version)
- `docs:` - Documentation changes
- `chore:` - Maintenance tasks (dependencies, build config)
- `refactor:` - Code refactoring (no functional changes)
- `test:` - Test additions or improvements
- `perf:` - Performance improvements

### Breaking changes

Add `!` after type (e.g., `feat!:`) or include `BREAKING CHANGE:` in the footer.

### Examples

```
feat: add OAuth2 actor resolver

fix: handle null actor in AuditPipeline

docs: improve installation guide

feat!: rename AuditRecord.getAction() to AuditRecord.action()

BREAKING CHANGE: Method signature changed for consistency with records.

chore: update Spring Boot to 4.1.0
```

## Pull request style

Prefer small, focused pull requests.

Good PRs usually:
- solve one clear problem
- include tests
- include docs where relevant
- explain trade-offs briefly and clearly
- use conventional commit messages

## Communication

When in doubt, open an issue first for discussion, especially for:
- new modules
- public API changes
- SPI changes
- persistence model changes
- transaction model changes

## Project direction

Torana is intended to grow into a trustworthy open-source foundation for business auditing in Spring Boot systems.

Contributions that make it:
- clearer
- safer
- more extensible
- more predictable
- more useful in real business systems

are strongly aligned with the project.
