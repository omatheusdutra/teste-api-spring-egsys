package br.com.egsys.tasks.infrastructure.persistence.mapper

import br.com.egsys.tasks.domain.model.DataHoraTarefa
import br.com.egsys.tasks.domain.model.Descricao
import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.Titulo
import br.com.egsys.tasks.domain.model.UsuarioId
import br.com.egsys.tasks.infrastructure.persistence.entity.CategoriaJpaEntity
import br.com.egsys.tasks.infrastructure.persistence.entity.TarefaJpaEntity

object TarefaJpaMapper {
    fun toDomain(entity: TarefaJpaEntity): Tarefa =
        Tarefa.reconstituir(
            id = TarefaId.from(requireJpaField(entity.id, "tarefa.id")),
            ownerId = UsuarioId.from(requireJpaField(entity.ownerId, "tarefa.ownerId")),
            titulo = Titulo.of(entity.titulo),
            descricao = Descricao.of(entity.descricao),
            categoria = CategoriaJpaMapper.toDomain(requireJpaField(entity.categoria, "tarefa.categoria")),
            dataHora = DataHoraTarefa.existente(requireJpaField(entity.dataHora, "tarefa.dataHora")),
            status = entity.status,
            criadaEm = requireJpaField(entity.criadaEm, "tarefa.criadaEm"),
            atualizadaEm = requireJpaField(entity.atualizadaEm, "tarefa.atualizadaEm"),
            excluidaEm = entity.excluidaEm,
        )

    fun toEntity(
        tarefa: Tarefa,
        categoria: CategoriaJpaEntity,
    ): TarefaJpaEntity =
        TarefaJpaEntity(
            id = tarefa.id.value,
            ownerId = tarefa.ownerId.value,
            titulo = tarefa.titulo.value,
            descricao = tarefa.descricao?.value,
            categoria = categoria,
            dataHora = tarefa.dataHora.value,
            status = tarefa.status,
            criadaEm = tarefa.criadaEm,
            atualizadaEm = tarefa.atualizadaEm,
            excluidaEm = tarefa.excluidaEm,
        )
}
