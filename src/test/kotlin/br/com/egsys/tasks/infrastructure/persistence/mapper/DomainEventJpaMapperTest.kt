package br.com.egsys.tasks.infrastructure.persistence.mapper

import br.com.egsys.tasks.domain.model.TarefaAtualizada
import br.com.egsys.tasks.domain.model.TarefaConcluida
import br.com.egsys.tasks.domain.model.TarefaCriada
import br.com.egsys.tasks.domain.model.TarefaExcluida
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.UsuarioId
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class DomainEventJpaMapperTest {
    private val tarefaId = TarefaId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36911"))
    private val ownerId = UsuarioId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36914"))
    private val occurredAt = Instant.parse("2026-05-01T12:00:00Z")

    @Test
    fun `maps all task event types to outbox and history`() {
        val events =
            listOf(
                TarefaCriada(tarefaId, occurredAt),
                TarefaAtualizada(tarefaId, setOf("titulo", "status"), occurredAt),
                TarefaConcluida(tarefaId, occurredAt),
                TarefaExcluida(tarefaId, occurredAt),
            )

        val outbox = events.map { DomainEventJpaMapper.toOutboxEntity(it, ownerId, occurredAt) }
        val history = events.map { DomainEventJpaMapper.toHistoricoEntity(it, ownerId) }

        outbox.map { it.eventType } shouldBe
            listOf("TarefaCriada", "TarefaAtualizada", "TarefaConcluida", "TarefaExcluida")
        history[1].changedFields shouldBe "titulo,status"
        outbox[1].payload shouldBe
            """{"tarefaId":"${tarefaId.value}","eventType":"TarefaAtualizada","changedFields":["titulo","status"]}"""
    }
}
