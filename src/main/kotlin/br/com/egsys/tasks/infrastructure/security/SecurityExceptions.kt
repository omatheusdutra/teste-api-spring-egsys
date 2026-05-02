package br.com.egsys.tasks.infrastructure.security

class InvalidCredentialsException : RuntimeException("Credenciais invalidas")

class WeakPasswordException(
    message: String,
) : RuntimeException(message)

class EmailAlreadyRegisteredException : RuntimeException("email ja cadastrado")

class InvalidTokenException(
    message: String = "token invalido",
) : RuntimeException(message)

class RateLimitExceededException(
    val retryAfterSeconds: Long,
) : RuntimeException("limite de requisicoes excedido")
