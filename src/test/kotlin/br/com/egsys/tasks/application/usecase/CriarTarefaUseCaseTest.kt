package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.application.exception.CategoriaNaoEncontradaException
import br.com.egsys.tasks.domain.exception.InvalidDomainValueException
import br.com.egsys.tasks.domain.model.CategoriaId
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
import java.util.UUID

class CriarTarefaUseCaseTest {
    private val tarefas = mockk<TarefaRepository>()
    private val categorias = mockk<CategoriaRepository>()
    private val useCase =
        CriarTarefaUseCase(
            tarefas = tarefas,
            categorias = categorias,
            clock = fixedClock,
            idGenerator = { taskId },
        )

    @Test
    fun `creates task after resolving category`() {
        val categoria = categoria()
        val captured = slot<Tarefa>()
        every { categorias.findById(categoryId) } returns categoria
        every { tarefas.save(capture(captured)) } answers { firstArg() }

        val created =
            useCase.execute(
                CriarTarefaCommand(
                    titulo = "  Lavar roupa  ",
                    descricao = " Separar roupas claras ",
                    categoriaId = categoryId.value,
                    dataHora = scheduledAt,
                ),
            )

        created.id shouldBe taskId
        created.titulo shouldBe Titulo.of("Lavar roupa")
        created.descricao shouldBe Descricao.of("Separar roupas claras")
        created.categoria shouldBe categoria
        created.dataHora shouldBe DataHoraTarefa.agendadaPara(scheduledAt, fixedClock)
        created.criadaEm shouldBe fixedNow
        captured.captured shouldBe created
        verify(exactly = 1) { categorias.findById(categoryId) }
        verify(exactly = 1) { tarefas.save(any()) }
    }

    @Test
    fun `rejects creation when category does not exist`() {
        every { categorias.findById(categoryId) } returns null

        assertThrows<CategoriaNaoEncontradaException> {
            useCase.execute(
                CriarTarefaCommand(
                    titulo = "Lavar roupa",
                    descricao = null,
                    categoriaId = categoryId.value,
                    dataHora = scheduledAt,
                ),
            )
        }.shouldHaveMessage("categoria nao encontrada: ${categoryId.value}")

        verify(exactly = 1) { categorias.findById(categoryId) }
        verify(exactly = 0) { tarefas.save(any()) }
    }

    @Test
    fun `rejects invalid category uuid before querying repository`() {
        val nilUuid = UUID(0L, 0L)

        assertThrows<InvalidDomainValueException> {
            useCase.execute(
                CriarTarefaCommand(
                    titulo = "Lavar roupa",
                    descricao = null,
                    categoriaId = nilUuid,
                    dataHora = Instant.parse("2026-05-01T13:00:00Z"),
                ),
            )
        }

        verify(exactly = 0) { categorias.findById(any<CategoriaId>()) }
        verify(exactly = 0) { tarefas.save(any()) }
    }
}
