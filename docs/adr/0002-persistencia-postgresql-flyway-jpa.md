# ADR 0002: Persistencia PostgreSQL, Flyway e JPA

## Status

Accepted

## Context

A Etapa 2 precisa persistir categorias e tarefas em PostgreSQL real, sem H2, mantendo a arquitetura hexagonal: dominio
puro, portas no dominio e adaptadores na infraestrutura. O modelo tambem precisa preparar o terreno para soft delete,
auditoria e buscas eficientes.

## Decision

Usar Flyway para criar e versionar o schema `categorias` e `tarefas`, com UUID como chave primaria, `ON DELETE RESTRICT`
na relacao tarefa-categoria, checks de integridade e indices para categoria, status e listagens ativas por data.

As entidades JPA ficam restritas a `infrastructure.persistence.entity`. O dominio continua exposto apenas por
`CategoriaRepository` e `TarefaRepository`, implementados por adaptadores Spring Data JPA. Mappers dedicados convertem
entre entidades JPA e agregados de dominio, falhando explicitamente quando uma entidade persistida estiver incompleta.

Os testes de persistencia usam Testcontainers com `postgres:16-alpine`. Eles sao marcados com a tag `postgres`, e o
Gradle exclui essa tag apenas quando `docker info` nao estiver disponivel no ambiente local. No CI com Docker, os testes
rodam contra PostgreSQL real. Os contextos JPA sao descartados por classe para evitar reutilizar uma URL de container ja
encerrado.

## Consequences

Nao ha divergencia de comportamento causada por H2. A suite de integracao fica mais lenta que testes unitarios puros,
mas valida migrations, constraints, indices, mapeamento JPA e soft delete com o mesmo banco esperado em producao.
