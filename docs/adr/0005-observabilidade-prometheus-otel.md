# ADR 0005 - Observabilidade com logs JSON, Prometheus e OpenTelemetry

## Status

Aceita

## Contexto

A API precisa operar como um serviço de produção: cada requisição deve ser correlacionável em logs, métricas devem ser
coletaveis pelo Prometheus, traces devem sair por OTLP e os checks de saude precisam sinalizar falhas reais de
dependencias criticas.

## Decisao

- Logs em JSON via `logstash-logback-encoder`, sempre incluindo `application` e MDC quando disponivel.
- `X-Correlation-Id` e aceito somente com caracteres seguros e tamanho controlado; valores ausentes ou suspeitos sao
  substituidos por UUID gerado no servidor.
- `JwtAuthenticationFilter` adiciona `userId` ao MDC durante a requisição autenticada, sem logar email, token ou JTI.
- `/actuator/prometheus` permanece protegido pela regra global de autenticação do Spring Security.
- Tracing usa Micrometer Tracing com exporter OTLP configurado por `OTEL_EXPORTER_OTLP_ENDPOINT`.
- Health checks customizados validam PostgreSQL com `SELECT 1` e Redis com `PING`.

## Consequencias

- Logs ficam prontos para ingestao em Loki/ELK/Datadog sem parsing fragil.
- Prometheus exige JWT, reduzindo vazamento de métricas internas.
- Ambientes sem collector OTLP podem configurar endpoint local ou reduzir sampling por profile.
- Health checks expostos pelo Actuator refletem dependência real de DB e Redis, não apenas boot da aplicação.
