# ADR 0007 - DevEx e deploy local

## Status

Aceita

## Contexto

O projeto precisa ser executado rapidamente em revisão técnica, mantendo um caminho plausível para produção:
imagem enxuta, stack local realista, comandos comuns e pipeline automatizada.

## Decisão

- Dockerfile multi-stage: build em `eclipse-temurin:21-jdk-alpine` e runtime distroless Java 21 non-root.
- `docker-compose.yml` sobe API, PostgreSQL 16, Redis 7, Prometheus e Grafana para avaliação local.
- `docker-compose.prod.yml` sobe uma demo `prod` enxuta com API, PostgreSQL e Redis internos, secrets por env e bind local para uso atrás de HTTPS.
- Prometheus usa `authorization.credentials_file` para manter `/actuator/prometheus` autenticado sem versionar token.
- `Makefile` concentra comandos de rotina (`check`, `pitest`, `compose-up`, `docker-build`, `security-scan`).
- Coleção Bruno versionada cobre o tour mínimo: cadastro, login, criação e listagem de tarefas.
- GitHub Actions separa CI principal de security workflow: build/test sempre rodam no push; scans rodam por PR ou demanda.

## Consequências

- A imagem final não contém JDK, Gradle, shell ou usuário root.
- O compose local é adequado para avaliação funcional, mas o scrape Prometheus da API exige preencher token admin local.
- O compose de demo evita publicar banco e Redis na rede pública, mas ainda espera um reverse proxy externo para TLS e domínio.
- Scans que dependem de bases externas ficam isolados em workflow próprio para reduzir flakiness do CI principal.
