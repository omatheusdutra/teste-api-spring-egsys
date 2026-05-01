package br.com.egsys.tasks.web.mapper

import br.com.egsys.tasks.domain.model.Tarefa
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.util.Base64
import java.util.UUID

data class TarefaCursor(
    val dataHora: Instant,
    val id: UUID,
)

class InvalidCursorException(
    cursor: String,
) : IllegalArgumentException("cursor invalido: $cursor")

object CursorCodec {
    private const val SEPARATOR = "|"

    fun encode(tarefa: Tarefa): String =
        Base64
            .getUrlEncoder()
            .withoutPadding()
            .encodeToString("${tarefa.dataHora.value}$SEPARATOR${tarefa.id.value}".toByteArray(StandardCharsets.UTF_8))

    fun decode(cursor: String): TarefaCursor =
        runCatching {
            val decoded =
                String(
                    Base64.getUrlDecoder().decode(cursor),
                    StandardCharsets.UTF_8,
                )
            val parts = decoded.split(SEPARATOR)
            check(parts.size == 2)
            TarefaCursor(
                dataHora = Instant.parse(parts[0]),
                id = UUID.fromString(parts[1]),
            )
        }.getOrElse {
            throw InvalidCursorException(cursor)
        }
}
