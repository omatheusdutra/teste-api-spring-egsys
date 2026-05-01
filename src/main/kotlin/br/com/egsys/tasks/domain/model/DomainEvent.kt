package br.com.egsys.tasks.domain.model

import java.time.Instant

sealed interface DomainEvent {
    val tarefaId: TarefaId
    val occurredAt: Instant
}

data class TarefaCriada(
    override val tarefaId: TarefaId,
    override val occurredAt: Instant,
) : DomainEvent

data class TarefaAtualizada(
    override val tarefaId: TarefaId,
    val changedFields: Set<String>,
    override val occurredAt: Instant,
) : DomainEvent

data class TarefaConcluida(
    override val tarefaId: TarefaId,
    override val occurredAt: Instant,
) : DomainEvent

data class TarefaExcluida(
    override val tarefaId: TarefaId,
    override val occurredAt: Instant,
) : DomainEvent
