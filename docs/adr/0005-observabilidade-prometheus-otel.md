# ADR 0005 - Observabilidade com logs JSON, Prometheus e OpenTelemetry

## Status

Aceita

## Contexto

A API precisa operar como um servico de producao: cada requisicao deve ser correlacionavel em logs, metricas devem ser
coletaveis pelo Prometheus, traces devem sair por OTLP e os checks de saude precisam sinalizar falhas reais de
dependencias criticas.

## Decisao

- Logs em JSON via `logstash-logback-encoder`, sempre incluindo `application` e MDC quando disponivel.
- `X-Correlation-Id` e aceito somente com caracteres seguros e tamanho controlado; valores ausentes ou suspeitos sao
  substituidos por UUID gerado no servidor.
- `JwtAuthenticationFilter` adiciona `userId` ao MDC durante a requisicao autenticada, sem logar email, token ou JTI.
- `/actuator/prometheus` permanece protegido pela regra global de autenticacao do Spring Security.
- Tracing usa Micrometer Tracing com exporter OTLP configurado por `OTEL_EXPORTER_OTLP_ENDPOINT`.
- Health checks customizados validam PostgreSQL com `SELECT 1` e Redis com `PING`.

## Consequencias

- Logs ficam prontos para ingestao em Loki/ELK/Datadog sem parsing fragil.
- Prometheus exige JWT, reduzindo vazamento de metricas internas.
- Ambientes sem collector OTLP podem configurar endpoint local ou reduzir sampling por profile.
- Health checks expostos pelo Actuator refletem dependencia real de DB e Redis, nao apenas boot da aplicacao.
