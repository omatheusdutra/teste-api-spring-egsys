package br.com.egsys.tasks.domain.model

import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.util.UUID

class CategoriaTest {
    @Test
    fun `creates category with id and description`() {
        val id = CategoriaId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36912"))
        val descricao = DescricaoCategoria.of("Trabalho")

        val categoria = Categoria(id = id, descricao = descricao)

        categoria.id shouldBe id
        categoria.descricao shouldBe descricao
    }
}
