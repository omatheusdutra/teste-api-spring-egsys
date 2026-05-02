package br.com.egsys.tasks.domain.model

import java.time.Instant
import java.util.UUID

data class TarefaHistorico(
    val id: UUID,
    val tarefaId: TarefaId,
    val ownerId: UsuarioId,
    val eventType: String,
    val changedFields: Set<String>,
    val occurredAt: Instant,
)
