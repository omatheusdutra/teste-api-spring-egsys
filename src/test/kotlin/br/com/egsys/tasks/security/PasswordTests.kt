package br.com.egsys.tasks.security

import br.com.egsys.tasks.infrastructure.security.PasswordPolicy
import br.com.egsys.tasks.infrastructure.security.WeakPasswordException
import io.kotest.assertions.throwables.shouldThrow
import org.junit.jupiter.api.Test

class PasswordTests {
    private val policy = PasswordPolicy()

    @Test
    fun `senha fraca rejeitada`() {
        shouldThrow<WeakPasswordException> {
            policy.validate("123456")
        }
    }

    @Test
    fun `senha sem letras mistas rejeitada`() {
        shouldThrow<WeakPasswordException> {
            policy.validate("senha-fraca-123!")
        }
    }

    @Test
    fun `senha sem letra minuscula rejeitada`() {
        shouldThrow<WeakPasswordException> {
            policy.validate("SENHA-FRACA-123!")
        }
    }

    @Test
    fun `senha sem numero rejeitada`() {
        shouldThrow<WeakPasswordException> {
            policy.validate("Senha-forte-sem-numero!")
        }
    }

    @Test
    fun `senha sem caractere especial rejeitada`() {
        shouldThrow<WeakPasswordException> {
            policy.validate("SenhaForte1234")
        }
    }

    @Test
    fun `senha forte aceita`() {
        policy.validate("Senha-forte-123!")
    }
}
