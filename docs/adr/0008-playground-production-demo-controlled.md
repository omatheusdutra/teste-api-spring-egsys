# ADR 0008 - Playground como demo controlada por propriedades

## Status

Aceita

## Contexto

O playground acelera a avaliação técnica porque permite registrar, logar, criar tarefas e acionar
defesas sem depender de um cliente externo. A decisão original era deixá-lo restrito ao profile
`dev`, evitando exposição acidental. Porém, para uma entrega demonstrável em ambiente publicado, o
avaliador precisa acessar a experiência guiada sem trocar profile ou importar coleções.

O risco real está nos botões ofensivos de demonstração, não no fluxo de demo autenticado. Esses
botões disparam tentativas como rate limit, IDOR, JWT adulterado, SQLi e mass assignment contra a
própria API. Em produção, eles devem ficar desligados por padrão.

## Decisão

- `PlaygroundController` não usa mais `@Profile("dev")`.
- A disponibilidade do controller passa a ser controlada por
  `egsys.playground.enabled=true`, via `@ConditionalOnProperty`.
- O HTML continua fora de `src/main/resources/static`, em `src/main/resources/playground`, e só é
  servido pelo controller. Isso preserva a guarda contra vazamento por static resource handler.
- O endpoint `GET /playground/config` informa se as demonstrações ofensivas estão habilitadas.
- `application-dev.yml` e `application-local.yml` habilitam o playground e os botões ofensivos.
- `application-prod.yml` habilita o playground como demo controlada, mas define
  `egsys.playground.attack-demos-enabled=false`.
- O frontend busca `/playground/config` ao iniciar. Quando as demonstrações ofensivas estão
  desligadas, o painel fica bloqueado e exibe a mensagem:
  "Demonstrações ofensivas disponíveis apenas em ambiente controlado."

## Consequências

- Em produção, `/playground` pode ser usado como tour comercial e técnico da API.
- O pentest interativo completo permanece limitado a ambientes `dev`/`local`/sandbox.
- A CSP, os headers de segurança, o uso de JS externo e o armazenamento de token apenas em memória
  continuam sendo requisitos do playground.
- Testes passam a validar disponibilidade por propriedade, configuracao de demos ofensivas e
  ausencia de `localStorage`, `sessionStorage`, `eval` e handlers inline.
