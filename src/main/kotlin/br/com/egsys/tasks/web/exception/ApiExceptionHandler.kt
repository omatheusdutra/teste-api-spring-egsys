package br.com.egsys.tasks.web.exception

import br.com.egsys.tasks.application.exception.CategoriaNaoEncontradaException
import br.com.egsys.tasks.application.exception.TarefaNaoEncontradaException
import br.com.egsys.tasks.domain.exception.DomainException
import br.com.egsys.tasks.web.mapper.InvalidCursorException
import jakarta.validation.ConstraintViolationException
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.validation.FieldError
import org.springframework.web.HttpMediaTypeNotSupportedException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.context.request.WebRequest
import org.springframework.web.method.annotation.HandlerMethodValidationException
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler

@RestControllerAdvice
class ApiExceptionHandler : ResponseEntityExceptionHandler() {
    override fun handleMethodArgumentNotValid(
        ex: MethodArgumentNotValidException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val problem = problem(HttpStatus.BAD_REQUEST, "Requisicao invalida", "Campos invalidos no corpo da requisicao")
        problem.setProperty(
            "violations",
            ex.bindingResult.fieldErrors.map { it.toViolation() },
        )

        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request)
    }

    override fun handleHttpMessageNotReadable(
        ex: HttpMessageNotReadableException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val problem = problem(HttpStatus.BAD_REQUEST, "JSON invalido", "Corpo da requisicao ausente ou malformado")
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request)
    }

    override fun handleHttpMediaTypeNotSupported(
        ex: HttpMediaTypeNotSupportedException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val problem = problem(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Content-Type nao suportado", "Use application/json")
        return handleExceptionInternal(ex, problem, headers, HttpStatus.UNSUPPORTED_MEDIA_TYPE, request)
    }

    override fun handleHandlerMethodValidationException(
        ex: HandlerMethodValidationException,
        headers: HttpHeaders,
        status: HttpStatusCode,
        request: WebRequest,
    ): ResponseEntity<Any>? {
        val problem = problem(HttpStatus.BAD_REQUEST, "Parametro invalido", "Parametros de requisicao invalidos")
        problem.setProperty(
            "violations",
            ex.parameterValidationResults
                .flatMap { result ->
                    result.resolvableErrors.map {
                        Violation(
                            field = result.methodParameter.parameterName ?: "parametro",
                            message = it.defaultMessage ?: "valor invalido",
                        )
                    }
                },
        )
        return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request)
    }

    @ExceptionHandler(CategoriaNaoEncontradaException::class, TarefaNaoEncontradaException::class)
    fun handleNotFound(ex: RuntimeException): ProblemDetail =
        problem(HttpStatus.NOT_FOUND, "Recurso nao encontrado", requireNotNull(ex.message))

    @ExceptionHandler(DomainException::class, InvalidCursorException::class)
    fun handleBadRequest(ex: RuntimeException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "Requisicao invalida", requireNotNull(ex.message))

    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(ex: MethodArgumentTypeMismatchException): ProblemDetail =
        problem(HttpStatus.BAD_REQUEST, "Parametro invalido", "Parametro ${ex.name} invalido")

    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(ex: ConstraintViolationException): ProblemDetail {
        val problem = problem(HttpStatus.BAD_REQUEST, "Parametro invalido", "Parametros de requisicao invalidos")
        problem.setProperty(
            "violations",
            ex.constraintViolations.map {
                Violation(
                    field = it.propertyPath.toString().substringAfterLast("."),
                    message = it.message,
                )
            },
        )
        return problem
    }

    private fun problem(
        status: HttpStatus,
        title: String,
        detail: String,
    ): ProblemDetail =
        ProblemDetail.forStatusAndDetail(status, detail).apply {
            this.title = title
        }

    private fun FieldError.toViolation(): Violation =
        Violation(
            field = field,
            message = defaultMessage ?: "valor invalido",
        )
}

data class Violation(
    val field: String,
    val message: String,
)
