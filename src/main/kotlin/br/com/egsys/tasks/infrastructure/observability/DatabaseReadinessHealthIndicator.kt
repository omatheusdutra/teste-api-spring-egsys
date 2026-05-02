package br.com.egsys.tasks.infrastructure.observability

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Component

@Component("egsysDatabaseHealthIndicator")
class DatabaseReadinessHealthIndicator(
    private val jdbcTemplate: JdbcTemplate,
) : HealthIndicator {
    override fun health(): Health =
        try {
            val result = jdbcTemplate.queryForObject("SELECT 1", Int::class.java)
            if (result == 1) {
                Health
                    .up()
                    .withDetail("database", "postgresql")
                    .withDetail("validationQuery", "SELECT 1")
                    .build()
            } else {
                Health
                    .down()
                    .withDetail("database", "postgresql")
                    .withDetail("reason", "unexpected validation result")
                    .build()
            }
        } catch (exception: DataAccessException) {
            Health
                .down(exception)
                .withDetail("database", "postgresql")
                .build()
        }
}
