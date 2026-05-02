package br.com.egsys.tasks.web.mapper

import br.com.egsys.tasks.domain.model.Categoria
import br.com.egsys.tasks.domain.model.CategoriaId
import br.com.egsys.tasks.domain.model.DataHoraTarefa
import br.com.egsys.tasks.domain.model.Descricao
import br.com.egsys.tasks.domain.model.DescricaoCategoria
import br.com.egsys.tasks.domain.model.Tarefa
import br.com.egsys.tasks.domain.model.TarefaId
import br.com.egsys.tasks.domain.model.TarefaStatus
import br.com.egsys.tasks.domain.model.Titulo
import br.com.egsys.tasks.domain.model.UsuarioId
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Base64
import java.util.UUID

class CursorCodecTest {
    @Test
    fun `encodes and decodes task cursor`() {
        val decoded = CursorCodec.decode(CursorCodec.encode(tarefa))

        decoded shouldBe TarefaCursor(futureDate, taskId)
    }

    @Test
    fun `rejects non base64 cursor`() {
        shouldThrow<InvalidCursorException> {
            CursorCodec.decode("not-a-cursor")
        }
    }

    @Test
    fun `rejects cursor with invalid payload shape`() {
        val cursor = Base64.getUrlEncoder().withoutPadding().encodeToString("only-one-part".toByteArray())

        shouldThrow<InvalidCursorException> {
            CursorCodec.decode(cursor)
        }
    }

    private companion object {
        val taskId: UUID = UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36911")
        val futureDate: Instant = Instant.parse("2030-05-01T13:00:00Z")
        val tarefa: Tarefa =
            Tarefa.reconstituir(
                id = TarefaId.from(taskId),
                ownerId = UsuarioId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36914")),
                titulo = Titulo.of("Pagar aluguel"),
                descricao = Descricao.of("Vencimento do contrato residencial"),
                categoria =
                    Categoria(
                        id = CategoriaId.from(UUID.fromString("018f95df-0c7b-7af2-a199-447f82f36912")),
                        descricao = DescricaoCategoria.of("Casa"),
                    ),
                dataHora = DataHoraTarefa.existente(futureDate),
                status = TarefaStatus.PENDENTE,
                criadaEm = Instant.parse("2026-05-01T12:00:00Z"),
                atualizadaEm = Instant.parse("2026-05-01T12:00:00Z"),
                excluidaEm = null,
            )
    }
}
