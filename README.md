# teste-api-spring-egsys

API RESTful de tarefas em Kotlin e Spring Boot para o teste tecnico da EGSYS.

## Estado Atual

Etapas 0, 1, 2, 3, 4 e 5 concluidas: bootstrap, dominio puro em TDD, persistencia PostgreSQL, casos de uso,
API REST v1 e seguranca com JWT RS256, Argon2id, RBAC, anti-IDOR, revogacao e rate limiting.

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
./gradlew pitest
```

## API REST v1

Swagger UI: `/swagger-ui.html`

Endpoints implementados:

- `POST /api/v1/auth/register`
- `POST /api/v1/auth/login`
- `POST /api/v1/auth/refresh`
- `POST /api/v1/auth/logout`
- `POST /api/v1/auth/revoke-all/{userId}` (`ROLE_ADMIN`)
- `GET /api/v1/categorias`
- `POST /api/v1/tarefas`
- `GET /api/v1/tarefas/{id}`
- `GET /api/v1/tarefas?cursor={cursor}&limit={1..100}`
- `PUT /api/v1/tarefas/{id}`
- `DELETE /api/v1/tarefas/{id}`

Erros HTTP usam `application/problem+json` via RFC 7807 `ProblemDetail`. As listagens de tarefas usam paginacao
cursor-based, evitando offset em colecoes grandes.

## Superficie de Ataque

| Vetor | Defesa implementada | Teste |
| --- | --- | --- |
| JWT `alg=none` / HS256 | rejeicao explicita de algoritmo diferente de RS256 antes da assinatura | `AuthenticationTests` |
| Token expirado, adulterado, iss/aud errado ou JTI revogado | validacao rigorosa de claims + blacklist Redis por JTI | `AuthenticationTests` |
| Refresh token reutilizado | rotacao a cada uso e revogacao da familia | `RefreshTokenReuseTests` |
| IDOR em tarefas | `owner_id` no dominio, use cases e queries de repositorio | `TarefaJpaRepositoryIntegrationTest` |
| Acesso USER a endpoint ADMIN | RBAC com `@PreAuthorize` | `AuthorizationAndHeadersTests` |
| Brute force | rate limiting por IP/usuario com `Retry-After` | `AuthorizationAndHeadersTests` |
| Headers ausentes | CSP, HSTS, no-sniff, frame deny, no-referrer, permissions policy | `AuthorizationAndHeadersTests` |

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
| [0003](docs/adr/0003-api-rest-problemdetail-cursor.md) | API REST com ProblemDetail e cursor pagination |
| [0004](docs/adr/0004-seguranca-jwt-rs256-rbac.md) | Seguranca JWT RS256, RBAC e anti-IDOR |
