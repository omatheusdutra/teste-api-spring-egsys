package br.com.egsys.tasks.support

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.Test

class StaticHomeAssetTest {
    @Test
    fun `home publica estatica aponta para recursos locais`() {
        val html = requireNotNull(javaClass.classLoader.getResource("static/index.html")).readText()

        html.contains("EGSYS Tasks API").shouldBeTrue()
        html.contains("""<link rel="icon" href="/egsys-logo.svg" type="image/svg+xml">""").shouldBeTrue()
        html.contains("""<img src="/egsys-logo.svg" alt="Grupo Bringel" class="brand-mark-img">""").shouldBeTrue()
        html.contains("https://github.com/omatheusdutra/teste-api-spring-egsys").shouldBeTrue()
        html.contains("href=\"/playground\"").shouldBeTrue()
        html.contains("feita para devolver tempo às pessoas.").shouldBeTrue()
        html.contains("Abrir Swagger UI").shouldBeTrue()
        html.contains("OpenAPI JSON").shouldBeTrue()
        html.contains("Health protegido").shouldBeTrue()
        html.contains("""<script src="/home.js"></script>""").shouldBeTrue()
        html.contains("""<div class="brand-mark">eB</div>""").shouldBeFalse()
        html.contains("fonts.googleapis.com").shouldBeFalse()
        html.contains("<script>").shouldBeFalse()
        html.contains("NUNCA CONFIE NO CLIENTE").shouldBeFalse()
    }
}
