# ADR 0007 - DevEx e deploy local

## Status

Aceita

## Contexto

O avaliador precisa executar a API rapidamente, mas o projeto tambem deve demonstrar caminho plausivel para producao:
imagem enxuta, stack local realista, comandos comuns e pipeline automatizada.

## Decisao

- Dockerfile multi-stage: build em `eclipse-temurin:21-jdk-alpine` e runtime distroless Java 21 non-root.
- `docker-compose.yml` sobe API, PostgreSQL 16, Redis 7, Prometheus e Grafana.
- Prometheus usa `authorization.credentials_file` para manter `/actuator/prometheus` autenticado sem versionar token.
- `Makefile` concentra comandos de rotina (`check`, `pitest`, `compose-up`, `docker-build`, `security-scan`).
- Colecao Bruno versionada cobre o tour minimo: cadastro, login, criacao e listagem de tarefas.
- GitHub Actions separa CI principal de security workflow: build/test sempre rodam no push; scans rodam por PR ou demanda.

## Consequencias

- A imagem final nao contem JDK, Gradle, shell ou usuario root.
- O compose local e adequado para avaliacao funcional, mas o scrape Prometheus da API exige preencher token admin local.
- Scans que dependem de bases externas ficam isolados em workflow proprio para reduzir flakiness do CI principal.
