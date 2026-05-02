package br.com.egsys.tasks.domain.model

import br.com.egsys.tasks.domain.exception.InvalidTaskStateTransitionException
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

class TarefaTest {
    private val now: Instant = Instant.parse("2026-05-01T12:00:00Z")
    private val clock: Clock = Clock.fixed(now, ZoneOffset.UTC)
    private val later: Instant = Instant.parse("2026-05-01T13:00:00Z")
    private val ownerId = UsuarioId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36914"))
    private val categoria =
        Categoria(
            id = CategoriaId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36912")),
            descricao = DescricaoCategoria.of("Casa"),
        )

    @Test
    fun `creates pending task and records creation event`() {
        val tarefa = novaTarefa()

        tarefa.id.value shouldBe UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36911")
        tarefa.ownerId shouldBe ownerId
        tarefa.titulo shouldBe Titulo.of("Lavar roupa")
        tarefa.descricao shouldBe Descricao.of("Separar roupas claras")
        tarefa.categoria shouldBe categoria
        tarefa.dataHora shouldBe DataHoraTarefa.agendadaPara(later, clock)
        tarefa.status shouldBe TarefaStatus.PENDENTE
        tarefa.criadaEm shouldBe now
        tarefa.atualizadaEm shouldBe now
        tarefa.excluidaEm shouldBe null

        tarefa.pullDomainEvents() shouldContainExactly
            listOf(
                TarefaCriada(tarefaId = tarefa.id, occurredAt = now),
            )
        tarefa.pullDomainEvents() shouldBe emptyList()
    }

    @Test
    fun `updates task fields and records changed fields`() {
        val tarefa = novaTarefa()
        tarefa.pullDomainEvents()
        val updateClock = Clock.fixed(Instant.parse("2026-05-01T12:10:00Z"), ZoneOffset.UTC)
        val novaCategoria =
            Categoria(
                id = CategoriaId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36913")),
                descricao = DescricaoCategoria.of("Trabalho"),
            )
        val novaDataHora = DataHoraTarefa.agendadaPara(Instant.parse("2026-05-01T14:00:00Z"), updateClock)

        tarefa.atualizar(
            titulo = Titulo.of("Enviar relatorio"),
            descricao = Descricao.of("Enviar PDF para a diretoria"),
            categoria = novaCategoria,
            dataHora = novaDataHora,
            clock = updateClock,
        )

        tarefa.titulo shouldBe Titulo.of("Enviar relatorio")
        tarefa.descricao shouldBe Descricao.of("Enviar PDF para a diretoria")
        tarefa.categoria shouldBe novaCategoria
        tarefa.dataHora shouldBe novaDataHora
        tarefa.atualizadaEm shouldBe updateClock.instant()
        tarefa.pullDomainEvents() shouldContainExactly
            listOf(
                TarefaAtualizada(
                    tarefaId = tarefa.id,
                    changedFields = setOf("titulo", "descricao", "categoria", "dataHora"),
                    occurredAt = updateClock.instant(),
                ),
            )
    }

    @Test
    fun `no-op update does not change timestamp or record event`() {
        val tarefa = novaTarefa()
        tarefa.pullDomainEvents()

        tarefa.atualizar(
            titulo = tarefa.titulo,
            descricao = tarefa.descricao,
            categoria = tarefa.categoria,
            dataHora = tarefa.dataHora,
            clock = Clock.fixed(Instant.parse("2026-05-01T12:20:00Z"), ZoneOffset.UTC),
        )

        tarefa.atualizadaEm shouldBe now
        tarefa.pullDomainEvents() shouldBe emptyList()
    }

    @Test
    fun `updates nullable description edges`() {
        val tarefa =
            Tarefa.reconstituir(
                id = TarefaId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36911")),
                ownerId = ownerId,
                titulo = Titulo.of("Lavar roupa"),
                descricao = null,
                categoria = categoria,
                dataHora = DataHoraTarefa.existente(later),
                status = TarefaStatus.PENDENTE,
                criadaEm = now,
                atualizadaEm = now,
                excluidaEm = null,
            )
        val describedClock = Clock.fixed(Instant.parse("2026-05-01T12:20:00Z"), ZoneOffset.UTC)
        val clearedClock = Clock.fixed(Instant.parse("2026-05-01T12:25:00Z"), ZoneOffset.UTC)

        tarefa.atualizar(
            titulo = tarefa.titulo,
            descricao = null,
            categoria = tarefa.categoria,
            dataHora = tarefa.dataHora,
            clock = describedClock,
        )

        tarefa.pullDomainEvents() shouldBe emptyList()

        tarefa.atualizar(
            titulo = tarefa.titulo,
            descricao = Descricao.of("Separar roupas escuras"),
            categoria = tarefa.categoria,
            dataHora = tarefa.dataHora,
            clock = describedClock,
        )

        tarefa.descricao shouldBe Descricao.of("Separar roupas escuras")
        tarefa.pullDomainEvents() shouldContainExactly
            listOf(
                TarefaAtualizada(
                    tarefaId = tarefa.id,
                    changedFields = setOf("descricao"),
                    occurredAt = describedClock.instant(),
                ),
            )

        tarefa.atualizar(
            titulo = tarefa.titulo,
            descricao = null,
            categoria = tarefa.categoria,
            dataHora = tarefa.dataHora,
            clock = clearedClock,
        )

        tarefa.descricao shouldBe null
        tarefa.pullDomainEvents() shouldContainExactly
            listOf(
                TarefaAtualizada(
                    tarefaId = tarefa.id,
                    changedFields = setOf("descricao"),
                    occurredAt = clearedClock.instant(),
                ),
            )
    }

    @Test
    fun `moves through valid status transitions`() {
        val tarefa = novaTarefa()
        tarefa.pullDomainEvents()
        val startClock = Clock.fixed(Instant.parse("2026-05-01T12:30:00Z"), ZoneOffset.UTC)
        val doneClock = Clock.fixed(Instant.parse("2026-05-01T12:40:00Z"), ZoneOffset.UTC)

        tarefa.iniciar(startClock)
        tarefa.status shouldBe TarefaStatus.EM_ANDAMENTO
        tarefa.atualizadaEm shouldBe startClock.instant()
        tarefa.pullDomainEvents() shouldContainExactly
            listOf(
                TarefaAtualizada(
                    tarefaId = tarefa.id,
                    changedFields = setOf("status"),
                    occurredAt = startClock.instant(),
                ),
            )

        tarefa.concluir(doneClock)
        tarefa.status shouldBe TarefaStatus.CONCLUIDA
        tarefa.atualizadaEm shouldBe doneClock.instant()
        tarefa.pullDomainEvents() shouldContainExactly
            listOf(
                TarefaConcluida(tarefaId = tarefa.id, occurredAt = doneClock.instant()),
            )
    }

    @Test
    fun `rejects invalid status transitions`() {
        val tarefa = novaTarefa()
        tarefa.cancelar(Clock.fixed(Instant.parse("2026-05-01T12:30:00Z"), ZoneOffset.UTC))

        assertThrows<InvalidTaskStateTransitionException> {
            tarefa.concluir(Clock.fixed(Instant.parse("2026-05-01T12:40:00Z"), ZoneOffset.UTC))
        }.shouldHaveMessage("nao e permitido mover tarefa de CANCELADA para CONCLUIDA")
    }

    @Test
    fun `soft deletes task and blocks future mutations`() {
        val tarefa = novaTarefa()
        tarefa.pullDomainEvents()
        val deleteClock = Clock.fixed(Instant.parse("2026-05-01T12:50:00Z"), ZoneOffset.UTC)

        tarefa.excluir(deleteClock)

        tarefa.excluidaEm.shouldNotBeNull() shouldBe deleteClock.instant()
        tarefa.pullDomainEvents() shouldContainExactly
            listOf(
                TarefaExcluida(tarefaId = tarefa.id, occurredAt = deleteClock.instant()),
            )

        assertThrows<InvalidTaskStateTransitionException> {
            tarefa.iniciar(Clock.fixed(Instant.parse("2026-05-01T12:55:00Z"), ZoneOffset.UTC))
        }.shouldHaveMessage("tarefa excluida nao pode ser alterada")
    }

    @Test
    fun `cancel records status update event`() {
        val tarefa = novaTarefa()
        tarefa.pullDomainEvents()
        val cancelClock = Clock.fixed(Instant.parse("2026-05-01T12:35:00Z"), ZoneOffset.UTC)

        tarefa.cancelar(cancelClock)

        tarefa.status shouldBe TarefaStatus.CANCELADA
        tarefa.pullDomainEvents() shouldContainExactly
            listOf(
                TarefaAtualizada(
                    tarefaId = tarefa.id,
                    changedFields = setOf("status"),
                    occurredAt = cancelClock.instant(),
                ),
            )
    }

    @Test
    fun `restores existing task without emitting events`() {
        val deletedAt = Instant.parse("2026-05-01T12:50:00Z")

        val tarefa =
            Tarefa.reconstituir(
                id = TarefaId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36911")),
                ownerId = ownerId,
                titulo = Titulo.of("Historico"),
                descricao = null,
                categoria = categoria,
                dataHora = DataHoraTarefa.existente(Instant.parse("2026-04-30T13:00:00Z")),
                status = TarefaStatus.CANCELADA,
                criadaEm = Instant.parse("2026-04-01T12:00:00Z"),
                atualizadaEm = deletedAt,
                excluidaEm = deletedAt,
            )

        tarefa.status shouldBe TarefaStatus.CANCELADA
        tarefa.excluidaEm shouldBe deletedAt
        tarefa.pullDomainEvents() shouldBe emptyList()
    }

    @Test
    fun `records multiple events in order`() {
        val tarefa = novaTarefa()
        val startClock = Clock.fixed(Instant.parse("2026-05-01T12:30:00Z"), ZoneOffset.UTC)
        val doneClock = Clock.fixed(Instant.parse("2026-05-01T12:40:00Z"), ZoneOffset.UTC)

        tarefa.iniciar(startClock)
        tarefa.concluir(doneClock)

        tarefa.pullDomainEvents().map { it::class.simpleName } shouldContainExactlyInAnyOrder
            listOf(
                "TarefaCriada",
                "TarefaAtualizada",
                "TarefaConcluida",
            )
    }

    private fun novaTarefa(): Tarefa =
        Tarefa.criar(
            id = TarefaId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36911")),
            ownerId = ownerId,
            titulo = Titulo.of("Lavar roupa"),
            descricao = Descricao.of("Separar roupas claras"),
            categoria = categoria,
            dataHora = DataHoraTarefa.agendadaPara(later, clock),
            clock = clock,
        )
}
