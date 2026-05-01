package br.com.egsys.tasks.application.usecase

import br.com.egsys.tasks.domain.model.Categoria
import br.com.egsys.tasks.domain.model.CategoriaId
import br.com.egsys.tasks.domain.model.DescricaoCategoria
import br.com.egsys.tasks.domain.port.CategoriaRepository
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.UUID

class ListarCategoriasUseCaseTest {
    private val repository = mockk<CategoriaRepository>()
    private val useCase = ListarCategoriasUseCase(repository)

    @Test
    fun `lists categories from repository`() {
        val categorias =
            listOf(
                categoria("018f95df-0c7b-7af2-a199-447f82f36912", "Casa"),
                categoria("018f95df-0c7b-7af2-a199-447f82f36913", "Trabalho"),
            )
        every { repository.findAll() } returns categorias

        useCase.execute() shouldBe categorias

        verify(exactly = 1) { repository.findAll() }
    }

    private fun categoria(
        id: String,
        descricao: String,
    ): Categoria =
        Categoria(
            id = CategoriaId.from(UUID.fromString(id)),
            descricao = DescricaoCategoria.of(descricao),
        )
}
