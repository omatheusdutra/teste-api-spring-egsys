package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.application.exception.TarefaNaoEncontradaException
import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaConcluida
import br.com.egsys.tasks.domain.model.TarefaStatus
import br.com.egsys.tasks.domain.port.TarefaRepository
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class AlterarStatusTarefaUseCaseTest {
    private val repository = mockk<TarefaRepository>()
    private val useCase = AlterarStatusTarefaUseCase(repository, fixedClock)

    @Test
    fun `conclui tarefa respeitando owner e persiste evento de dominio`() {
        val tarefa = tarefa()
        val captured = slot<Tarefa>()
        every { repository.findById(taskId, ownerId) } returns tarefa
        every { repository.save(capture(captured)) } answers { firstArg() }

        val updated =
            useCase.execute(
                AlterarStatusTarefaCommand(
                    id = taskId.value,
                    ownerId = ownerId.value,
                    status = TarefaStatus.CONCLUIDA,
                ),
            )

        updated.status shouldBe TarefaStatus.CONCLUIDA
        updated.pullDomainEvents() shouldContainExactly
            listOf(TarefaConcluida(tarefaId = taskId, occurredAt = fixedNow))
        captured.captured shouldBe updated
        verify(exactly = 1) { repository.findById(taskId, ownerId) }
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `inicia tarefa pendente`() {
        val tarefa = tarefa()
        every { repository.findById(taskId, ownerId) } returns tarefa
        every { repository.save(any()) } answers { firstArg() }

        val updated =
            useCase.execute(
                AlterarStatusTarefaCommand(
                    id = taskId.value,
                    ownerId = ownerId.value,
                    status = TarefaStatus.EM_ANDAMENTO,
                ),
            )

        updated.status shouldBe TarefaStatus.EM_ANDAMENTO
    }

    @Test
    fun `cancela tarefa pendente`() {
        val tarefa = tarefa()
        every { repository.findById(taskId, ownerId) } returns tarefa
        every { repository.save(any()) } answers { firstArg() }

        val updated =
            useCase.execute(
                AlterarStatusTarefaCommand(
                    id = taskId.value,
                    ownerId = ownerId.value,
                    status = TarefaStatus.CANCELADA,
                ),
            )

        updated.status shouldBe TarefaStatus.CANCELADA
    }

    @Test
    fun `rejeita voltar para status inicial`() {
        every { repository.findById(taskId, ownerId) } returns tarefa()

        assertThrows<br.com.egsys.tasks.domain.exception.InvalidTaskStateTransitionException> {
            useCase.execute(
                AlterarStatusTarefaCommand(
                    id = taskId.value,
                    ownerId = ownerId.value,
                    status = TarefaStatus.PENDENTE,
                ),
            )
        }.shouldHaveMessage("status PENDENTE e apenas inicial")

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `falha quando tarefa nao existe para o owner autenticado`() {
        every { repository.findById(taskId, ownerId) } returns null

        assertThrows<TarefaNaoEncontradaException> {
            useCase.execute(
                AlterarStatusTarefaCommand(
                    id = taskId.value,
                    ownerId = ownerId.value,
                    status = TarefaStatus.EM_ANDAMENTO,
                ),
            )
        }

        verify(exactly = 1) { repository.findById(taskId, ownerId) }
        verify(exactly = 0) { repository.save(any()) }
    }
}
