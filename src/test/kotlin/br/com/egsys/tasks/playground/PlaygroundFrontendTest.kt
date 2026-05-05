package br.com.egsys.tasks.playground

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

/**
 * Regression net for the playground frontend.
 *
 * Each assertion encodes a security or design promise so future changes cannot
 * silently regress them: token never goes to storage, no inline script (CSP),
 * no `innerHTML` with user-controlled data, no `eval`/`Function`, mesma paleta da home.
 */
class PlaygroundFrontendTest {
    private val html: String by lazy {
        requireNotNull(javaClass.classLoader.getResource("playground/playground.html")).readText()
    }
    private val js: String by lazy {
        requireNotNull(javaClass.classLoader.getResource("playground/app.js")).readText()
    }
    private val css: String by lazy {
        requireNotNull(javaClass.classLoader.getResource("playground/styles.css")).readText()
    }

    @Test
    fun `html externaliza javascript em vez de usar inline script (CSP friendly)`() {
        // CSP global proíbe inline-script. Logo, a única forma de carregar JS é via src=.
        val inlineScriptOpenTag = Regex("<script(?![^>]*\\bsrc=)[^>]*>[\\s\\S]+?</script>", RegexOption.IGNORE_CASE)
        inlineScriptOpenTag.containsMatchIn(html).shouldBeFalse()
        html.shouldContain("http-equiv=\"Content-Security-Policy\"")
        html.shouldContain("script-src 'self'")
        html.shouldContain("style-src 'self'")
        html.shouldContain("name=\"referrer\" content=\"no-referrer\"")
        html.shouldContain("/playground/assets/app.js")
        html.shouldContain("/playground/assets/styles.css")
    }

    @Test
    fun `html não tem handlers inline tipo onclick onload onerror`() {
        // CSP não bloqueia handlers inline em todos os browsers, mas a política de segurança do projeto
        // proíbe HTML attribute handlers — eles são vetor clássico de XSS injetado.
        val attrHandler = Regex("\\son(click|load|error|input|change|submit|focus|blur)\\s*=", RegexOption.IGNORE_CASE)
        attrHandler.containsMatchIn(html).shouldBeFalse()
    }

    @Test
    fun `js nunca persiste token em storage do navegador`() {
        // Verifica USO da API, não mera menção em comentário.
        Regex("\\b(local|session)Storage\\s*[\\.\\[\\(]").containsMatchIn(js).shouldBeFalse()
        Regex("document\\.cookie\\s*=").containsMatchIn(js).shouldBeFalse()
    }

    @Test
    fun `js não usa eval new Function ou innerHTML para conteúdo dinâmico`() {
        // eval e new Function() rodariam código arbitrário; innerHTML aceita HTML
        // que reabriria XSS quando vier do servidor.
        Regex("\\beval\\s*\\(").containsMatchIn(js).shouldBeFalse()
        Regex("new\\s+Function\\s*\\(").containsMatchIn(js).shouldBeFalse()
        Regex("\\.innerHTML\\s*=").containsMatchIn(js).shouldBeFalse()
        Regex("\\.outerHTML\\s*=").containsMatchIn(js).shouldBeFalse()
        Regex("document\\.write\\b").containsMatchIn(js).shouldBeFalse()
    }

    @Test
    fun `js não usa confirm nativo do navegador`() {
        Regex("(?<!function )\\bconfirm\\s*\\(").containsMatchIn(js).shouldBeFalse()
        html.shouldContain("id=\"confirm-dialog\"")
        js.shouldContain("function confirmAction")
    }

    @Test
    fun `modal de confirmacao fica centralizado`() {
        css.shouldContain("dialog#confirm-dialog")
        css.shouldContain("margin: auto")
        css.shouldContain("position: fixed")
        css.shouldContain("inset: 0")
    }

    @Test
    fun `modal access denied existe e usa renderizacao segura`() {
        html.shouldContain("id=\"access-denied-dialog\"")
        html.shouldContain("id=\"access-denied-title\"")
        html.shouldContain("id=\"access-denied-reason\"")
        html.shouldContain("class=\"access-denied-close\"")
        js.shouldContain("function showAccessDenied")
        js.shouldContain("function isAuthError")
        js.shouldContain("function wireAccessDeniedDialog")
        js.shouldContain("showAccessDenied('Email ou senha incorretos")
        js.shouldContain("showAccessDenied('Não foi possível registrar")
        js.shouldContain("access-denied-dialog")
        css.shouldContain("dialog#access-denied-dialog")
        css.shouldContain(".access-denied-x")
        css.shouldContain("@media (prefers-reduced-motion: reduce)")
    }

    @Test
    fun `categorias usam cards ricos com copiar id`() {
        js.shouldContain("class: 'cat-icon'")
        js.shouldContain("class: 'cat-info'")
        js.shouldContain("class: 'cat-copy-btn'")
        js.shouldContain("navigator.clipboard.writeText(c.id)")
        js.shouldContain("Copiado ✓")
        css.shouldContain(".cat-icon")
        css.shouldContain(".cat-copy-btn")
        css.shouldContain("#categoria-list .task-row::before")
    }

    @Test
    fun `metricas recebem icones e select usa tema escuro`() {
        js.shouldContain("'m-rps': '⚡'")
        js.shouldContain("'m-p95': '⏱'")
        js.shouldContain("'m-4xx': '⚠'")
        js.shouldContain("'m-5xx': '🛑'")
        js.shouldContain("card.dataset.icon = icon")
        css.shouldContain("color-scheme: dark")
        css.shouldContain(".metric::after")
        css.shouldContain("content: attr(data-icon)")
    }

    @Test
    fun `reset limpa estado local e campos visuais da sessao`() {
        js.shouldContain("function resetLocalSession")
        js.shouldContain("function resetForms")
        js.shouldContain("function resetRequestResponsePanels")
        js.shouldContain("function resetMetricsPanel")
        js.shouldContain("function resetDemoVerdicts")
        js.shouldContain("form.reset()")
        js.shouldContain("taskSearch = ''")
        js.shouldContain("lastRequest = null")
        js.shouldContain("auditLog.length = 0")
        js.shouldContain("defenseStats.rateLimit.current = 0")
        js.shouldContain("defenseStats.lastTriggered = null")
        js.shouldContain("sessão local resetada")
    }

    @Test
    fun `js usa textContent ou createElement para renderizar conteúdo`() {
        // proxy positivo: garante que houve esforço explícito de escape estrutural
        js.shouldContain("textContent")
        js.shouldContain("createElement")
    }

    @Test
    fun `css mantém paleta laranja e cyan da home pública`() {
        css.shouldContain("#FF6B2C")
        css.shouldContain("#22D3EE")
        css.shouldContain("#05070D")
    }

    @Test
    fun `html anuncia demo controlada e não indexa em buscadores`() {
        html.shouldContain("noindex")
        html.shouldContain("production mindset em modo demonstrável")
    }

    @Test
    fun `html declara aria live para regions com atualização dinâmica`() {
        // acessibilidade básica: leitores de tela precisam ser avisados.
        html.contains("aria-live").shouldBeTrue()
    }

    @Test
    fun `painel de demonstrações de defesa expõe os 6 cenários prometidos`() {
        val demos = listOf("rate-limit", "idor", "tampered-jwt", "alg-none", "sqli", "mass-assignment")
        demos.forEach { html.shouldContain("data-demo=\"$it\"") }
        demos.forEach { name ->
            // chave de objeto JS pode ser 'name': ou name: (sem aspas) quando for identificador valido.
            val pattern = Regex("(?:'${Regex.escape(name)}'|\\b${Regex.escape(name)})\\s*:")
            pattern.containsMatchIn(js).shouldBeTrue()
        }
    }

    @Test
    fun `playground busca config e bloqueia demos ofensivas quando desabilitadas`() {
        js.shouldContain("/playground/config")
        js.shouldContain("attackDemosEnabled")
        html.shouldContain("Demonstrações ofensivas disponíveis apenas em ambiente controlado.")
        html.contains("veja segurando ao vivo").shouldBeFalse()
    }

    @Test
    fun `decodeJwt não usa escape deprecada e tem padding base64`() {
        // C1: substituída a chamada `decodeURIComponent(escape(...))` (deprecada e
        // frágil com unicode) por TextDecoder + Uint8Array com padding base64url.
        Regex("escape\\s*\\(").containsMatchIn(js).shouldBeFalse()
        js.shouldContain("TextDecoder")
        // padding base64url e sinal de robustez: '='.repeat((4 - x.length % 4) % 4)
        js.shouldContain("'='.repeat")
    }

    @Test
    fun `demo rate-limit dispara 120 requests para garantir o estouro`() {
        // C2: card promete ~120, código precisa cumprir.
        Regex("length:\\s*120").containsMatchIn(js).shouldBeTrue()
        html.shouldContain("limite típico 100/min")
    }

    @Test
    fun `demo idor usa crypto randomUUID em vez de string aleatória`() {
        // C3: UUID v4 válido afasta a hipótese de 400 por input inválido.
        js.shouldContain("crypto.randomUUID()")
    }

    @Test
    fun `detectDefenseTrigger não dispara para 400 nem 401`() {
        // C7: validação trivial (400) e login errado (401) são UX, não defesa.
        // Verifica que o switch da função não mapeia mais esses status para flashDefense.
        val funcao =
            Regex("function detectDefenseTrigger[\\s\\S]+?\n  \\}", RegexOption.MULTILINE)
                .find(js)
                ?.value
                .orEmpty()
        funcao.shouldContain("status === 429")
        funcao.shouldContain("status === 403")
        Regex("status === 400").containsMatchIn(funcao).shouldBeFalse()
        Regex("status === 401").containsMatchIn(funcao).shouldBeFalse()
    }

    @Test
    fun `pill de rate limit tem decremento agendado via Retry-After`() {
        // C6: ao receber Retry-After, agenda reset via setTimeout para a pill voltar a 0.
        js.shouldContain("rateLimitResetTimer")
        js.shouldContain("Retry-After")
    }

    @Test
    fun `auto refresh evita loop quente para token com ttl curto`() {
        js.shouldContain("if (remaining <= 60_000)")
        js.shouldContain("autoRefresh();")
        js.shouldContain("setTimeout(autoRefresh, remaining - 60_000)")
    }

    @Test
    fun `alterar status documenta contrato command style do backend`() {
        js.shouldContain("Contrato atual do backend")
        js.shouldContain("POST /status/{status}")
        js.shouldContain("`/api/v1/tarefas/\${id}/status/\${status}`")
    }

    @Test
    fun `métricas aceitam count e total e avisam formato inesperado`() {
        js.shouldContain("http_server_requests_seconds_(?:count|total)")
        js.shouldContain("métricas presentes mas formato inesperado")
        js.shouldContain("console.debug('[playground] prometheus raw sample'")
    }

    @Test
    fun `polling de métricas é cancelado ao sair da aba`() {
        js.shouldContain("if (name !== 'metricas' && metricsTimer)")
        js.shouldContain("clearTimeout(metricsTimer)")
        js.shouldContain("if (!$('#tab-metricas').hidden) metricsTimer = setTimeout(refreshMetrics, 5000)")
    }

    @Test
    fun `playground usa app header com marca e controles operacionais`() {
        html.shouldContain("class=\"app-header playground-header\"")
        html.shouldContain("class=\"brand-wordmark\"")
        html.shouldContain("class=\"page-controls\"")
        html.shouldContain("id=\"lang-toggle\"")
        html.shouldContain("id=\"health-pill\"")
        html.shouldContain("id=\"auth-badge\"")
        html.contains("class=\"app-nav\"").let { check(!it) }
        html.contains("<a class=\"nav-link\"").let { check(!it) }
        html.shouldContain("LinkedIn")
        html.shouldContain("GitHub")
    }

    @Test
    fun `onda quatro adiciona skeleton loaders e loading state`() {
        js.shouldContain("function renderSkeletonRows")
        js.shouldContain("class: 'skeleton-row'")
        css.shouldContain(".skeleton-row")
        js.shouldContain("async function withLoadingState")
        css.shouldContain(".btn-loading")
    }

    @Test
    fun `empty state de tarefas tem svg e cta seguro`() {
        js.shouldContain("function renderEmptyTasksState")
        js.shouldContain("document.createElementNS('http://www.w3.org/2000/svg', 'svg')")
        js.shouldContain("Criar primeira tarefa")
        css.shouldContain(".empty-state")
    }

    @Test
    fun `exclusão usa desfazer antes de chamar delete`() {
        js.shouldContain("pendingDeleteTimers")
        js.shouldContain("exclusão agendada por 8s")
        js.shouldContain("actionLabel: 'Desfazer'")
        js.shouldContain("setTimeout(async () =>")
    }

    @Test
    fun `painel de defesa mostra aviso sandbox controlado`() {
        html.shouldContain("Ambiente sob controle")
        html.shouldContain("dados são descartáveis")
        css.shouldContain(".sandbox-banner")
    }

    @Test
    fun `logo do playground volta para home publica`() {
        html.shouldContain("href=\"/\"")
        html.shouldContain("aria-label=\"EGSYS Tasks API\"")
        html.shouldContain("class=\"brand\"")
    }

    @Test
    fun `mobile mostra painel lateral depois do conteúdo principal`() {
        css.shouldContain(".right { order: 2; }")
    }

    @Test
    fun `onda cinco adiciona i18n leve sem storage de token ou preferência`() {
        js.shouldContain("const i18n =")
        js.shouldContain("'pt-BR'")
        js.shouldContain("'en-US'")
        js.shouldContain("function t(key)")
        js.shouldContain("function toggleLanguage")
        html.shouldContain("id=\"lang-toggle\"")
    }

    @Test
    fun `onda cinco adiciona atalhos globais e dialog de ajuda`() {
        js.shouldContain("function handleGlobalShortcuts")
        js.shouldContain("document.addEventListener('keydown', handleGlobalShortcuts)")
        js.shouldContain("pendingGoto === 'g'")
        html.shouldContain("id=\"shortcuts-dialog\"")
        html.shouldContain("<kbd>g</kbd> <kbd>t</kbd>")
    }

    @Test
    fun `onda cinco adiciona busca client-side de tarefas`() {
        html.shouldContain("id=\"task-search\"")
        js.shouldContain("let taskSearch = ''")
        js.shouldContain("$('#task-search').addEventListener('input'")
        js.shouldContain("value.toLowerCase().includes(query)")
    }

    @Test
    fun `onda cinco adiciona sparkline de latência p95`() {
        html.shouldContain("id=\"latency-sparkline\"")
        js.shouldContain("const latencyHistory = []")
        js.shouldContain("function pushLatencySample")
        js.shouldContain("function renderLatencySparkline")
        css.shouldContain(".sparkline")
    }

    @Test
    fun `onda cinco adiciona health pill no topbar`() {
        html.shouldContain("id=\"health-pill\"")
        js.shouldContain("async function refreshHealth")
        js.shouldContain("/actuator/health")
        js.shouldContain("if (accessToken) headers.Authorization")
        js.shouldContain("refreshHealth();")
        js.shouldContain("setTimeout(refreshHealth, 30_000)")
        css.shouldContain(".health-up")
        css.shouldContain(".health-down")
    }

    @Test
    fun `tabs tem semântica completa e navegação por teclado`() {
        html.shouldContain("role=\"tablist\"")
        html.shouldContain("role=\"tab\"")
        html.shouldContain("role=\"tabpanel\"")
        html.shouldContain("aria-controls=\"tab-tarefas\"")
        html.shouldContain("aria-labelledby=\"tab-btn-tarefas\"")
        js.shouldContain("function handleTabKeydown")
        js.shouldContain("ev.key === 'ArrowRight'")
        js.shouldContain("ev.key === 'Home'")
        js.shouldContain("ev.key === 'End'")
    }

    // -------------------------------------------------------------
    // Intrusion Theater (visualizacao cinematografica dos demos)
    // -------------------------------------------------------------

    @Test
    fun `intrusion theater tem dialogo nativo com titulo acessivel`() {
        html.shouldContain("id=\"intrusion-theater\"")
        html.shouldContain("aria-labelledby=\"theater-title\"")
        html.shouldContain("id=\"theater-title\"")
        html.shouldContain("class=\"visually-hidden\"")
        css.shouldContain(".visually-hidden")
    }

    @Test
    fun `intrusion theater tem painel attacker e defense grid`() {
        html.shouldContain("id=\"theater-attacker-log\"")
        html.shouldContain("id=\"theater-defense-list\"")
        html.shouldContain("id=\"theater-verdict\"")
        html.shouldContain("id=\"theater-close\"")
    }

    @Test
    fun `intrusion theater orquestra boot recon payload defense response e verdict`() {
        // o roteiro deve cobrir todas as 6 demos com camadas, recon e veredictos.
        val demos = listOf("rate-limit", "idor", "tampered-jwt", "alg-none", "sqli", "mass-assignment")
        demos.forEach { name ->
            val pattern = Regex("(?:'${Regex.escape(name)}'|\\b${Regex.escape(name)})\\s*:")
            pattern.containsMatchIn(js).shouldBeTrue()
        }
        js.shouldContain("const theaterScripts =")
        js.shouldContain("verdictOk:")
        js.shouldContain("verdictFail:")
        js.shouldContain("expectedDefense:")
        js.shouldContain("async function openTheater")
        js.shouldContain("async function typeLine")
    }

    @Test
    fun `intrusion theater respeita prefers-reduced-motion`() {
        js.shouldContain("matchMedia('(prefers-reduced-motion: reduce)').matches")
        js.shouldContain("const baseDelay = reduced ? 0 : 60")
        css.shouldContain("@media (prefers-reduced-motion: reduce)")
        css.shouldContain(".theater-scanlines")
        // bloco de reduced-motion desliga as animacoes do teatro
        val reducedBlock =
            Regex("@media \\(prefers-reduced-motion: reduce\\)\\s*\\{[\\s\\S]+?\\n\\}", RegexOption.MULTILINE)
                .find(css)
                ?.value
                .orEmpty()
        reducedBlock.shouldContain("theater")
    }

    @Test
    fun `intrusion theater fecha com botao backdrop e devolve foco`() {
        // botao FECHAR deve ter listener explicito e o close do dialog deve restaurar foco
        // ao botao da demo que abriu o teatro.
        js.shouldContain("wireTheaterDialog")
        js.shouldContain("closeBtn.addEventListener('click'")
        js.shouldContain("dlg.addEventListener('close'")
        js.shouldContain("theaterReturnFocus")
        // backdrop click tambem fecha (o evento ocorre quando ev.target === dlg)
        js.shouldContain("if (ev.target === dlg) dlg.close")
    }

    @Test
    fun `cada demo reporta status e latencia reais ao teatro`() {
        // o teatro nao substitui o ataque; ele consome telemetria real via callback.
        js.shouldContain("reportTelemetry({ status: r.status, latencyMs })")
        js.shouldContain("await openTheater(name, result, attackResult, btn)")
        js.shouldContain("performance.now()")
    }

    @Test
    fun `i18n cobre as chaves do teatro nas duas linguas`() {
        js.shouldContain("'theater.close': 'FECHAR · ESC'")
        js.shouldContain("'theater.close': 'CLOSE · ESC'")
        js.shouldContain("'theater.attacker'")
        js.shouldContain("'theater.defense'")
    }
}
