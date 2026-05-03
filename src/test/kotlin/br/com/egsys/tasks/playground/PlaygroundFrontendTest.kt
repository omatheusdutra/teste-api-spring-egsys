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
        // CSP global proibe inline-script. Logo, a unica forma de carregar JS e via src=.
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
    fun `html nao tem handlers inline tipo onclick onload onerror`() {
        // CSP nao bloqueia handlers inline em todos os browsers, mas a politica de seguranca do projeto
        // proibe HTML attribute handlers — eles sao vetor classico de XSS injetado.
        val attrHandler = Regex("\\son(click|load|error|input|change|submit|focus|blur)\\s*=", RegexOption.IGNORE_CASE)
        attrHandler.containsMatchIn(html).shouldBeFalse()
    }

    @Test
    fun `js nunca persiste token em storage do navegador`() {
        // Verifica USO da API, nao mera mencao em comentario.
        Regex("\\b(local|session)Storage\\s*[\\.\\[\\(]").containsMatchIn(js).shouldBeFalse()
        Regex("document\\.cookie\\s*=").containsMatchIn(js).shouldBeFalse()
    }

    @Test
    fun `js nao usa eval new Function ou innerHTML para conteudo dinamico`() {
        // eval e new Function() rodariam codigo arbitrario; innerHTML aceita HTML
        // que reabriria XSS quando vier do servidor.
        Regex("\\beval\\s*\\(").containsMatchIn(js).shouldBeFalse()
        Regex("new\\s+Function\\s*\\(").containsMatchIn(js).shouldBeFalse()
        Regex("\\.innerHTML\\s*=").containsMatchIn(js).shouldBeFalse()
        Regex("\\.outerHTML\\s*=").containsMatchIn(js).shouldBeFalse()
        Regex("document\\.write\\b").containsMatchIn(js).shouldBeFalse()
    }

    @Test
    fun `js nao usa confirm nativo do navegador`() {
        Regex("(?<!function )\\bconfirm\\s*\\(").containsMatchIn(js).shouldBeFalse()
        html.shouldContain("id=\"confirm-dialog\"")
        js.shouldContain("function confirmAction")
    }

    @Test
    fun `js usa textContent ou createElement para renderizar conteudo`() {
        // proxy positivo: garante que houve esforco explicito de escape estrutural
        js.shouldContain("textContent")
        js.shouldContain("createElement")
    }

    @Test
    fun `css mantem paleta laranja e cyan da home publica`() {
        css.shouldContain("#FF6B2C")
        css.shouldContain("#22D3EE")
        css.shouldContain("#05070D")
    }

    @Test
    fun `html anuncia demo controlada e nao indexa em buscadores`() {
        html.shouldContain("noindex")
        html.shouldContain("demo controlada")
    }

    @Test
    fun `html declara aria live para regions com atualizacao dinamica`() {
        // acessibilidade basica: leitores de tela precisam ser avisados.
        html.contains("aria-live").shouldBeTrue()
    }

    @Test
    fun `painel de demonstracoes de defesa expoe os 6 cenarios prometidos`() {
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
    fun `decodeJwt nao usa escape deprecada e tem padding base64`() {
        // C1: substituida a chamada `decodeURIComponent(escape(...))` (deprecada e
        // fragil com unicode) por TextDecoder + Uint8Array com padding base64url.
        Regex("escape\\s*\\(").containsMatchIn(js).shouldBeFalse()
        js.shouldContain("TextDecoder")
        // padding base64url e sinal de robustez: '='.repeat((4 - x.length % 4) % 4)
        js.shouldContain("'='.repeat")
    }

    @Test
    fun `demo rate-limit dispara 120 requests para garantir o estouro`() {
        // C2: card promete ~120, codigo precisa cumprir.
        Regex("length:\\s*120").containsMatchIn(js).shouldBeTrue()
        html.shouldContain("limite típico 100/min")
    }

    @Test
    fun `demo idor usa crypto randomUUID em vez de string aleatoria`() {
        // C3: UUID v4 valido afasta a hipotese de 400 por input invalido.
        js.shouldContain("crypto.randomUUID()")
    }

    @Test
    fun `detectDefenseTrigger nao dispara para 400 nem 401`() {
        // C7: validacao trivial (400) e login errado (401) sao UX, nao defesa.
        // Verifica que o switch da funcao nao mapeia mais esses status para flashDefense.
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
    fun `metricas aceitam count e total e avisam formato inesperado`() {
        js.shouldContain("http_server_requests_seconds_(?:count|total)")
        js.shouldContain("metricas presentes mas formato inesperado")
        js.shouldContain("console.debug('[playground] prometheus raw sample'")
    }

    @Test
    fun `polling de metricas e cancelado ao sair da aba`() {
        js.shouldContain("if (name !== 'metricas' && metricsTimer)")
        js.shouldContain("clearTimeout(metricsTimer)")
        js.shouldContain("if (!$('#tab-metricas').hidden) metricsTimer = setTimeout(refreshMetrics, 5000)")
    }

    @Test
    fun `brand-sub usa code para aproveitar regra visual sem css morto`() {
        html.shouldContain("<code>memoria</code>")
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
    fun `exclusao usa desfazer antes de chamar delete`() {
        js.shouldContain("pendingDeleteTimers")
        js.shouldContain("exclusao agendada por 8s")
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
    fun `logo do playground linka para linkedin da egsys`() {
        html.shouldContain("https://br.linkedin.com/company/egsys")
        html.shouldContain("rel=\"noopener\"")
        html.shouldContain("class=\"brand-link\"")
    }

    @Test
    fun `mobile mostra painel lateral depois do conteudo principal`() {
        css.shouldContain(".right { order: 2; }")
    }
}
