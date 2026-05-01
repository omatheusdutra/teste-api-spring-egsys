package br.com.egsys.tasks.infrastructure.persistence.mapper

import br.com.egsys.tasks.domain.model.Categoria
import br.com.egsys.tasks.domain.model.CategoriaId
import br.com.egsys.tasks.domain.model.DescricaoCategoria
import br.com.egsys.tasks.infrastructure.persistence.entity.CategoriaJpaEntity

object CategoriaJpaMapper {
    fun toDomain(entity: CategoriaJpaEntity): Categoria =
        Categoria(
            id = CategoriaId.from(requireJpaField(entity.id, "categoria.id")),
            descricao = DescricaoCategoria.of(entity.descricao),
        )

    fun toEntity(categoria: Categoria): CategoriaJpaEntity =
        CategoriaJpaEntity(
            id = categoria.id.value,
            descricao = categoria.descricao.value,
        )
}
