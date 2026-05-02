package br.com.egsys.tasks.infrastructure.persistence.mapper

import br.com.egsys.tasks.domain.model.DomainEvent
import br.com.egsys.tasks.domain.model.TarefaAtualizada
import br.com.egsys.tasks.domain.model.TarefaConcluida
import br.com.egsys.tasks.domain.model.TarefaCriada
import br.com.egsys.tasks.domain.model.TarefaExcluida
import br.com.egsys.tasks.domain.model.UsuarioId
import br.com.egsys.tasks.infrastructure.persistence.entity.OutboxEventJpaEntity
import br.com.egsys.tasks.infrastructure.persistence.entity.TarefaHistoricoJpaEntity
import java.time.Instant
import java.util.UUID

object DomainEventJpaMapper {
    fun toOutboxEntity(
        event: DomainEvent,
        ownerId: UsuarioId,
        createdAt: Instant,
    ): OutboxEventJpaEntity =
        OutboxEventJpaEntity(
            id = UUID.randomUUID(),
            aggregateType = "Tarefa",
            tarefaId = event.tarefaId.value,
            ownerId = ownerId.value,
            eventType = event.typeName(),
            payload = event.payload(),
            occurredAt = event.occurredAt,
            createdAt = createdAt,
            processedAt = null,
        )

    fun toHistoricoEntity(
        event: DomainEvent,
        ownerId: UsuarioId,
    ): TarefaHistoricoJpaEntity =
        TarefaHistoricoJpaEntity(
            id = UUID.randomUUID(),
            tarefaId = event.tarefaId.value,
            ownerId = ownerId.value,
            eventType = event.typeName(),
            changedFields = event.changedFields().joinToString(","),
            payload = event.payload(),
            occurredAt = event.occurredAt,
        )

    private fun DomainEvent.typeName(): String =
        when (this) {
            is TarefaCriada -> "TarefaCriada"
            is TarefaAtualizada -> "TarefaAtualizada"
            is TarefaConcluida -> "TarefaConcluida"
            is TarefaExcluida -> "TarefaExcluida"
        }

    private fun DomainEvent.changedFields(): Set<String> =
        when (this) {
            is TarefaAtualizada -> changedFields
            else -> emptySet()
        }

    private fun DomainEvent.payload(): String {
        val fields = changedFields().joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }
        return """{"tarefaId":"${tarefaId.value}","eventType":"${typeName()}","changedFields":$fields}"""
    }
}
