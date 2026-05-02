package br.com.egsys.tasks.domain.model

import br.com.egsys.tasks.domain.exception.InvalidDomainValueException

private val SINGLE_LINE_FORBIDDEN: (Char) -> Boolean = { it.code < 0x20 || it.code == 0x7F }
private val MULTI_LINE_FORBIDDEN: (Char) -> Boolean = {
    val code = it.code
    (code < 0x20 && it != '\n' && it != '\r' && it != '\t') || code == 0x7F
}

private fun rejectControlChars(
    value: String,
    forbidden: (Char) -> Boolean,
    onViolation: () -> InvalidDomainValueException,
) {
    if (value.any(forbidden)) {
        throw onViolation()
    }
}

@JvmInline
value class Titulo private constructor(
    val value: String,
) {
    companion object {
        private const val MAX_LENGTH = 200

        fun of(value: String): Titulo {
            val normalized = value.trim()

            if (normalized.isEmpty()) {
                throw InvalidDomainValueException("titulo nao pode ser vazio")
            }

            if (normalized.length > MAX_LENGTH) {
                throw InvalidDomainValueException("titulo deve ter no maximo 200 caracteres")
            }

            rejectControlChars(normalized, SINGLE_LINE_FORBIDDEN) {
                InvalidDomainValueException("titulo nao pode conter caracteres de controle")
            }

            return Titulo(normalized)
        }
    }
}

@JvmInline
value class Descricao private constructor(
    val value: String,
) {
    companion object {
        private const val MAX_LENGTH = 2_000

        fun of(value: String?): Descricao? {
            val normalized = value?.trim()

            if (normalized.isNullOrEmpty()) {
                return null
            }

            if (normalized.length > MAX_LENGTH) {
                throw InvalidDomainValueException("descricao deve ter no maximo 2000 caracteres")
            }

            rejectControlChars(normalized, MULTI_LINE_FORBIDDEN) {
                InvalidDomainValueException("descricao nao pode conter caracteres de controle")
            }

            return Descricao(normalized)
        }
    }
}

@JvmInline
value class DescricaoCategoria private constructor(
    val value: String,
) {
    companion object {
        private const val MAX_LENGTH = 120

        fun of(value: String): DescricaoCategoria {
            val normalized = value.trim()

            if (normalized.isEmpty()) {
                throw InvalidDomainValueException("categoria.descricao nao pode ser vazia")
            }

            if (normalized.length > MAX_LENGTH) {
                throw InvalidDomainValueException("categoria.descricao deve ter no maximo 120 caracteres")
            }

            rejectControlChars(normalized, SINGLE_LINE_FORBIDDEN) {
                InvalidDomainValueException("categoria.descricao nao pode conter caracteres de controle")
            }

            return DescricaoCategoria(normalized)
        }
    }
}
