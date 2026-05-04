# ADR 0001: Stack e arquitetura base

## Status

Aceita

## Contexto

O teste técnico da EGSYS pede uma API REST em Kotlin + Spring Boot. A entrega foi tratada como um serviço de produção:
segurança, performance, manutenibilidade e criatividade guiaram as escolhas técnicas.

## Decisão

Usar Kotlin 2.x na JVM 21, Spring Boot 3.5.x, Gradle Kotlin DSL, PostgreSQL, Redis, Flyway, Spring Security, JJWT,
Argon2id via Bouncy Castle, springdoc-openapi, Micrometer, OpenTelemetry, ktlint, detekt, JaCoCo, Pitest, Testcontainers
e ArchUnit.

A organização segue arquitetura hexagonal:

- `domain` é Kotlin puro.
- `application` orquestra casos de uso e depende apenas de `domain`.
- `infrastructure` contém adaptadores de persistência, segurança, mensageria e configuração.
- `web` contém adaptadores HTTP, DTOs e tratamento de erros.

## Consequências

A base é mais robusta que um CRUD mínimo, mas evita retrabalho quando segurança, observabilidade, testes e deploy entram
no projeto.
