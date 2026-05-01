package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.application.exception.TarefaNaoEncontradaException
import br.com.egsys.tasks.domain.port.TarefaRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class BuscarTarefaUseCaseTest {
    private val repository = mockk<TarefaRepository>()
    private val useCase = BuscarTarefaUseCase(repository)

    @Test
    fun `returns active task by id`() {
        val tarefa = tarefa()
        every { repository.findById(taskId) } returns tarefa

        useCase.execute(taskId.value) shouldBe tarefa

        verify(exactly = 1) { repository.findById(taskId) }
    }

    @Test
    fun `throws when task does not exist or is deleted`() {
        every { repository.findById(taskId) } returns null

        assertThrows<TarefaNaoEncontradaException> {
            useCase.execute(taskId.value)
        }.shouldHaveMessage("tarefa nao encontrada: ${taskId.value}")

        verify(exactly = 1) { repository.findById(taskId) }
    }
}
