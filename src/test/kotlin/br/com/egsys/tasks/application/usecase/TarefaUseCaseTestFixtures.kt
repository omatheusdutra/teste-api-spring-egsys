package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.domain.model.Categoria
import br.com.egsys.tasks.domain.model.CategoriaId
import br.com.egsys.tasks.domain.model.DataHoraTarefa
import br.com.egsys.tasks.domain.model.Descricao
import br.com.egsys.tasks.domain.model.DescricaoCategoria
import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.Titulo
import br.com.egsys.tasks.domain.model.UsuarioId
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID

internal val fixedNow: Instant = Instant.parse("2026-05-01T12:00:00Z")
internal val fixedClock: Clock = Clock.fixed(fixedNow, ZoneOffset.UTC)
internal val taskId: TarefaId = TarefaId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36911"))
internal val categoryId: CategoriaId = CategoriaId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36912"))
internal val otherCategoryId: CategoriaId = CategoriaId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36913"))
internal val ownerId: UsuarioId = UsuarioId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36914"))
internal val scheduledAt: Instant = Instant.parse("2026-05-01T13:00:00Z")

internal fun categoria(
    id: CategoriaId = categoryId,
    descricao: String = "Casa",
): Categoria =
    Categoria(
        id = id,
        descricao = DescricaoCategoria.of(descricao),
    )

internal fun tarefa(
    id: TarefaId = taskId,
    categoria: Categoria = categoria(),
    titulo: String = "Lavar roupa",
    descricao: String? = "Separar roupas claras",
    dataHora: Instant = scheduledAt,
): Tarefa {
    val tarefa =
        Tarefa.criar(
            id = id,
            ownerId = ownerId,
            titulo = Titulo.of(titulo),
            descricao = Descricao.of(descricao),
            categoria = categoria,
            dataHora = DataHoraTarefa.agendadaPara(dataHora, fixedClock),
            clock = fixedClock,
        )
    tarefa.pullDomainEvents()
    return tarefa
}
