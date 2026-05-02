package br.com.egsys.tasks.domain.model

import br.com.egsys.tasks.domain.exception.InvalidTaskStateTransitionException
import java.time.Clock
import java.time.Instant

@Suppress("LongParameterList", "TooManyFunctions")
class Tarefa private constructor(
    val id: TarefaId,
    val ownerId: UsuarioId,
    titulo: Titulo,
    descricao: Descricao?,
    categoria: Categoria,
    dataHora: DataHoraTarefa,
    status: TarefaStatus,
    val criadaEm: Instant,
    atualizadaEm: Instant,
    excluidaEm: Instant?,
    initialEvents: List<DomainEvent>,
) {
    var titulo: Titulo = titulo
        private set

    var descricao: Descricao? = descricao
        private set

    var categoria: Categoria = categoria
        private set

    var dataHora: DataHoraTarefa = dataHora
        private set

    var status: TarefaStatus = status
        private set

    var atualizadaEm: Instant = atualizadaEm
        private set

    var excluidaEm: Instant? = excluidaEm
        private set

    private val domainEvents: MutableList<DomainEvent> = initialEvents.toMutableList()

    fun atualizar(
        titulo: Titulo,
        descricao: Descricao?,
        categoria: Categoria,
        dataHora: DataHoraTarefa,
        clock: Clock,
    ) {
        ensureNotDeleted()

        val changedFields = linkedSetOf<String>()

        if (this.titulo != titulo) {
            this.titulo = titulo
            changedFields += "titulo"
        }

        if (this.descricao != descricao) {
            this.descricao = descricao
            changedFields += "descricao"
        }

        if (this.categoria != categoria) {
            this.categoria = categoria
            changedFields += "categoria"
        }

        if (this.dataHora != dataHora) {
            this.dataHora = dataHora
            changedFields += "dataHora"
        }

        recordUpdate(changedFields, clock)
    }

    fun iniciar(clock: Clock) {
        transitionTo(
            target = TarefaStatus.EM_ANDAMENTO,
            allowedSources = setOf(TarefaStatus.PENDENTE),
            clock = clock,
            eventFactory = ::statusUpdated,
        )
    }

    fun concluir(clock: Clock) {
        transitionTo(
            target = TarefaStatus.CONCLUIDA,
            allowedSources = setOf(TarefaStatus.PENDENTE, TarefaStatus.EM_ANDAMENTO),
            clock = clock,
            eventFactory = ::taskDone,
        )
    }

    fun cancelar(clock: Clock) {
        transitionTo(
            target = TarefaStatus.CANCELADA,
            allowedSources = setOf(TarefaStatus.PENDENTE, TarefaStatus.EM_ANDAMENTO),
            clock = clock,
            eventFactory = ::statusUpdated,
        )
    }

    fun excluir(clock: Clock) {
        ensureNotDeleted()

        val occurredAt = clock.instant()
        excluidaEm = occurredAt
        atualizadaEm = occurredAt
        domainEvents += TarefaExcluida(tarefaId = id, occurredAt = occurredAt)
    }

    fun pullDomainEvents(): List<DomainEvent> {
        val events = domainEvents.toList()
        domainEvents.clear()
        return events
    }

    private fun transitionTo(
        target: TarefaStatus,
        allowedSources: Set<TarefaStatus>,
        clock: Clock,
        eventFactory: (Instant) -> DomainEvent,
    ) {
        ensureNotDeleted()

        if (status !in allowedSources) {
            throw InvalidTaskStateTransitionException(
                "nao e permitido mover tarefa de ${status.name} para ${target.name}",
            )
        }

        status = target
        atualizadaEm = clock.instant()
        domainEvents += eventFactory(atualizadaEm)
    }

    private fun recordUpdate(
        changedFields: Set<String>,
        clock: Clock,
    ) {
        if (changedFields.isEmpty()) {
            return
        }

        atualizadaEm = clock.instant()
        domainEvents +=
            TarefaAtualizada(
                tarefaId = id,
                changedFields = changedFields,
                occurredAt = atualizadaEm,
            )
    }

    private fun statusUpdated(occurredAt: Instant): DomainEvent =
        TarefaAtualizada(
            tarefaId = id,
            changedFields = setOf("status"),
            occurredAt = occurredAt,
        )

    private fun taskDone(occurredAt: Instant): DomainEvent = TarefaConcluida(tarefaId = id, occurredAt = occurredAt)

    private fun ensureNotDeleted() {
        if (excluidaEm != null) {
            throw InvalidTaskStateTransitionException("tarefa excluida nao pode ser alterada")
        }
    }

    companion object {
        fun criar(
            id: TarefaId,
            ownerId: UsuarioId,
            titulo: Titulo,
            descricao: Descricao?,
            categoria: Categoria,
            dataHora: DataHoraTarefa,
            clock: Clock,
        ): Tarefa {
            val now = clock.instant()

            return Tarefa(
                id = id,
                ownerId = ownerId,
                titulo = titulo,
                descricao = descricao,
                categoria = categoria,
                dataHora = dataHora,
                status = TarefaStatus.PENDENTE,
                criadaEm = now,
                atualizadaEm = now,
                excluidaEm = null,
                initialEvents = listOf(TarefaCriada(tarefaId = id, occurredAt = now)),
            )
        }

        fun reconstituir(
            id: TarefaId,
            ownerId: UsuarioId,
            titulo: Titulo,
            descricao: Descricao?,
            categoria: Categoria,
            dataHora: DataHoraTarefa,
            status: TarefaStatus,
            criadaEm: Instant,
            atualizadaEm: Instant,
            excluidaEm: Instant?,
        ): Tarefa =
            Tarefa(
                id = id,
                ownerId = ownerId,
                titulo = titulo,
                descricao = descricao,
                categoria = categoria,
                dataHora = dataHora,
                status = status,
                criadaEm = criadaEm,
                atualizadaEm = atualizadaEm,
                excluidaEm = excluidaEm,
                initialEvents = emptyList(),
            )
    }
}
