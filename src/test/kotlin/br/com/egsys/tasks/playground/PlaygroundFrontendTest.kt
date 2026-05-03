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
}
