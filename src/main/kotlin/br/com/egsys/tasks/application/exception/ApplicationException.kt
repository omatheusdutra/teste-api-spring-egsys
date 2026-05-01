package br.com.egsys.tasks.application.exception

import java.util.UUID

open class ApplicationException(
    message: String,
) : RuntimeException(message)

class CategoriaNaoEncontradaException(
    id: UUID,
) : ApplicationException("categoria nao encontrada: $id")

class TarefaNaoEncontradaException(
    id: UUID,
) : ApplicationException("tarefa nao encontrada: $id")
