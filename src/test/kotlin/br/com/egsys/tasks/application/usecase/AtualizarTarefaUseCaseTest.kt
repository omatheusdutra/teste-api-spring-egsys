package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.application.exception.CategoriaNaoEncontradaException
import br.com.egsys.tasks.application.exception.TarefaNaoEncontradaException
import br.com.egsys.tasks.domain.model.DataHoraTarefa
import br.com.egsys.tasks.domain.model.Descricao
import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.Titulo
import br.com.egsys.tasks.domain.port.CategoriaRepository
import br.com.egsys.tasks.domain.port.TarefaRepository
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant

class AtualizarTarefaUseCaseTest {
    private val tarefas = mockk<TarefaRepository>()
    private val categorias = mockk<CategoriaRepository>()
    private val useCase = AtualizarTarefaUseCase(tarefas, categorias, fixedClock)

    @Test
    fun `updates task after resolving task and category`() {
        val existente = tarefa()
        val novaCategoria = categoria(id = otherCategoryId, descricao = "Trabalho")
        val novaDataHora = Instant.parse("2026-05-01T14:00:00Z")
        val captured = slot<Tarefa>()
        every { tarefas.findById(taskId, ownerId) } returns existente
        every { categorias.findById(otherCategoryId) } returns novaCategoria
        every { tarefas.save(capture(captured)) } answers { firstArg() }

        val updated =
            useCase.execute(
                AtualizarTarefaCommand(
                    id = taskId.value,
                    ownerId = ownerId.value,
                    titulo = " Enviar relatorio ",
                    descricao = " PDF executivo ",
                    categoriaId = otherCategoryId.value,
                    dataHora = novaDataHora,
                ),
            )

        updated.titulo shouldBe Titulo.of("Enviar relatorio")
        updated.descricao shouldBe Descricao.of("PDF executivo")
        updated.categoria shouldBe novaCategoria
        updated.dataHora shouldBe DataHoraTarefa.agendadaPara(novaDataHora, fixedClock)
        updated.atualizadaEm shouldBe fixedNow
        captured.captured shouldBe updated
        verify(exactly = 1) { tarefas.findById(taskId, ownerId) }
        verify(exactly = 1) { categorias.findById(otherCategoryId) }
        verify(exactly = 1) { tarefas.save(any()) }
    }

    @Test
    fun `throws when task does not exist`() {
        every { tarefas.findById(taskId, ownerId) } returns null

        assertThrows<TarefaNaoEncontradaException> {
            useCase.execute(command())
        }.shouldHaveMessage("tarefa nao encontrada: ${taskId.value}")

        verify(exactly = 1) { tarefas.findById(taskId, ownerId) }
        verify(exactly = 0) { categorias.findById(any()) }
        verify(exactly = 0) { tarefas.save(any()) }
    }

    @Test
    fun `throws when new category does not exist`() {
        every { tarefas.findById(taskId, ownerId) } returns tarefa()
        every { categorias.findById(categoryId) } returns null

        assertThrows<CategoriaNaoEncontradaException> {
            useCase.execute(command())
        }.shouldHaveMessage("categoria nao encontrada: ${categoryId.value}")

        verify(exactly = 1) { tarefas.findById(taskId, ownerId) }
        verify(exactly = 1) { categorias.findById(categoryId) }
        verify(exactly = 0) { tarefas.save(any()) }
    }

    private fun command(): AtualizarTarefaCommand =
        AtualizarTarefaCommand(
            id = taskId.value,
            ownerId = ownerId.value,
            titulo = "Lavar roupa",
            descricao = null,
            categoriaId = categoryId.value,
            dataHora = scheduledAt,
        )
}
