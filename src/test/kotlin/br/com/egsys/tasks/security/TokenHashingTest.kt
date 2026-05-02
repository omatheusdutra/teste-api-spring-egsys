package br.com.egsys.tasks.security

import br.com.egsys.tasks.infrastructure.security.TokenHashing
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test

class TokenHashingTest {
    @Test
    fun `gera SHA-256 hexadecimal deterministico para tokens opacos`() {
        TokenHashing.sha256Hex("abc") shouldBe
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    }

    @Test
    fun `hash muda quando token bruto muda`() {
        TokenHashing.sha256Hex("refresh-token-a") shouldBe
            TokenHashing.sha256Hex("refresh-token-a")
        TokenHashing.sha256Hex("refresh-token-a").length shouldBe 64
        (TokenHashing.sha256Hex("refresh-token-a") == TokenHashing.sha256Hex("refresh-token-b")) shouldBe false
    }
}
