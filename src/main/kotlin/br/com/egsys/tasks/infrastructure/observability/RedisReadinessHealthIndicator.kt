package br.com.egsys.tasks.infrastructure.observability

import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.dao.DataAccessException
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.stereotype.Component

@Component("egsysRedisHealthIndicator")
class RedisReadinessHealthIndicator(
    private val redisConnectionFactory: RedisConnectionFactory,
) : HealthIndicator {
    override fun health(): Health =
        try {
            redisConnectionFactory.getConnection().use { connection ->
                val pong = connection.ping()
                if (pong == "PONG") {
                    Health
                        .up()
                        .withDetail("redis", "reachable")
                        .build()
                } else {
                    Health
                        .down()
                        .withDetail("redis", "unexpected ping response")
                        .build()
                }
            }
        } catch (exception: DataAccessException) {
            Health
                .down(exception)
                .withDetail("redis", "unreachable")
                .build()
        }
}
