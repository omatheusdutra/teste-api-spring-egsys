# teste-api-spring-egsys

API RESTful de tarefas em Kotlin e Spring Boot para o teste tecnico da EGSYS.

## Estado Atual

Etapas 0, 1 e 2 concluidas: bootstrap, dominio puro em TDD e persistencia PostgreSQL com Flyway, JPA e Testcontainers.

## Stack Base

- Kotlin 2.x, JVM 21, Spring Boot 3.5.x
- Gradle Kotlin DSL
- PostgreSQL, Redis, Spring Data JPA, Flyway
- Spring Security, JJWT, Bouncy Castle
- JUnit 5, Kotest assertions, MockK, Testcontainers, ArchUnit
- ktlint, detekt, JaCoCo, Pitest

## Como Testar

Pre-requisitos: JDK 21. Docker e necessario para executar os testes de persistencia com Testcontainers; sem Docker, a
tag `postgres` e excluida automaticamente do `test` local.

```bash
./gradlew check
```

## Arquitetura

```mermaid
C4Container
title EGSYS Tasks API - bootstrap

Person(user, "Usuario autenticado")
System_Boundary(api, "Tasks API") {
  Container(web, "Web Adapter", "Spring MVC", "Controllers, DTOs, ProblemDetail")
  Container(app, "Application", "Kotlin", "Casos de uso")
  Container(domain, "Domain", "Kotlin puro", "Entidades, VOs, eventos e portas")
  Container(infra, "Infrastructure", "Spring/JPA/Redis", "Persistencia, seguranca, mensageria")
}
SystemDb(postgres, "PostgreSQL 16", "Dados transacionais")
SystemDb(redis, "Redis 7", "Token store, cache e rate limiting")

Rel(user, web, "HTTPS JSON")
Rel(web, app, "invoca")
Rel(app, domain, "usa")
Rel(infra, domain, "implementa portas")
Rel(infra, postgres, "JDBC")
Rel(infra, redis, "RESP")
```

## ADRs

| ADR | Decisao |
| --- | --- |
| [0001](docs/adr/0001-bootstrap-stack.md) | Bootstrap stack |
| [0002](docs/adr/0002-persistencia-postgresql-flyway-jpa.md) | Persistencia PostgreSQL, Flyway e JPA |
