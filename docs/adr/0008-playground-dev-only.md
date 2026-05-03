# ADR 0008 - Playground interativo dev-only

## Status

Substituída pela [ADR 0009](0009-playground-production-demo-controlled.md).

## Contexto

A primeira versão do playground foi pensada como recurso exclusivo de desenvolvimento. A intenção era evitar exposição
acidental de uma interface de teste e manter o HTML fora de `resources/static`.

## Decisão Original

- Servir `/playground` somente por controller.
- Manter `playground.html` fora das pastas estáticas do Spring.
- Criar o controller apenas no profile `dev`.
- Preservar CSP, headers de segurança e token apenas em memória.

## Substituição

A estratégia foi refinada na [ADR 0009](0009-playground-production-demo-controlled.md):

- o playground agora é controlado por `egsys.playground.enabled`;
- produção pode expor uma demo controlada;
- os botões ofensivos ficam atrás de `egsys.playground.attack-demos-enabled`;
- o pentest interativo completo permanece limitado a `dev`/`local`/sandbox.
