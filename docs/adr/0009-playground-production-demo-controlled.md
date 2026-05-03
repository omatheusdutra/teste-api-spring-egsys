# ADR 0009 - Playground como demo controlada por propriedades

## Status

Aceita

## Contexto

O playground acelerou a avaliacao tecnica porque permite registrar, logar, criar tarefas e acionar
defesas sem depender de um cliente externo. A decisao original era deixa-lo restrito ao profile
`dev`, evitando exposicao acidental. Porem, para uma entrega demonstravel em ambiente publicado, o
avaliador precisa acessar a experiencia guiada sem trocar profile ou importar colecoes.

O risco real esta nos botoes ofensivos de demonstracao, nao no fluxo de demo autenticado. Esses
botoes disparam tentativas como rate limit, IDOR, JWT adulterado, SQLi e mass assignment contra a
propria API. Em producao, eles devem ficar desligados por padrao.

## Decisao

- `PlaygroundController` nao usa mais `@Profile("dev")`.
- A disponibilidade do controller passa a ser controlada por
  `egsys.playground.enabled=true`, via `@ConditionalOnProperty`.
- O HTML continua fora de `src/main/resources/static`, em `src/main/resources/playground`, e so e
  servido pelo controller. Isso preserva a guarda contra vazamento por static resource handler.
- O endpoint `GET /playground/config` informa se as demonstracoes ofensivas estao habilitadas.
- `application-dev.yml` e `application-local.yml` habilitam o playground e os botoes ofensivos.
- `application-prod.yml` habilita o playground como demo controlada, mas define
  `egsys.playground.attack-demos-enabled=false`.
- O frontend busca `/playground/config` ao iniciar. Quando as demonstracoes ofensivas estao
  desligadas, o painel fica bloqueado e exibe a mensagem:
  "Demonstrações ofensivas disponíveis apenas em ambiente controlado."

## Consequencias

- Em producao, `/playground` pode ser usado como tour comercial e tecnico da API.
- O pentest interativo completo permanece limitado a ambientes `dev`/`local`/sandbox.
- A CSP, os headers de seguranca, o uso de JS externo e o armazenamento de token apenas em memoria
  continuam sendo requisitos do playground.
- Testes passam a validar disponibilidade por propriedade, configuracao de demos ofensivas e
  ausencia de `localStorage`, `sessionStorage`, `eval` e handlers inline.
