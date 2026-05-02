package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.application.exception.TarefaNaoEncontradaException
import br.com.egsys.tasks.domain.model.TarefaHistorico
import br.com.egsys.tasks.domain.port.TarefaHistoricoRepository
import br.com.egsys.tasks.domain.port.TarefaRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.UUID

class ListarHistoricoTarefaUseCaseTest {
    private val tarefas = mockk<TarefaRepository>()
    private val historico = mockk<TarefaHistoricoRepository>()
    private val useCase = ListarHistoricoTarefaUseCase(tarefas, historico)

    @Test
    fun `lista historico apenas depois de validar propriedade da tarefa`() {
        val evento =
            TarefaHistorico(
                id = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36920"),
                tarefaId = taskId,
                ownerId = ownerId,
                eventType = "TarefaAtualizada",
                changedFields = setOf("titulo"),
                occurredAt = fixedNow,
            )
        every { tarefas.findByIdIncludingDeleted(taskId, ownerId) } returns tarefa()
        every { historico.findByTarefaId(taskId, ownerId) } returns listOf(evento)

        useCase.execute(taskId.value, ownerId.value) shouldContainExactly listOf(evento)

        verify(exactly = 1) { tarefas.findByIdIncludingDeleted(taskId, ownerId) }
        verify(exactly = 1) { historico.findByTarefaId(taskId, ownerId) }
    }

    @Test
    fun `falha sem consultar historico quando tarefa nao pertence ao usuario`() {
        every { tarefas.findByIdIncludingDeleted(taskId, ownerId) } returns null

        assertThrows<TarefaNaoEncontradaException> {
            useCase.execute(taskId.value, ownerId.value)
        }

        verify(exactly = 1) { tarefas.findByIdIncludingDeleted(taskId, ownerId) }
        verify(exactly = 0) { historico.findByTarefaId(any(), any()) }
    }
}
