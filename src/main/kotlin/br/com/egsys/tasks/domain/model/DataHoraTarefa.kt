package br.com.egsys.tasks.domain.model

import br.com.egsys.tasks.domain.exception.InvalidDomainValueException
import java.time.Clock
import java.time.Instant

@JvmInline
value class DataHoraTarefa private constructor(
    val value: Instant,
) {
    companion object {
        fun agendadaPara(
            value: Instant,
            clock: Clock,
        ): DataHoraTarefa {
            if (value.isBefore(clock.instant())) {
                throw InvalidDomainValueException("dataHora nao pode estar no passado")
            }

            return DataHoraTarefa(value)
        }

        fun existente(value: Instant): DataHoraTarefa = DataHoraTarefa(value)
    }
}
