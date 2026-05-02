package br.com.egsys.tasks.infrastructure.persistence.mapper

import br.com.egsys.tasks.infrastructure.persistence.entity.TarefaHistoricoJpaEntity
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID

class TarefaHistoricoJpaMapperTest {
    @Test
    fun `maps changed fields from persistence format`() {
        val entity =
            TarefaHistoricoJpaEntity(
                id = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36931"),
                tarefaId = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36911"),
                ownerId = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36914"),
                eventType = "TarefaAtualizada",
                changedFields = "titulo, status",
                payload = "{}",
                occurredAt = Instant.parse("2026-05-01T12:00:00Z"),
            )

        val historico = TarefaHistoricoJpaMapper.toDomain(entity)

        historico.changedFields shouldBe setOf("titulo", "status")
    }

    @Test
    fun `maps empty changed fields`() {
        val entity =
            TarefaHistoricoJpaEntity(
                id = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36932"),
                tarefaId = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36911"),
                ownerId = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36914"),
                eventType = "TarefaCriada",
                changedFields = "",
                payload = "{}",
                occurredAt = Instant.parse("2026-05-01T12:00:00Z"),
            )

        val historico = TarefaHistoricoJpaMapper.toDomain(entity)

        historico.changedFields shouldBe emptySet()
    }
}
