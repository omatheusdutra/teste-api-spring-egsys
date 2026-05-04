# ADR 0005 - Observabilidade com logs JSON, Prometheus e OpenTelemetry

## Status

Aceita

## Contexto

A API precisa operar como um serviço de produção: cada requisição deve ser correlacionável em logs, métricas devem ser
coletáveis pelo Prometheus, traces devem sair por OTLP e os checks de saúde precisam sinalizar falhas reais de
dependências críticas.

## Decisão

- Logs em JSON via `logstash-logback-encoder`, sempre incluindo `application` e MDC quando disponível.
- `X-Correlation-Id` é aceito somente com caracteres seguros e tamanho controlado; valores ausentes ou suspeitos são
  substituídos por UUID gerado no servidor.
- `JwtAuthenticationFilter` adiciona `userId` ao MDC durante a requisição autenticada, sem logar email, token ou JTI.
- `/actuator/prometheus` permanece protegido pela regra global de autenticação do Spring Security.
- Tracing usa Micrometer Tracing com exporter OTLP configurado por `OTEL_EXPORTER_OTLP_ENDPOINT`.
- Health checks customizados validam PostgreSQL com `SELECT 1` e Redis com `PING`.

## Consequências

- Logs ficam prontos para ingestão em Loki/ELK/Datadog sem parsing frágil.
- Prometheus exige JWT, reduzindo vazamento de métricas internas.
- Ambientes sem collector OTLP podem configurar endpoint local ou reduzir sampling por profile.
- Health checks expostos pelo Actuator refletem dependência real de DB e Redis, não apenas boot da aplicação.
