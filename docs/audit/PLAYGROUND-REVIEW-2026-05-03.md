# Playground · Revisao pre-refatoracao — 2026-05-03

Read-only. Esta auditoria confirma (ou refuta) cada um dos 27 problemas levantados na lista
priorizada e amarra cada item ao codigo real para que a refatoracao seja cirurgica.

Arquivos auditados (caminhos reais — o prompt-mestre cita `static/playground/...` mas o
projeto serve via `PlaygroundController`, com os arquivos em `src/main/resources/playground/`):

- [src/main/resources/playground/playground.html](../../src/main/resources/playground/playground.html) — 222 linhas
- [src/main/resources/playground/styles.css](../../src/main/resources/playground/styles.css) — 309 linhas
- [src/main/resources/playground/app.js](../../src/main/resources/playground/app.js) — 789 linhas
- Backend de apoio: [PlaygroundController.kt](../../src/main/kotlin/br/com/egsys/tasks/web/controller/PlaygroundController.kt), [TarefaController.kt](../../src/main/kotlin/br/com/egsys/tasks/web/controller/TarefaController.kt), [CategoriaController.kt](../../src/main/kotlin/br/com/egsys/tasks/web/controller/CategoriaController.kt)
- ADR vigente: [docs/adr/0009-playground-production-demo-controlled.md](../adr/0009-playground-production-demo-controlled.md)

## Sumario por severidade

| Severidade | Qtd | Confirmados | Parcial / refutado | Pendentes |
| --- | --- | --- | --- | --- |
| 🔴 Critico (C1-C7) | 7 | 7 | 0 | 0 |
| 🟡 Medio (M1-M7) | 7 | 6 | 1 (M1 — ver nota) | 0 |
| 🟢 Polish (P1-P13) | 13 | 13 | 0 | 0 |
| **Total** | **27** | **26** | **1** | **0** |

## 🔴 Criticos

| # | Problema | Local real | Status | Nota de refatoracao |
| --- | --- | --- | --- | --- |
| C1 | `decodeJwt` usa `escape()` deprecada | [app.js:89](../../src/main/resources/playground/app.js#L89) — `decodeURIComponent(escape(json))` | **CONFIRMADO** | Trocar por `TextDecoder('utf-8')` sobre `Uint8Array` derivado de `atob`. Já inclui padding do base64 que hoje não é tratado. |
| C2 | Demo rate-limit faz 12 requests, card promete ~120 | [HTML:68](../../src/main/resources/playground/playground.html#L68) "dispara ~120 requests em paralelo" vs [app.js:615](../../src/main/resources/playground/app.js#L615) `Array.from({ length: 12 }, ...)` | **CONFIRMADO** | Subir para 120 e adicionar nota no card sobre o limite tipico (100/min). |
| C3 | Demo idor com UUID montado frágil | [app.js:635](../../src/main/resources/playground/app.js#L635) — `Math.random().toString(16).slice(2,14).padEnd(12,'0')` | **CONFIRMADO** | Usar `crypto.randomUUID()` (disponível em todo browser moderno). Endurecer veredito: aceitar 404/403, REJEITAR 400 (que indicaria UUID malformado, não defesa IDOR). |
| C4 | Demo sqli em `?cursor=` (parâmetro decodado opaco) | [app.js:696-697](../../src/main/resources/playground/app.js#L696) — payload em cursor que é decodificado por `CursorCodec` antes de qualquer query | **CONFIRMADO com nuance** | A API não expõe filtros LIKE/where dinâmicos baseados em string crua — `TarefaController.listar` só aceita `cursor`/`limit` e o cursor é decodificado para `(dataHora, id)`. Logo, `?cursor=` testa **rejeição de input opaco**, não parametrização SQL. Vamos manter o vetor mas adicionar uma chamada de **count antes/depois** (`GET /api/v1/tarefas?limit=1` sucesso pós-ataque) para provar que a tabela continua intacta. |
| C5 | Demo mass-assignment so aceita 400 como OK | [app.js:735](../../src/main/resources/playground/app.js#L735) — `ok: r.status === 400` | **CONFIRMADO** | Logica em duas etapas: se 400 → defesa A (Jackson `failOnUnknownProperties`); se 201 → buscar tarefa criada e checar `categoria/owner` real != injetado, OK se diferente. Ambos sao defesas validas. |
| C6 | Pill de rate limit fica em max ate reload | [app.js:203-206](../../src/main/resources/playground/app.js#L203) — seta `current = max` mas nunca decrementa | **CONFIRMADO** | Parsear `Retry-After`, agendar `setTimeout(() => { current = 0; render() }, seconds * 1000)`. |
| C7 | `detectDefenseTrigger` dispara para 400 e 401 (UX ruim) | [app.js:196-207](../../src/main/resources/playground/app.js#L196) — login errado dispara "auth-rejected"; form invalido dispara "input-validation" | **CONFIRMADO** | Restringir a 429/403/413/415. 401/400 sao UX normal e poluem a faixa de defesas. |

## 🟡 Medios

| # | Problema | Local real | Status | Nota de refatoracao |
| --- | --- | --- | --- | --- |
| M1 | Contrato divergente: categorias retorna array, tarefas retorna `{items}` | [app.js:314](../../src/main/resources/playground/app.js#L314) vs [app.js:346-347](../../src/main/resources/playground/app.js#L346); backend: [CategoriaController.kt:23](../../src/main/kotlin/br/com/egsys/tasks/web/controller/CategoriaController.kt#L23) retorna `List<CategoriaResponse>`; [TarefaController.kt:103](../../src/main/kotlin/br/com/egsys/tasks/web/controller/TarefaController.kt#L103) retorna `TarefaPageResponse(items, nextCursor)` | **CONFIRMADO mas válido** | Os dois retornos estão corretos para o que são: categorias é lista finita; tarefas exigem cursor pagination. **Não mudar o backend.** Apenas adicionar comentário no JS explicando a divergência intencional. Inversão do prompt: ajustar **a documentação**, não o código. |
| M2 | `POST /tarefas/{id}/status/{status}` — REST não-idiomático | [app.js:386](../../src/main/resources/playground/app.js#L386); [TarefaController.kt:163](../../src/main/kotlin/br/com/egsys/tasks/web/controller/TarefaController.kt#L163) | **CONFIRMADO** | Backend define o contrato; o front só consome. Adicionar comentário no JS explicando que é fluxo de comando (transição de estado), não update arbitrário. Não trocar para PATCH nesta fase. |
| M3 | `scheduleAutoRefresh` pode entrar em loop quente | [app.js:289-294](../../src/main/resources/playground/app.js#L289) — `Math.max(5000, remaining - 60_000)` faz timer de 5s sempre que `remaining < 65s` | **CONFIRMADO** | Se `remaining <= 60_000`, chamar `autoRefresh()` imediatamente. |
| M4 | `parsePrometheus` depende do nome `_count` que pode ser `_total` em versoes mais novas | [app.js:567](../../src/main/resources/playground/app.js#L567) | **CONFIRMADO** | Aceitar `_count` OU `_total` no regex. Logar `console.debug` do snippet bruto para diagnostico. |
| M5 | `refreshMetrics` continua rodando ao trocar de aba | [app.js:555](../../src/main/resources/playground/app.js#L555); [activateTab:522-526](../../src/main/resources/playground/app.js#L522) | **CONFIRMADO** | Em `activateTab`, se `name !== 'metricas'`, `clearTimeout(metricsTimer)`. |
| M6 | Sem `<meta http-equiv="Content-Security-Policy">` no HTML | [HTML:3-10](../../src/main/resources/playground/playground.html#L3) | **CONFIRMADO** | O servidor ja envia CSP via header (verificado no pentest cenario 39), mas adicionar `<meta>` da defesa em profundidade caso o asset seja servido por outro pipeline. |
| M7 | CSS morto: `.brand-sub code` | [styles.css:78](../../src/main/resources/playground/styles.css#L78); HTML linha 20 não tem `<code>` em `.brand-sub` | **CONFIRMADO** | Usar `<code>` no subtítulo (ex.: destaque a palavra "demo controlada") para manter a regra útil. |

## 🟢 Polish

| # | Problema | Status | Nota de refatoracao |
| --- | --- | --- | --- |
| P1 | Skeleton loaders ausentes | **CONFIRMADO** | Adicionar `.skeleton-row` com shimmer; renderizar 3 antes de cada `loadCategorias`/`loadTarefas`. |
| P2 | Botões não têm estado loading | **CONFIRMADO** | Wrapper `withLoadingState(btn, fn)` aplicado a submits e demos. |
| P3 | Empty state não ilustrado | **CONFIRMADO** | SVG inline (~30 linhas) + CTA que dá foco no input de título. |
| P4 | Excluir não tem "desfazer" | **CONFIRMADO** | Toast com botão desfazer; `setTimeout` 8s antes do `DELETE` real. |
| P5 | Sem atalhos de teclado | **CONFIRMADO** | `c`, `/`, `Esc`, `?`, `g t/c/a/m`. Listener global respeitando focus em inputs. |
| P6 | Sem sparkline de latência | **CONFIRMADO** | Histórico das últimas 30 P95 em SVG `<polyline>`. |
| P7 | Strings hardcoded em pt-BR | **CONFIRMADO** | Dict `i18n` central, toggle PT/EN no topbar, idioma escolhido pode ir para `localStorage` (preferência trivial — não token). |
| P8 | Sem health pill | **CONFIRMADO** | `GET /actuator/health` a cada 30s; bolinha verde/amarela/vermelha + tooltip. |
| P9 | Sem aviso "sandbox controlado" no painel de demos | **CONFIRMADO** | Banner verde no card defense-demos. Hoje só existe a mensagem laranja "demonstrações ofensivas indisponíveis" quando desabilitado. |
| P10 | Logo não linka LinkedIn (home pública linka) | **CONFIRMADO** | Envolver `<img class="brand-logo">` em `<a href="https://br.linkedin.com/company/egsys" target="_blank" rel="noopener">`. Coerente com [home pública:759](../../src/main/resources/static/index.html#L759). |
| P11 | Mobile: painel "Última requisição" aparece antes do conteúdo | [styles.css:90](../../src/main/resources/playground/styles.css#L90) — `.right { order: -1 }`. **CONFIRMADO** | Trocar por `order: 2` ou colapsar painel direito em `<details>` em mobile. |
| P12 | Tabs sem semântica completa de tablist | [HTML:104-109](../../src/main/resources/playground/playground.html#L104) — `role="tablist"` no nav mas botões sem `role="tab"`/`aria-controls` e sem navegação seta esquerda/direita. **CONFIRMADO** | Adicionar `role="tab"`, `role="tabpanel"`, `aria-controls`, navegação por teclado (ArrowLeft/Right, Home/End). |
| P13 | `confirm()` nativo na exclusão | [app.js:395](../../src/main/resources/playground/app.js#L395) | **CONFIRMADO** | Criar `<dialog id="confirm-dialog">` + `confirmAction(msg): Promise<boolean>`. (Nota: P4 adiciona "desfazer", o que pode tornar P13 redundante para a exclusão específica — mas o `<dialog>` fica disponível para outras confirmações.) |

## Premissas que ficam preservadas durante a refatoracao

Nenhuma destas pode regredir. Cada uma tem teste em
[PlaygroundFrontendTest](../../src/test/kotlin/br/com/egsys/tasks/playground/PlaygroundFrontendTest.kt):

- Token vive **so em closure JS**, sem `localStorage`/`sessionStorage`/`document.cookie` (excecao
  unica permitida: idioma escolhido em P7, com comentario explicito).
- Sem `eval`, sem `new Function`, sem `innerHTML =`, sem `document.write`.
- HTML sem `<script>` inline e sem handlers `onclick=`/`onload=`.
- CSS mantem paleta laranja `#FF6B2C` + cyan `#22D3EE` + dark `#05070D`, fontes Inter +
  JetBrains Mono.
- Servidor envia headers `Content-Security-Policy`, `X-Content-Type-Options: nosniff`,
  `X-Frame-Options: DENY`, `Referrer-Policy: no-referrer`, `Cache-Control: no-store`.

## Decisoes que vou tomar antes de cada onda

1. **C4 (SQLi):** mantem `?cursor=` mas adiciona check de count, e ajusta o titulo do botao
   para "Cursor injection" para não mentir sobre o vetor real.
2. **M1 (contrato):** não mexer no backend; documentar a divergência.
3. **M2 (path-style status):** não mexer no backend; comentário explicando.
4. **P3 (empty state):** SVG inline desenhado a mao, sem dependencia.
5. **P7 (i18n):** salvar idioma escolhido em `localStorage` apenas (`playground.lang`), não
   tokens. Adicionar comentário `// EXCEÇÃO: preferência trivial — não toca tokens` para o
   teste de segurança cobrir a lista permitida.
6. **P12 (a11y):** implementacao manual sem dependencia.

## Fora do escopo desta refatoracao

- Mudar contrato HTTP da API (M2 PATCH, M1 wrapper de paginacao em categorias).
- Migrar para framework JS.
- Adicionar bibliotecas externas (incluindo lodash, jQuery, Alpine, htmx).
- Implementar busca client-side persistente em campo de filtro novo (atalho `/` em P5
  vai focar **input existente**, não criar novo recurso de busca persistente).

## Checkpoint

Auditoria concluida sem alterar codigo. Pronto para iniciar Onda 2 (criticos) apos commit
deste relatorio.
