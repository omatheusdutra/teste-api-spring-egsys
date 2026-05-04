# Auditoria de coerência visual — Home x Playground

Data: 2026-05-03

Escopo lido:

- `src/main/resources/static/index.html`
- `src/main/resources/playground/playground.html`
- `src/main/resources/playground/styles.css`
- `src/main/resources/playground/app.js`

Observação de arquitetura: o playground não está em `src/main/resources/static/playground/`. Ele vive em `src/main/resources/playground/` e é servido pelo `PlaygroundController`, controlado por `egsys.playground.enabled`. A decisão continua válida porque evita exposição acidental por static resource.

| Elemento | Home (`index.html`) | Playground (`playground.html`) | Decisão |
|---|---|---|---|
| Topbar — estrutura | `<nav class="top-nav">` com marca e links | `<header class="topbar">` com marca e controles | Unificar em `<header class="app-header">` com estrutura comum e CSS compartilhado. |
| Topbar — logo | SVG real do Grupo Bringel via `/egsys-logo.svg` | Mesmo SVG, mas com marcação diferente | Usar o mesmo padrão: logo + wordmark + contexto. |
| Topbar — wordmark | `egSYS` com `SYS` cyan | `Tasks API · Playground` sem `egSYS` | Playground passa a exibir `egSYS` e contexto `Playground`. |
| Topbar — links | LinkedIn e GitHub | Home via logo, sem nav compartilhada | Adicionar Home, Playground, API Docs, LinkedIn e GitHub em ambas. Controles exclusivos ficam no Playground. |
| Aurora animada | 3 blobs, opacidade `0.55`, `float 22s` | 3 blobs, opacidade `0.35`, `float 22s` | Equalizar a intensidade no CSS compartilhado para `0.48`, mantendo a sensação da home sem dominar o playground. |
| Grid overlay | `80%/60% at 50% 40%` | `80%/60% at 50% 30%` | Padronizar em `50% 35%`, meio-termo entre hero e painel operacional. |
| Paleta | `#FF6B2C`, `#22D3EE`, `#05070D` | Mesma paleta base | Extrair tokens comuns para `assets/shared.css`. |
| Fonte | Declara `Inter` e `JetBrains Mono`, mas sem fonte local nem link externo atual | Declara `Inter` e `JetBrains Mono`, também dependente do sistema | Auto-hospedar Inter e JetBrains Mono em `assets/fonts/` e carregar `assets/fonts/fonts.css` nas duas páginas. |
| Botões | Gradiente laranja, glow e glass | Gradiente laranja e ghost buttons | Manter estilos específicos, mas tokens e header compartilhados. |
| Footer | Footer completo com logo, links e endereço | Apenas defense-bar fixa | Adicionar mini-footer no Playground acima da defense-bar. |
| Microcopy | “production mindset”, “under guard” | “demo controlada”, “Provoque uma defesa” | Reescrever microcopy do Playground para ecoar a narrativa da home. |
| CSP | Home sem CSP por meta; controlada por headers Spring | Playground tem CSP meta `default-src 'self'` | Manter CSP do Playground intacta e carregar apenas assets locais. |

## Decisões da Parte I

1. Criar `src/main/resources/static/assets/shared.css` para tokens visuais, aurora, grid, header e footer compartilhados.
2. Criar `src/main/resources/static/assets/fonts/fonts.css` e auto-hospedar fontes locais.
3. Manter CSS específico de cada página para componentes exclusivos: hero/home e painéis/playground.
4. Não mover o playground para `static/`; manter o controller por propriedade.
5. Deixar a `defense-bar` exclusiva do Playground, porque ela é contexto operacional e não deve poluir a home.

## Itens fora de escopo nesta parte

- Intrusion Theater fica para a Parte II.
- Lighthouse formal e screenshots ficam para validação integrada posterior.
