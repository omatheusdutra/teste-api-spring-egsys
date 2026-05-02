package br.com.egsys.tasks.infrastructure.security

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

@Service
class RateLimiterService(
    private val redis: StringRedisTemplate,
) {
    fun consume(
        key: String,
        limit: Long,
        window: Duration,
    ) {
        val redisKey = "rate:$key"
        val attempts = redis.opsForValue().increment(redisKey) ?: 1L
        if (attempts == 1L) {
            redis.expire(redisKey, window)
        }
        if (attempts > limit) {
            throw RateLimitExceededException(window.seconds)
        }
    }
}
