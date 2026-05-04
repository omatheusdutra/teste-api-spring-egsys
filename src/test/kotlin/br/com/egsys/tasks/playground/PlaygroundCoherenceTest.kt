package br.com.egsys.tasks.playground

import io.kotest.matchers.file.shouldExist
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test
import java.io.File

class PlaygroundCoherenceTest {
    private val homeHtml: String by lazy {
        requireNotNull(javaClass.classLoader.getResource("static/index.html")).readText()
    }

    private val playgroundHtml: String by lazy {
        requireNotNull(javaClass.classLoader.getResource("playground/playground.html")).readText()
    }

    @Test
    fun `home e playground carregam css compartilhado e fontes locais`() {
        listOf(homeHtml, playgroundHtml).forEach { html ->
            html.shouldContain("/assets/fonts/fonts.css")
            html.shouldContain("/assets/shared.css")
        }
    }

    @Test
    fun `home e playground compartilham marca e header base`() {
        listOf(homeHtml, playgroundHtml).forEach { html ->
            html.shouldContain("class=\"app-header")
            html.shouldContain("class=\"brand-wordmark\"")
            html.shouldContain("class=\"brand-name\"")
            html.shouldContain("class=\"brand-context\"")
        }
    }

    @Test
    fun `home exibe navegacao publica principal`() {
        homeHtml.shouldContain("class=\"app-nav\"")
        homeHtml.shouldContain("href=\"/\"")
        homeHtml.shouldContain("href=\"/playground\"")
        homeHtml.shouldContain("href=\"/swagger-ui.html\"")
        homeHtml.shouldContain("https://br.linkedin.com/company/egsys")
        homeHtml.shouldContain("https://github.com/omatheusdutra/teste-api-spring-egsys")
    }

    @Test
    fun `playground prioriza controles operacionais no topo`() {
        playgroundHtml.shouldContain("class=\"page-controls\"")
        playgroundHtml.contains("class=\"app-nav\"").let { check(!it) }
        playgroundHtml.contains("<a class=\"nav-link\"").let { check(!it) }
        playgroundHtml.shouldContain("href=\"/\"")
    }

    @Test
    fun `fontes ficam auto hospedadas sem cdn externa`() {
        File("src/main/resources/static/assets/fonts/InterVariable.woff2").shouldExist()
        File("src/main/resources/static/assets/fonts/JetBrainsMono-Regular.woff2").shouldExist()
        File("src/main/resources/static/assets/fonts/JetBrainsMono-Medium.woff2").shouldExist()
        File("src/main/resources/static/assets/fonts/JetBrainsMono-Bold.woff2").shouldExist()

        homeHtml.shouldContain("fonts.css")
        playgroundHtml.shouldContain("fonts.css")
        homeHtml.contains("fonts.googleapis.com").let { check(!it) }
        playgroundHtml.contains("fonts.googleapis.com").let { check(!it) }
        homeHtml.contains("fonts.gstatic.com").let { check(!it) }
        playgroundHtml.contains("fonts.gstatic.com").let { check(!it) }
    }
}
