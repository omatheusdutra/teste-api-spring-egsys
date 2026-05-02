# teste-api-spring-egsys

API RESTful de tarefas em Kotlin e Spring Boot para o teste tecnico da EGSYS.

## Estado Atual

Etapas 0, 1, 2, 3, 4, 5, 6 e 7 concluidas: bootstrap, dominio puro em TDD, persistencia PostgreSQL, casos de uso,
API REST v1, seguranca com JWT RS256, Argon2id, RBAC, anti-IDOR, revogacao/rate limiting e observabilidade com
logs JSON, correlation ID, Prometheus autenticado, tracing OTLP, health checks customizados e inovacoes com status,
Outbox, historico auditavel e CSV.

## Stack Base

- Kotlin 2.x, JVM 21, Spring Boot 3.5.x
- Gradle Kotlin DSL
- PostgreSQL, Redis, Spring Data JPA, Flyway
- Spring Security, JJWT, Bouncy Castle
- JUnit 5, Kotest assertions, MockK, Testcontainers, ArchUnit
- ktlint, detekt, JaCoCo, Pitest
- Micrometer, Prometheus, OpenTelemetry e logs JSON

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
- `GET /api/v1/tarefas/export.csv`
- `PUT /api/v1/tarefas/{id}`
- `POST /api/v1/tarefas/{id}/status/{em-andamento|concluida|cancelada}`
- `GET /api/v1/tarefas/{id}/historico`
- `DELETE /api/v1/tarefas/{id}`
- `GET /actuator/health` (autenticado)
- `GET /actuator/prometheus` (autenticado)

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
| Correlation ID malicioso | regex allowlist, tamanho maximo e fallback para UUID servidor | `CorrelationIdFilterTest` |
| Vazamento de metricas internas | `/actuator/prometheus` exige JWT | `AuthorizationAndHeadersTests` |
| Perda de evento apos commit | Outbox transacional em `outbox_events` | `TarefaJpaRepositoryIntegrationTest` |
| Auditoria filtrada no cliente | Historico filtra por `owner_id` no repositorio/use case | `RestApiWebTest` |
| CSV injection / quebra de formato | campos CSV com aspas, virgulas e quebras sao escapados | `RestApiWebTest` |

## Inovacoes Entregues

| Diferencial | Implementacao |
| --- | --- |
| Soft delete com trilha de auditoria | `excluida_em` + evento `TarefaExcluida` em historico/outbox |
| Status de tarefa com maquina de estados | Dominio valida transicoes; API expõe endpoint de status |
| Eventos de dominio + Outbox Pattern | `outbox_events` gravado na mesma transacao do aggregate |
| Historico de mudancas por tarefa | `tarefa_historico` consultavel por tarefa e usuario |
| Exportacao CSV | `GET /api/v1/tarefas/export.csv` |

## Observabilidade

- Logs JSON no console via `logback-spring.xml`, com `correlationId` e `userId` no MDC quando presentes.
- `X-Correlation-Id` e propagado em toda resposta; valores invalidos sao descartados.
- Metricas Prometheus disponiveis em `/actuator/prometheus`, protegidas por JWT.
- Traces saem via OTLP em `OTEL_EXPORTER_OTLP_ENDPOINT` com sampling configuravel por `EGSYS_TRACING_SAMPLE_PROBABILITY`.
- Health checks customizados validam PostgreSQL (`SELECT 1`) e Redis (`PING`).

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
| [0005](docs/adr/0005-observabilidade-prometheus-otel.md) | Observabilidade com logs JSON, Prometheus e OTLP |
| [0006](docs/adr/0006-inovacoes-outbox-historico-status-csv.md) | Inovacoes com Outbox, historico, status e CSV |
