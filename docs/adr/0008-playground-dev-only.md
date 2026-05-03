# ADR 0008 - Playground interativo dev-only

## Status

Substituida pela [ADR 0009](0009-playground-production-demo-controlled.md).

Esta decisao foi valida na primeira versao do playground, mas foi refinada para permitir uma demo
controlada em producao sem expor os botoes ofensivos.

## Contexto

A API expoe um CRUD de tarefas com auth JWT, RBAC, anti-IDOR e camadas de defesa. Avaliadores
do desafio precisam validar todo o fluxo (registrar -> logar -> criar tarefa -> editar -> alterar
status -> excluir) sem montar Postman, sem importar coleção e sem ler Swagger linha a linha.
A home publica em `/` ja explica o projeto, mas nao executa nada — e o objetivo aqui e que o
avaliador termine o tour em menos de 60 segundos com a propria API rodando contra ela mesma.

Querer uma "GUI de desafio" cria um risco classico: virar superficie de ataque permanente. Em
producao, qualquer pagina dev-friendly e brinquedo de atacante (information disclosure, vetor de
XSS auto-executado, debug endpoints exposed). Por isso a pagina precisa ser dev-only, com
guardas em camadas e sem possibilidade de bypass via recurso estatico.

## Decisao

- A pagina vira `src/main/resources/playground/playground.html` — fora de `static/`, `public/` e
  qualquer pasta servida automaticamente pelo Spring. Logo, mesmo que o controller fosse
  registrado por engano em producao, ainda nao haveria handler estatico para ela.
- `PlaygroundController` em `web/controller/PlaygroundController.kt` recebe `@Profile("dev")` —
  o bean so e criado quando o profile `dev` esta ativo. Em qualquer outro profile (`local`,
  `prod`, `test`), o dispatcher retorna 404 para `/playground`.
- `SecurityConfig.authorizeHttpRequests` libera `GET /playground` e `/playground/**` com
  `permitAll`, mas a guarda real e o `@Profile` do controller — `permitAll` so importa quando o
  bean existe. A pagina em si nao chama endpoints sensiveis sem token; ela usa `fetch` contra
  `/api/v1/auth/*` e `/api/v1/tarefas/*` exatamente como qualquer cliente HTTP.
- `application-dev.yml` complementa `application.yml` apontando datasource/Redis para o ambiente
  local, espelhando `application-local.yml`. O profile `dev` substitui o default `local` quando
  ativo, mantendo configuracao explicita.
- O token de acesso obtido via login pelo playground vive apenas em variavel JS no closure da
  pagina — nunca em `localStorage` ou `sessionStorage`. Isso mantem a narrativa de defesa
  consistente: refresh acontece via endpoint, nao via storage do navegador.
- A CSP global ja restringe `script-src` a `'self'`, sem inline; o playground respeita isso e
  carrega seu JS como recurso da propria origem.
- Tres testes em `src/test/kotlin/br/com/egsys/tasks/playground/PlaygroundProfileTests.kt`
  cobrem: anotacao `@Profile("dev")` presente; recurso HTML existe no classpath e NAO existe sob
  `static/`; bean registrado quando profile `dev` ativo; bean ausente quando o profile `dev`
  nao esta ativo.

## Consequencias

- Avaliadores rodam `SPRING_PROFILES_ACTIVE=dev` (ou `dev,local`) e tem o playground
  imediatamente em `http://localhost:8080/playground`.
- Em producao (`prod`), `/playground` retorna 404 e o arquivo HTML nem chega a ser servido.
- Qualquer evolucao do playground (novos botoes, novos endpoints) precisa manter o
  `@Profile("dev")` — auditavel via `PlaygroundProfileTests`.
- A imagem distroless de producao continua carregando o HTML no jar (e harmless, ja que o
  controller nao registra em prod), mas se em algum momento for necessario eliminar o arquivo do
  artefato final, basta excluir `playground/**` no `bootJar`.
