package br.com.egsys.tasks.infrastructure.security

import org.springframework.stereotype.Component

@Component
class PasswordPolicy {
    @Suppress("ThrowsCount")
    fun validate(rawPassword: String) {
        if (rawPassword.length < MIN_LENGTH) {
            throw WeakPasswordException("senha deve ter ao menos $MIN_LENGTH caracteres")
        }
        if (!rawPassword.any(Char::isUpperCase) || !rawPassword.any(Char::isLowerCase)) {
            throw WeakPasswordException("senha deve conter letras maiusculas e minusculas")
        }
        if (!rawPassword.any(Char::isDigit)) {
            throw WeakPasswordException("senha deve conter ao menos um numero")
        }
        if (rawPassword.none { !it.isLetterOrDigit() }) {
            throw WeakPasswordException("senha deve conter ao menos um caractere especial")
        }
    }

    private companion object {
        const val MIN_LENGTH = 12
    }
}
