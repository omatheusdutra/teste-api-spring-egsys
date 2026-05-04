# ADR 0002: Persistência PostgreSQL, Flyway e JPA

## Status

Aceita

## Contexto

A API precisa persistir categorias e tarefas em PostgreSQL real, sem H2, mantendo domínio puro, portas no domínio e
adaptadores na infraestrutura. O modelo também precisa suportar soft delete, auditoria e buscas eficientes.

## Decisão

Usar Flyway para criar e versionar o schema, com UUID como chave primária, `ON DELETE RESTRICT` na relação
tarefa-categoria, checks de integridade e índices para categoria, status e listagens ativas por data.

As entidades JPA ficam restritas a `infrastructure.persistence.entity`. O domínio continua exposto apenas por
`CategoriaRepository` e `TarefaRepository`, implementados por adaptadores Spring Data JPA. Mappers dedicados convertem
entre entidades JPA e agregados de domínio, falhando explicitamente quando uma entidade persistida estiver incompleta.

Os testes de persistência usam Testcontainers com `postgres:16-alpine`. No CI com Docker, eles rodam contra PostgreSQL
real.

## Consequências

Não há divergência de comportamento causada por H2. A suíte de integração fica mais lenta que testes unitários puros,
mas valida migrations, constraints, índices, mapeamento JPA e soft delete com o mesmo banco esperado em produção.
