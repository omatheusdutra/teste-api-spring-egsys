package br.com.egsys.tasks.infrastructure.persistence

import br.com.egsys.tasks.domain.model.Categoria
import br.com.egsys.tasks.domain.model.DataHoraTarefa
import br.com.egsys.tasks.domain.model.Descricao
import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.TarefaStatus
import br.com.egsys.tasks.domain.model.Titulo
import br.com.egsys.tasks.domain.model.UsuarioId
import br.com.egsys.tasks.domain.port.CategoriaRepository
import br.com.egsys.tasks.domain.port.TarefaRepository
import br.com.egsys.tasks.support.PersistenceIntegrationTest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

@Tag("postgres")
class TarefaJpaRepositoryIntegrationTest : PersistenceIntegrationTest() {
    private val now = Instant.parse("2026-05-01T12:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val ownerId = UsuarioId.from(UUID.fromString("00000000-0000-0000-0000-000000000001"))
    private val otherOwnerId = UsuarioId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36999"))

    @Autowired
    private lateinit var tarefas: TarefaRepository

    @Autowired
    private lateinit var categorias: CategoriaRepository

    @Test
    fun `saves and restores task aggregate`() {
        val categoria = categoria("Casa")
        val tarefa =
            novaTarefa(
                id = "018f95df-0c7b-7af2-a199-447f82f36941",
                titulo = "Pagar aluguel",
                categoria = categoria,
                dataHora = "2026-05-01T13:00:00Z",
            )

        val saved = tarefas.save(tarefa)
        val restored = tarefas.findById(tarefa.id, ownerId).shouldNotBeNull()

        saved.id shouldBe tarefa.id
        restored.ownerId shouldBe ownerId
        restored.titulo shouldBe Titulo.of("Pagar aluguel")
        restored.descricao shouldBe Descricao.of("Criada pelo teste de persistencia")
        restored.categoria shouldBe categoria
        restored.dataHora shouldBe DataHoraTarefa.existente(Instant.parse("2026-05-01T13:00:00Z"))
        restored.status shouldBe TarefaStatus.PENDENTE
        restored.criadaEm shouldBe now
        restored.atualizadaEm shouldBe now
        restored.pullDomainEvents() shouldBe emptyList()
    }

    @Test
    fun `lists only active tasks ordered by schedule and id`() {
        val categoria = categoria("Trabalho")
        val first =
            novaTarefa(
                id = "018f95df-0c7b-7af2-a199-447f82f36942",
                titulo = "Primeira",
                categoria = categoria,
                dataHora = "2026-05-01T13:00:00Z",
            )
        val second =
            novaTarefa(
                id = "018f95df-0c7b-7af2-a199-447f82f36943",
                titulo = "Segunda",
                categoria = categoria,
                dataHora = "2026-05-01T14:00:00Z",
            )
        val deleted =
            novaTarefa(
                id = "018f95df-0c7b-7af2-a199-447f82f36944",
                titulo = "Excluida",
                categoria = categoria,
                dataHora = "2026-05-01T12:30:00Z",
            )

        tarefas.save(second)
        tarefas.save(first)
        deleted.excluir(Clock.fixed(Instant.parse("2026-05-01T12:10:00Z"), ZoneOffset.UTC))
        tarefas.save(deleted)

        tarefas.findAllActive(ownerId).map { it.id } shouldContainExactly listOf(first.id, second.id)
        tarefas.findAllActive(otherOwnerId) shouldBe emptyList()
    }

    @Test
    fun `soft deleted task is hidden from active lookup but available for audit`() {
        val categoria = categoria("Casa")
        val deleteClock = Clock.fixed(Instant.parse("2026-05-01T12:15:00Z"), ZoneOffset.UTC)
        val tarefa =
            novaTarefa(
                id = "018f95df-0c7b-7af2-a199-447f82f36945",
                titulo = "Arquivar",
                categoria = categoria,
                dataHora = "2026-05-01T13:00:00Z",
            )

        tarefas.save(tarefa)
        tarefa.excluir(deleteClock)
        tarefas.save(tarefa)

        tarefas.findById(tarefa.id, ownerId).shouldBeNull()
        tarefas.findByIdIncludingDeleted(tarefa.id, ownerId).shouldNotBeNull().excluidaEm shouldBe deleteClock.instant()
        tarefas.findByIdIncludingDeleted(tarefa.id, otherOwnerId).shouldBeNull()
    }

    private fun categoria(descricao: String): Categoria = categorias.findAll().first { it.descricao.value == descricao }

    private fun novaTarefa(
        id: String,
        titulo: String,
        categoria: Categoria,
        dataHora: String,
    ): Tarefa {
        val tarefa =
            Tarefa.criar(
                id = TarefaId.from(UUID.fromString(id)),
                ownerId = ownerId,
                titulo = Titulo.of(titulo),
                descricao = Descricao.of("Criada pelo teste de persistencia"),
                categoria = categoria,
                dataHora = DataHoraTarefa.agendadaPara(Instant.parse(dataHora), clock),
                clock = clock,
            )
        tarefa.pullDomainEvents()
        return tarefa
    }
}
