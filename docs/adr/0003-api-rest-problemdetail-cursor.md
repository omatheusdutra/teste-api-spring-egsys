# ADR 0003: API REST com ProblemDetail e cursor pagination

## Status

Accepted

## Context

A Etapa 4 precisa expor categorias e tarefas por HTTP sem vazar entidades de dominio ou JPA. A API tambem precisa ter
contratos previsíveis para validação, erros e listagens grandes, porque a Etapa 5 adicionará segurança em cima desses
endpoints.

## Decision

Usar controllers Spring MVC em `/api/v1`, com DTOs de request e response separados do dominio. Todos os corpos de entrada
passam por Jakarta Bean Validation, com `@JsonIgnoreProperties(ignoreUnknown = false)` para rejeitar campos inesperados.

Erros sao normalizados com RFC 7807 `ProblemDetail`, incluindo lista de violacoes de campo quando aplicavel. A
documentacao OpenAPI e gerada pelo springdoc a partir dos controllers e DTOs.

Listagens de tarefas usam paginacao cursor-based em vez de offset. O cursor e opaco para o cliente e codifica a dupla
`dataHora` + `id`, permitindo ordenacao deterministica por data e desempate por UUID.

## Consequences

A camada web fica explícita e testável, com contratos estáveis para evolução de segurança e autorização. A paginação
cursor-based evita custos crescentes de offset, mas exige que clientes tratem o `nextCursor` como valor opaco.
