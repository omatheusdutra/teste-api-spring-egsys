package br.com.egsys.tasks.domain.model

import br.com.egsys.tasks.domain.exception.InvalidDomainValueException

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

            return DescricaoCategoria(normalized)
        }
    }
}
