# ADR 0004: Seguranca JWT RS256, RBAC e anti-IDOR

## Status

Accepted

## Context

A Etapa 5 adiciona autenticação e autorização em uma API que deve assumir clientes hostis. O principal risco funcional é
IDOR: um usuário autenticado acessar uma tarefa de outro usuário apenas trocando o UUID na URL.

## Decision

Usar JWT assinado com RS256. O servidor rejeita explicitamente qualquer token cujo header `alg` não seja `RS256` antes de
validar assinatura e claims. Access tokens carregam `sub`, `iat`, `nbf`, `exp`, `jti`, `iss`, `aud` e `roles`. Logout
revoga o `jti` em Redis ate o vencimento do token.

Refresh tokens sao opacos, armazenados apenas como SHA-256, rotacionados a cada uso e agrupados por `family_id`. Reuso,
expiracao ou revogacao de um token antigo revoga a familia inteira.

Senhas usam Argon2id via `Argon2PasswordEncoder`, com parâmetros alinhados ao edital e pepper opcional por variável de
ambiente. Roles suportadas: `ROLE_USER` e `ROLE_ADMIN`.

Tarefas passam a ter `owner_id` no dominio, no schema e nas queries. Buscar, listar, atualizar e excluir tarefas sempre
recebem o usuário autenticado e filtram no repositório por `owner_id`, evitando buscar globalmente e filtrar em memória.

## Consequences

A API agora exige autenticação em endpoints de negócio. A migração adiciona tabela de usuários, refresh tokens e FK
`tarefas.owner_id`. Ambientes de produção devem fornecer chaves RSA por variáveis de ambiente; chaves efêmeras só são
permitidas fora do profile `prod` para facilitar testes locais.
