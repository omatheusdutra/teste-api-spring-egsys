package br.com.egsys.tasks.support

import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import org.junit.jupiter.api.Test

class StaticHomeAssetTest {
    @Test
    fun `home publica estatica aponta para recursos locais`() {
        val html = requireNotNull(javaClass.classLoader.getResource("static/index.html")).readText()

        html.contains("EGSYS Tasks API").shouldBeTrue()
        html.contains("""<script src="/home.js"></script>""").shouldBeTrue()
        html.contains("fonts.googleapis.com").shouldBeFalse()
        html.contains("<script>").shouldBeFalse()
        html.contains("NUNCA CONFIE NO CLIENTE").shouldBeFalse()
    }
}
