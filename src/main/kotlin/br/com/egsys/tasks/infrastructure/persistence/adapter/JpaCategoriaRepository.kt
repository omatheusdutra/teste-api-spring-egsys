package br.com.egsys.tasks.infrastructure.persistence.adapter

import br.com.egsys.tasks.domain.model.Categoria
import br.com.egsys.tasks.domain.model.CategoriaId
import br.com.egsys.tasks.domain.port.CategoriaRepository
import br.com.egsys.tasks.infrastructure.persistence.mapper.CategoriaJpaMapper
import br.com.egsys.tasks.infrastructure.persistence.repository.SpringDataCategoriaRepository
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
@Transactional(readOnly = true)
class JpaCategoriaRepository(
    private val repository: SpringDataCategoriaRepository,
) : CategoriaRepository {
    override fun findAll(): List<Categoria> =
        repository
            .findAllByOrderByDescricaoAsc()
            .map(CategoriaJpaMapper::toDomain)

    override fun findById(id: CategoriaId): Categoria? =
        repository
            .findById(id.value)
            .map(CategoriaJpaMapper::toDomain)
            .orElse(null)
}
