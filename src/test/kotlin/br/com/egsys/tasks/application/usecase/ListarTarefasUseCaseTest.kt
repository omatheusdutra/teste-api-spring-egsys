package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.domain.port.TarefaRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ListarTarefasUseCaseTest {
    private val repository = mockk<TarefaRepository>()
    private val useCase = ListarTarefasUseCase(repository)

    @Test
    fun `lists active tasks from repository`() {
        val tarefas =
            listOf(
                tarefa(titulo = "Primeira"),
                tarefa(titulo = "Segunda"),
            )
        every { repository.findAllActive() } returns tarefas

        useCase.execute() shouldBe tarefas

        verify(exactly = 1) { repository.findAllActive() }
    }
}
