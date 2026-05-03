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
        html.shouldContain("demo controlada")
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
        demos.forEach { js.shouldContain("'$it'") }
    }

    @Test
    fun `playground busca config e bloqueia demos ofensivas quando desabilitadas`() {
        js.shouldContain("/playground/config")
        js.shouldContain("attackDemosEnabled")
        html.shouldContain("Demonstrações ofensivas disponíveis apenas em ambiente controlado.")
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
    fun `brand-sub usa code para aproveitar regra visual sem css morto`() {
        html.shouldContain("<code>memória</code>")
        html.shouldContain("<code>sandbox</code>")
        css.shouldContain(".brand-sub code")
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
        html.shouldContain("Sandbox controlado")
        html.shouldContain("Nenhum dado real é afetado")
        css.shouldContain(".sandbox-banner")
    }

    @Test
    fun `logo do playground volta para home publica`() {
        html.shouldContain("href=\"/\"")
        html.shouldContain("aria-label=\"Voltar para a home\"")
        html.shouldContain("class=\"brand-link\"")
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
}
