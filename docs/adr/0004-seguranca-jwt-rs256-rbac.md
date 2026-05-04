# ADR 0004: Segurança JWT RS256, RBAC e anti-IDOR

## Status

Aceita

## Contexto

A API assume que qualquer cliente pode enviar dados hostis. O principal risco funcional é IDOR: um usuário autenticado
acessar a tarefa de outro usuário apenas trocando o UUID na URL.

## Decisão

Usar JWT assinado com RS256. O servidor rejeita explicitamente qualquer token cujo header `alg` não seja `RS256` antes de
validar assinatura e claims. Access tokens carregam `sub`, `iat`, `nbf`, `exp`, `jti`, `iss`, `aud` e `roles`. Logout
revoga o `jti` em Redis até o vencimento do token.

Refresh tokens são opacos, armazenados apenas como SHA-256, rotacionados a cada uso e agrupados por `family_id`. Reuso,
expiração ou revogação de um token antigo revoga a família inteira.

Senhas usam Argon2id via `Argon2PasswordEncoder`, com parâmetros alinhados ao edital e pepper opcional por variável de
ambiente. Roles suportadas: `ROLE_USER` e `ROLE_ADMIN`.

Tarefas passam a ter `owner_id` no domínio, no schema e nas queries. Buscar, listar, atualizar e excluir tarefas sempre
recebem o usuário autenticado e filtram no repositório por `owner_id`, evitando buscar globalmente e filtrar em memória.

## Consequências

A API agora exige autenticação em endpoints de negócio. A migração adiciona tabela de usuários, refresh tokens e FK
`tarefas.owner_id`. Ambientes de produção devem fornecer chaves RSA por variáveis de ambiente; chaves efêmeras só são
permitidas fora do profile `prod` para facilitar testes locais.
