package br.com.egsys.tasks.security

import br.com.egsys.tasks.domain.exception.InvalidDomainValueException
import br.com.egsys.tasks.domain.model.Descricao
import br.com.egsys.tasks.domain.model.DescricaoCategoria
import br.com.egsys.tasks.domain.model.Titulo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

class NulByteRejectionTests {
    @Test
    fun `titulo com NUL byte no meio e rejeitado pelo dominio`() {
        val nulInside = "abc\u0000def"

        val ex = shouldThrow<InvalidDomainValueException> { Titulo.of(nulInside) }
        ex.message.shouldContain("controle")
    }

    @Test
    fun `descricao com NUL byte no meio e rejeitada pelo dominio`() {
        val nulInside = "trecho com NUL\u0000aqui"

        val ex = shouldThrow<InvalidDomainValueException> { Descricao.of(nulInside) }
        ex.message.shouldContain("controle")
    }

    @Test
    fun `categoria descricao com NUL byte no meio e rejeitada pelo dominio`() {
        val nulInside = "Casa\u0000Trabalho"

        val ex = shouldThrow<InvalidDomainValueException> { DescricaoCategoria.of(nulInside) }
        ex.message.shouldContain("controle")
    }

    @Test
    fun `titulo bloqueia outros caracteres de controle do bloco C0 quando aparecem no meio`() {
        // ESC (\u001B), BEL (\u0007), VT (\u000B), BS (\b), DEL (\u007F)
        listOf("\u001B", "\u0007", "\u000B", "\b", "\u007F").forEach { ch ->
            shouldThrow<InvalidDomainValueException> { Titulo.of("ant${ch}post") }
        }
    }

    @Test
    fun `descricao tolera quebra de linha real porque e campo multiline`() {
        val multiline = "linha 1\nlinha 2\r\nlinha 3"

        Descricao.of(multiline)?.value!!.shouldContain("linha 2")
    }

    @Test
    fun `descricao bloqueia BEL e ESC mesmo em multiline`() {
        shouldThrow<InvalidDomainValueException> { Descricao.of("alerta\u0007ataque") }
        shouldThrow<InvalidDomainValueException> { Descricao.of("escape\u001Bsequence") }
    }
}
