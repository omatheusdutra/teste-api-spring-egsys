package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.application.exception.TarefaNaoEncontradaException
import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaExcluida
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

class ExcluirTarefaUseCaseTest {
    private val repository = mockk<TarefaRepository>()
    private val useCase = ExcluirTarefaUseCase(repository, fixedClock)

    @Test
    fun `soft deletes active task and persists it`() {
        val tarefa = tarefa()
        val captured = slot<Tarefa>()
        every { repository.findById(taskId) } returns tarefa
        every { repository.save(capture(captured)) } answers { firstArg() }

        val deleted = useCase.execute(taskId.value)

        deleted.excluidaEm shouldBe fixedNow
        captured.captured shouldBe deleted
        deleted.pullDomainEvents() shouldContainExactly
            listOf(
                TarefaExcluida(tarefaId = taskId, occurredAt = fixedNow),
            )
        verify(exactly = 1) { repository.findById(taskId) }
        verify(exactly = 1) { repository.save(any()) }
    }

    @Test
    fun `throws when task does not exist or is already deleted`() {
        every { repository.findById(taskId) } returns null

        assertThrows<TarefaNaoEncontradaException> {
            useCase.execute(taskId.value)
        }.shouldHaveMessage("tarefa nao encontrada: ${taskId.value}")

        verify(exactly = 1) { repository.findById(taskId) }
        verify(exactly = 0) { repository.save(any()) }
    }
}
