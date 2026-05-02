package br.com.egsys.tasks.infrastructure.persistence.mapper

import br.com.egsys.tasks.domain.model.TarefaHistorico
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.UsuarioId
import br.com.egsys.tasks.infrastructure.persistence.entity.TarefaHistoricoJpaEntity

object TarefaHistoricoJpaMapper {
    fun toDomain(entity: TarefaHistoricoJpaEntity): TarefaHistorico =
        TarefaHistorico(
            id = requireJpaField(entity.id, "tarefaHistorico.id"),
            tarefaId = TarefaId.from(requireJpaField(entity.tarefaId, "tarefaHistorico.tarefaId")),
            ownerId = UsuarioId.from(requireJpaField(entity.ownerId, "tarefaHistorico.ownerId")),
            eventType = entity.eventType,
            changedFields = entity.changedFields.toChangedFieldSet(),
            occurredAt = requireJpaField(entity.occurredAt, "tarefaHistorico.occurredAt"),
        )

    private fun String.toChangedFieldSet(): Set<String> =
        split(",")
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toSet()
}
