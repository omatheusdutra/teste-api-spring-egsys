# EGSYS Tasks API

[![CI](https://github.com/omatheusdutra/teste-api-spring-egsys/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/omatheusdutra/teste-api-spring-egsys/actions/workflows/ci.yml)
[![Security](https://github.com/omatheusdutra/teste-api-spring-egsys/actions/workflows/security.yml/badge.svg?branch=main)](https://github.com/omatheusdutra/teste-api-spring-egsys/actions/workflows/security.yml)
[![Release](https://img.shields.io/github/v/tag/omatheusdutra/teste-api-spring-egsys?label=release&sort=semver)](https://github.com/omatheusdutra/teste-api-spring-egsys/tags)
![Kotlin](https://img.shields.io/badge/Kotlin-2.x-7F52FF?logo=kotlin&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5.x-6DB33F?logo=springboot&logoColor=white)
![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)

API RESTful de tarefas em Kotlin + Spring Boot, desenvolvida para o teste tecnico da EGSYS com foco em seguranca, arquitetura limpa, observabilidade e testes automatizados.

## Destaques

- CRUD completo de tarefas e listagem de categorias.
- Autenticacao com JWT RS256, refresh token rotation, revogacao por Redis, Argon2id e RBAC.
- Anti-IDOR por ownership no servidor, DTOs explicitos, Bean Validation e erros RFC 7807 `ProblemDetail`.
- Soft delete, maquina de estados, historico auditavel, Outbox Pattern e exportacao CSV.
- Logs JSON com correlation ID, metricas Prometheus protegidas, tracing OTLP e health checks customizados.
- Docker Compose com API, PostgreSQL, Redis, Prometheus e Grafana.
- Playground interativo em `/playground`, com demo controlada para avaliacao funcional.

## Stack

- Kotlin 2.x, JVM 21, Spring Boot 3.5.x e Gradle Kotlin DSL.
- PostgreSQL 16, Redis 7, Spring Data JPA, Hibernate e Flyway.
- Spring Security 6, JJWT, Bouncy Castle e Jakarta Bean Validation.
- JUnit 5, Kotest assertions, MockK, Testcontainers, ArchUnit, JaCoCo e Pitest.
- Micrometer, Prometheus, OpenTelemetry e Logback JSON.
- Dockerfile multi-stage/distroless, GitHub Actions, Trivy, Semgrep e gitleaks.

## Como Executar

Subir tudo em containers:

```bash
docker compose up -d --build
```

Rodar a API local usando Postgres/Redis do Compose:

```bash
docker compose up -d postgres redis
./gradlew bootRun
```

Habilitar demonstracoes ofensivas do playground em ambiente controlado:

```bash
docker compose up -d postgres redis
SPRING_PROFILES_ACTIVE=dev EGSYS_DB_PASSWORD=egsys_local_password ./gradlew bootRun
```

Comandos curtos tambem estao no `Makefile`: `make check`, `make pitest`, `make compose-up`, `make docker-build`.

## Demo Online

A aplicacao esta publicada em:

- Home: <https://egsys-tasks-api.onrender.com>
- Playground: <https://egsys-tasks-api.onrender.com/playground>
- OpenAPI: <https://egsys-tasks-api.onrender.com/swagger-ui.html>

A demo publica roda com perfil `prod`: autenticacao, CRUD e tour visual ficam disponiveis, enquanto os cenarios ofensivos do playground ficam ocultos. O passo a passo de deploy e operacao fica em [deploy/README.md](deploy/README.md).

## Tour de 30s

1. Abra `http://localhost:8080` e clique em **Abrir Playground Interativo**, ou acesse `http://localhost:8080/playground`.
2. Registre um usuario ou faca login pelo painel de autenticacao.
3. Crie uma tarefa, liste, altere status, consulte historico e exporte CSV.
4. Em `dev`/`local`, habilite as demos ofensivas para ver a API bloqueando cenarios de ataque.

Alternativa via cliente HTTP: importe a colecao Bruno em `bruno/egsys-tasks-api`.

## Como Testar

```bash
./gradlew check
./gradlew pitest
```

Validacao defensiva local:

```bash
rm -f tools/pentest/.results.tsv
eval "$(bash tools/pentest/setup.sh)"
bash tools/pentest/01-auth-idor.sh
bash tools/pentest/02-input-sqli-xss.sh
bash tools/pentest/03-rate-headers-playground.sh
```

## Playground Interativo

O playground e servido por `PlaygroundController` quando `egsys.playground.enabled=true`. O HTML fica fora de `resources/static`, evitando exposicao acidental pelo static resource handler.

Em producao, ele funciona como demo controlada: autenticacao, CRUD e visualizacao continuam disponiveis, mas o painel ofensivo fica oculto por `egsys.playground.attack-demos-enabled=false`. Em `dev`/`local`, os botoes ofensivos ficam ativos para validacao defensiva.

Garantias do frontend:

- token apenas em memoria JS;
- sem `localStorage`, `sessionStorage`, `eval` ou handlers inline;
- renderizacao com `createElement`/`textContent`;
- CSP centralizada no Spring Security por header HTTP, com scripts restritos a `'self'`;
- fontes auto-hospedadas em `/assets/fonts`, sem Google Fonts/CDN;
- `style-src 'unsafe-inline'` mantido como concessao controlada para o CSS embutido da home publica;
- suporte a `prefers-reduced-motion` e navegacao acessivel por teclado.

### Demonstracoes defensivas

Em `dev`/`local`, as demos ofensivas executam chamadas reais contra a propria API e exibem status code, latencia e camada defensiva acionada. Em producao, esse painel fica oculto.

## API REST v1

Swagger UI: `/swagger-ui.html`

Endpoints principais:

- Auth: `POST /api/v1/auth/register`, `/login`, `/refresh`, `/logout`, `/revoke-all/{userId}`.
- Categorias: `GET /api/v1/categorias`.
- Tarefas: `POST /api/v1/tarefas`, `GET /api/v1/tarefas`, `GET /api/v1/tarefas/{id}`, `PUT /api/v1/tarefas/{id}`, `DELETE /api/v1/tarefas/{id}`.
- Status, historico e exportacao: `POST /api/v1/tarefas/{id}/status/{status}`, `GET /api/v1/tarefas/{id}/historico`, `GET /api/v1/tarefas/export.csv`.
- Observabilidade: `GET /actuator/health`, `GET /actuator/prometheus`.
- Demo: `GET /playground`, `GET /playground/config`.

Listagens usam paginacao cursor-based e erros HTTP usam RFC 7807 `ProblemDetail`.

## Superficie de Ataque

| Vetor | Defesa | Evidencia |
| --- | --- | --- |
| JWT `alg=none` / HS256 | rejeicao explicita de algoritmos nao RS256 | `AuthenticationTests`, playground demo |
| Token expirado, adulterado ou revogado | claims rigorosas + blacklist Redis por JTI | `AuthenticationTests`, `JwtReplayTests` |
| Refresh token reutilizado | rotacao e revogacao da familia | `RefreshTokenReuseTests` |
| IDOR em tarefas | ownership em use cases e queries por `owner_id` | `IdorTests`, playground demo |
| Mass assignment | DTOs explicitos e `ignoreUnknown=false` | `MassAssignmentTests`, playground demo |
| SQL injection | JPA/JPQL parametrizado e cursor opaco | `SqlInjectionTests`, playground demo |
| XSS/reflection | JSON correto, `nosniff`, CSP e `textContent` no playground | `XssReflectionTests`, `PlaygroundFrontendTest` |
| Brute force / abuso | rate limiting por IP/usuario com `Retry-After` | `BruteForceTests`, playground demo |
| Info leak | ProblemDetail sem stack trace e header `Server` removido | `InfoLeakTests` |
| Metricas internas | `/actuator/prometheus` exige JWT | `AuthorizationAndHeadersTests` |
| Playground ofensivo em prod | cenarios ofensivos ocultos por propriedade | `PlaygroundAvailabilityTests` |

## Observabilidade

- Logs JSON com `correlationId` e `userId` no MDC quando presentes.
- `X-Correlation-Id` propagado em toda resposta; valores invalidos sao descartados.
- Prometheus protegido por JWT em `/actuator/prometheus`.
- Tracing via OTLP em `OTEL_EXPORTER_OTLP_ENDPOINT`.
- Health checks customizados para PostgreSQL e Redis.

## Arquitetura

```mermaid
C4Container
title EGSYS Tasks API

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

## Decisoes Tecnicas

| Area | Decisao |
| --- | --- |
| Arquitetura | Hexagonal, com dominio Kotlin puro e dependencias validadas por ArchUnit |
| Persistencia | PostgreSQL 16 com Flyway, JPA/Hibernate e queries parametrizadas |
| API | REST v1, DTOs separados, ProblemDetail e paginacao por cursor |
| Seguranca | JWT RS256, Argon2id, RBAC, anti-IDOR, rate limiting e revogacao em Redis |
| Observabilidade | Logs JSON, correlation ID, Prometheus protegido e tracing OTLP |
| Deploy | Docker multi-stage/distroless, Compose local e Blueprint Render |

## Roadmap Futuro

| Item | Motivo |
| --- | --- |
| Worker de Outbox | publicar notificacoes/email/WebSocket com retry e idempotencia |
| Importacao CSV/iCal | fechar ciclo de interoperabilidade de tarefas |
| Dashboards Grafana versionados | entregar paineis prontos alem do datasource |
| Push de imagem GHCR | publicar imagem assinada em tags `v*` |
| OWASP Dependency-Check com NVD API key | tornar SCA menos sujeito a rate limit externo |
