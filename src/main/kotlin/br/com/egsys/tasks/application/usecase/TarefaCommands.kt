package br.com.egsys.tasks.application.usecase

import java.time.Instant
import java.util.UUID

data class CriarTarefaCommand(
    val ownerId: UUID,
    val titulo: String,
    val descricao: String?,
    val categoriaId: UUID,
    val dataHora: Instant,
)

data class AtualizarTarefaCommand(
    val id: UUID,
    val ownerId: UUID,
    val titulo: String,
    val descricao: String?,
    val categoriaId: UUID,
    val dataHora: Instant,
)
