package br.com.egsys.tasks.domain.model

import br.com.egsys.tasks.domain.exception.InvalidDomainValueException
import java.util.UUID

@JvmInline
value class TarefaId private constructor(
    val value: UUID,
) {
    companion object {
        fun new(): TarefaId = from(UUID.randomUUID())

        fun from(value: UUID): TarefaId {
            if (value == NIL_UUID) {
                throw InvalidDomainValueException("tarefa.id nao pode ser um UUID nulo")
            }

            return TarefaId(value)
        }
    }
}

@JvmInline
value class CategoriaId private constructor(
    val value: UUID,
) {
    companion object {
        fun new(): CategoriaId = from(UUID.randomUUID())

        fun from(value: UUID): CategoriaId {
            if (value == NIL_UUID) {
                throw InvalidDomainValueException("categoria.id nao pode ser um UUID nulo")
            }

            return CategoriaId(value)
        }
    }
}

@JvmInline
value class UsuarioId private constructor(
    val value: UUID,
) {
    companion object {
        fun new(): UsuarioId = from(UUID.randomUUID())

        fun from(value: UUID): UsuarioId {
            if (value == NIL_UUID) {
                throw InvalidDomainValueException("usuario.id nao pode ser um UUID nulo")
            }

            return UsuarioId(value)
        }
    }
}

private val NIL_UUID: UUID = UUID(0L, 0L)
