package br.com.egsys.tasks.security

import br.com.egsys.tasks.domain.exception.InvalidDomainValueException
import br.com.egsys.tasks.domain.model.Descricao
import br.com.egsys.tasks.domain.model.DescricaoCategoria
import br.com.egsys.tasks.domain.model.Titulo
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.string.shouldContain
import org.junit.jupiter.api.Test

/**
 * Regressao F-001 (pentest interno 2026-05-02 cenario 23).
 *
 * Antes da correcao: POST /api/v1/tarefas com NUL byte no titulo retornava 500
 * — o byte escapava do dominio, alcancava o Postgres (que rejeita NUL em colunas
 * TEXT) e lancava DataIntegrityViolationException. ApiExceptionHandler mapeava
 * para 500 generico.
 *
 * Defesa: rejeitar caracteres de controle (bloco C0 + DEL) no value object — assim
 * a entrada e barrada antes de qualquer hop de persistencia, e o handler de
 * DomainException produz 400 ProblemDetail sem stack trace.
 *
 * Nota: Kotlin String.trim() considera VT/FF/CR/LF/etc whitespace e os strip-a
 * automaticamente nas bordas. Por isso os testes posicionam os caracteres de
 * controle NO MEIO da string — exatamente o vetor que um atacante usaria para
 * embutir um NUL escapado dentro de uma payload aparentemente normal.
 */
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
