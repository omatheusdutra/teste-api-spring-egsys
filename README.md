# 🚀 EGSYS Tasks API

[![CI](https://github.com/omatheusdutra/teste-api-spring-egsys/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/omatheusdutra/teste-api-spring-egsys/actions/workflows/ci.yml)
[![Security](https://github.com/omatheusdutra/teste-api-spring-egsys/actions/workflows/security.yml/badge.svg?branch=main)](https://github.com/omatheusdutra/teste-api-spring-egsys/actions/workflows/security.yml)
[![Release](https://img.shields.io/github/v/tag/omatheusdutra/teste-api-spring-egsys?label=release&sort=semver)](https://github.com/omatheusdutra/teste-api-spring-egsys/releases/tag/v1.0.0)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)

API RESTful de tarefas em Kotlin + Spring Boot, desenhada como backend production-ready para o teste tecnico da EGSYS:
segura por padrao, observavel, testavel e pronta para rodar em containers.

## ✅ Estado Atual

Etapas 0 a 9 concluidas: bootstrap, dominio puro em TDD, persistencia PostgreSQL, casos de uso,
API REST v1, seguranca com JWT RS256, Argon2id, RBAC, anti-IDOR, revogacao/rate limiting e observabilidade com
logs JSON, correlation ID, Prometheus autenticado, tracing OTLP, health checks customizados e inovacoes com status,
Outbox, historico auditavel e CSV. A entrega inclui Dockerfile distroless, docker-compose, Makefile, Bruno collection e
workflows de CI/seguranca. O projeto tambem inclui playground interativo como demo controlada e pentest defensivo reproduzivel
com 49 cenarios.

## 🧱 Stack Base

- Kotlin 2.x, JVM 21, Spring Boot 3.5.x
- Gradle Kotlin DSL
- PostgreSQL, Redis, Spring Data JPA, Flyway
- Spring Security, JJWT, Bouncy Castle
- JUnit 5, Kotest assertions, MockK, Testcontainers, ArchUnit
- ktlint, detekt, JaCoCo, Pitest
- Micrometer, Prometheus, OpenTelemetry e logs JSON

## 🧪 Como Testar

Pre-requisitos: JDK 21. Docker e necessario para executar os testes de persistencia com Testcontainers; sem Docker, a
tag `postgres` e excluida automaticamente do `test` local.

```bash
./gradlew check
./gradlew pitest
```

Pentest defensivo local:

```bash
rm -f tools/pentest/.results.tsv
eval "$(bash tools/pentest/setup.sh)"
bash tools/pentest/01-auth-idor.sh
bash tools/pentest/02-input-sqli-xss.sh
bash tools/pentest/03-rate-headers-playground.sh
```

## 🛡️ Gates de Qualidade

| Gate | Status local |
| --- | --- |
| `./gradlew check` | Verde |
| `./gradlew pitest` | Verde, mutation score 70% |
| JaCoCo | Verde via `check`, minimo 85% linhas e 80% branches |
| Trivy, Semgrep e gitleaks | Configurados no workflow `.github/workflows/security.yml` |

## 🐳 Como Executar

Subir dependencias e API em containers:

```bash
docker compose up -d --build
```

Rodar a API local usando Postgres/Redis do compose:

```bash
docker compose up -d postgres redis
./gradlew bootRun
```

Rodar com playground interativo e demonstracoes ofensivas habilitadas:

```bash
docker compose up -d postgres redis
SPRING_PROFILES_ACTIVE=dev EGSYS_DB_PASSWORD=egsys_local_password ./gradlew bootRun
```

Comandos curtos tambem estao no `Makefile`: `make check`, `make pitest`, `make compose-up`, `make docker-build`.

Para o Prometheus raspar `/actuator/prometheus`, gere um token admin local e substitua o placeholder de
`config/prometheus/secrets/api-token.example` no formato `Bearer <token>` no seu ambiente local.

## ⚡ Tour de 30s

1. Abra `http://localhost:8080/playground` ou use o botao "Abrir Playground Interativo" na home publica.
2. Registre ou faca login pelo painel de auth.
3. Crie uma tarefa, liste, altere status e abra o historico.
4. Em `dev`/`local`, use "Provoque uma defesa" para ver rate limit, IDOR, JWT adulterado, SQLi e mass assignment sendo bloqueados.

Alternativa via cliente HTTP: abra a colecao Bruno `bruno/egsys-tasks-api`, execute `01 Register`, `02 Login`,
`03 Create Task` e `04 List Tasks`.

## Playground Interativo

O playground fica em `/playground` e tambem pode ser acessado pelo CTA futurista da home publica. Ele nao mora em
`static/`; e servido por `PlaygroundController` somente quando `egsys.playground.enabled=true`, conforme
[ADR 0009](docs/adr/0009-playground-production-demo-controlled.md).

Em producao, o playground funciona como demo controlada: auth, CRUD e visualizacao da API ficam disponiveis, mas o
painel "Provoque uma defesa" e bloqueado por `egsys.playground.attack-demos-enabled=false`. O pentest ofensivo completo
permanece em `dev`/`local`/sandbox.

- Auth completo: registrar, login, refresh, logout e countdown do JWT.
- CRUD/tour de tarefas: categorias, criacao, listagem, status, historico e CSV.
- Painel Request/Response com copiar como `curl` e repetir request.
- Demonstracoes defensivas: rate limit, IDOR, token adulterado/expirado, SQLi e mass assignment, habilitadas apenas em ambiente controlado.
- Hardening frontend: token apenas em memoria JS, sem `localStorage`, sem `sessionStorage`, sem `eval`, sem inline handlers.

## 📚 API REST v1

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
- `GET /playground` (demo controlada por `egsys.playground.enabled`)
- `GET /playground/config` (informa se demos ofensivas estao habilitadas)

Erros HTTP usam `application/problem+json` via RFC 7807 `ProblemDetail`. As listagens de tarefas usam paginacao
cursor-based, evitando offset em colecoes grandes.

## 🧨 Superficie de Ataque

Pentest interno reproduzivel em `tools/pentest/` confirmado em 2026-05-02. Relatorio:
[docs/pentest/REPORT-2026-05-02.md](docs/pentest/REPORT-2026-05-02.md).

| Vetor | Defesa implementada | Teste automatizado | Pentest interno |
| --- | --- | --- | --- |
| JWT `alg=none` / HS256 | rejeicao explicita de algoritmo diferente de RS256 antes da assinatura | `AuthenticationTests` | `01-auth-idor.sh` #5-6 PASS |
| Token expirado, adulterado, iss/aud errado ou JTI revogado | validacao rigorosa de claims + blacklist Redis por JTI | `AuthenticationTests` | `01-auth-idor.sh` #3-10 PASS |
| Refresh token reutilizado | rotacao a cada uso e revogacao da familia | `RefreshTokenReuseTests` | `01-auth-idor.sh` #11 PASS |
| IDOR em tarefas | `owner_id` no dominio, use cases e queries de repositorio | `TarefaJpaRepositoryIntegrationTest` | `01-auth-idor.sh` #12-14 PASS |
| Acesso USER a endpoint ADMIN | RBAC com `@PreAuthorize` | `AuthorizationAndHeadersTests` | `01-auth-idor.sh` #15-16 PASS |
| Mass assignment | DTOs explicitos e `ignoreUnknown=false` | `MassAssignmentTests` | `02-input-sqli-xss.sh` #17-19 PASS |
| Payload bombing / JSON malformado | limites de tamanho + Bean Validation + Jackson constraints | `InputValidationTests` | `02-input-sqli-xss.sh` #20-26 PASS |
| SQL injection | JPA/JPQL parametrizado e cursor opaco | `SqlInjectionTests` | `02-input-sqli-xss.sh` #27-30 PASS |
| XSS/reflection | JSON `Content-Type`, `nosniff` e playground com `textContent` | `XssReflectionTests`, `PlaygroundFrontendTest` | `02-input-sqli-xss.sh` #31-33 PASS |
| Brute force | rate limiting por IP/usuario com `Retry-After` | `AuthorizationAndHeadersTests` | `03-rate-headers-playground.sh` #34-35 PASS |
| Headers ausentes | CSP, HSTS, no-sniff, frame deny, no-referrer, permissions policy | `AuthorizationAndHeadersTests` | `03-rate-headers-playground.sh` #38-41 PASS/INFO |
| Stack trace/info leak | ProblemDetail sem stack trace e `Server` removido | `InfoLeakTests` | `03-rate-headers-playground.sh` #41-42 PASS |
| Correlation ID malicioso | regex allowlist, tamanho maximo e fallback para UUID servidor | `CorrelationIdFilterTest` | coberto por teste |
| Vazamento de metricas internas | `/actuator/prometheus` exige JWT | `AuthorizationAndHeadersTests` | `01-auth-idor.sh` #15 PASS |
| Playground ofensivo em prod | demo permitida por propriedade, mas demos ofensivas bloqueadas por `attack-demos-enabled=false`; HTML fora de `static/` | `PlaygroundAvailabilityTests`, `PlaygroundFrontendTest` | `03-rate-headers-playground.sh` #44-49 PASS |
| Perda de evento apos commit | Outbox transacional em `outbox_events` | `TarefaJpaRepositoryIntegrationTest` | coberto por teste |
| Auditoria filtrada no cliente | Historico filtra por `owner_id` no repositorio/use case | `RestApiWebTest` | coberto por teste |
| CSV injection / quebra de formato | campos CSV com aspas, virgulas e quebras sao escapados | `RestApiWebTest` | coberto por teste |

## ✨ Inovacoes Entregues

| Diferencial | Implementacao |
| --- | --- |
| Soft delete com trilha de auditoria | `excluida_em` + evento `TarefaExcluida` em historico/outbox |
| Status de tarefa com maquina de estados | Dominio valida transicoes; API expoe endpoint de status |
| Eventos de dominio + Outbox Pattern | `outbox_events` gravado na mesma transacao do aggregate |
| Historico de mudancas por tarefa | `tarefa_historico` consultavel por tarefa e usuario |
| Exportacao CSV | `GET /api/v1/tarefas/export.csv` |

## 📈 Observabilidade

- Logs JSON no console via `logback-spring.xml`, com `correlationId` e `userId` no MDC quando presentes.
- `X-Correlation-Id` e propagado em toda resposta; valores invalidos sao descartados.
- Metricas Prometheus disponiveis em `/actuator/prometheus`, protegidas por JWT.
- Traces saem via OTLP em `OTEL_EXPORTER_OTLP_ENDPOINT` com sampling configuravel por `EGSYS_TRACING_SAMPLE_PROBABILITY`.
- Health checks customizados validam PostgreSQL (`SELECT 1`) e Redis (`PING`).

## 🏗️ Arquitetura

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

## 📝 ADRs

| ADR | Decisao |
| --- | --- |
| [0001](docs/adr/0001-bootstrap-stack.md) | Bootstrap stack |
| [0002](docs/adr/0002-persistencia-postgresql-flyway-jpa.md) | Persistencia PostgreSQL, Flyway e JPA |
| [0003](docs/adr/0003-api-rest-problemdetail-cursor.md) | API REST com ProblemDetail e cursor pagination |
| [0004](docs/adr/0004-seguranca-jwt-rs256-rbac.md) | Seguranca JWT RS256, RBAC e anti-IDOR |
| [0005](docs/adr/0005-observabilidade-prometheus-otel.md) | Observabilidade com logs JSON, Prometheus e OTLP |
| [0006](docs/adr/0006-inovacoes-outbox-historico-status-csv.md) | Inovacoes com Outbox, historico, status e CSV |
| [0007](docs/adr/0007-devex-deploy-local.md) | DevEx e deploy local |
| [0008](docs/adr/0008-playground-dev-only.md) | Playground interativo dev-only (substituida) |
| [0009](docs/adr/0009-playground-production-demo-controlled.md) | Playground como demo controlada por propriedades |

## 🗺️ Roadmap Futuro

| Item | Motivo |
| --- | --- |
| Worker de Outbox | publicar notificacoes/email/WebSocket com retry e idempotencia |
| Importacao CSV/iCal | fechar ciclo de interoperabilidade de tarefas |
| Dashboards Grafana versionados | entregar paineis prontos alem do datasource |
| Push de imagem GHCR | publicar imagem assinada em tags `v*` |
| OWASP Dependency-Check com NVD API key | tornar SCA menos sujeito a rate limit externo |
