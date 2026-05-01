# ADR 0001: Bootstrap stack

## Status

Accepted

## Context

The EGSYS technical test requires a Kotlin and Spring Boot REST API. The delivery target is production-grade rather than
challenge-only code, with security, performance, maintainability, and creativity treated as architectural filters.

## Decision

Use Kotlin 2.x on JVM 21, Spring Boot 3.5.x, Gradle Kotlin DSL, PostgreSQL, Redis, Flyway, Spring Security, JJWT, Argon2id
via Bouncy Castle, springdoc-openapi, Micrometer, OpenTelemetry, ktlint, detekt, JaCoCo, Pitest, Testcontainers, and
ArchUnit.

The codebase starts with a hexagonal package structure:

- `domain` is pure Kotlin.
- `application` orchestrates use cases and depends only on `domain`.
- `infrastructure` contains driven adapters.
- `web` contains HTTP adapters.

## Consequences

The bootstrap has more dependencies than a minimal CRUD project, but it gives the later security, observability, testing,
and deployment stages a stable place to land without retrofitting the foundation.
