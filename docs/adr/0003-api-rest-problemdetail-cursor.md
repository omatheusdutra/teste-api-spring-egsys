# ADR 0003: API REST com ProblemDetail e paginação por cursor

## Status

Aceita

## Contexto

A API precisa expor categorias e tarefas por HTTP sem vazar entidades de domínio ou JPA. Os contratos também precisam
ser previsíveis para validação, erros e listagens grandes.

## Decisão

Usar controllers Spring MVC em `/api/v1`, com DTOs de request e response separados do domínio. Todos os corpos de entrada
passam por Jakarta Bean Validation, com `@JsonIgnoreProperties(ignoreUnknown = false)` para rejeitar campos inesperados.

Erros são normalizados com RFC 7807 `ProblemDetail`, incluindo lista de violações de campo quando aplicável. A
documentação OpenAPI é gerada pelo springdoc.

Listagens de tarefas usam paginação por cursor em vez de offset. O cursor é opaco para o cliente e codifica a dupla
`dataHora` + `id`, permitindo ordenação determinística por data e desempate por UUID.

## Consequências

A camada web fica explícita e testável, com contratos estáveis para evolução de segurança e autorização. A paginação
cursor-based evita custos crescentes de offset, mas exige que clientes tratem o `nextCursor` como valor opaco.
