package br.com.egsys.tasks.infrastructure.persistence

import br.com.egsys.tasks.domain.port.CategoriaRepository
import br.com.egsys.tasks.support.PersistenceIntegrationTest
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

@Tag("postgres")
class CategoriaJpaRepositoryIntegrationTest : PersistenceIntegrationTest() {
    @Autowired
    private lateinit var categorias: CategoriaRepository

    @Test
    fun `lists seeded categories ordered by description`() {
        val descricoes = categorias.findAll().map { it.descricao.value }

        descricoes shouldBe descricoes.sorted()
        descricoes shouldContainAll listOf("Casa", "Trabalho")
    }

    @Test
    fun `finds category by id`() {
        val casa = categorias.findAll().first { it.descricao.value == "Casa" }

        categorias.findById(casa.id) shouldBe casa
    }
}
