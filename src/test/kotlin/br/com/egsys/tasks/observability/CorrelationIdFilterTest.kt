package br.com.egsys.tasks.observability

import br.com.egsys.tasks.infrastructure.observability.CorrelationIdFilter
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldHaveLength
import jakarta.servlet.FilterChain
import org.junit.jupiter.api.Test
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse

class CorrelationIdFilterTest {
    private val filter = CorrelationIdFilter()

    @Test
    fun `usa correlation id recebido e propaga no MDC e response header`() {
        val request =
            MockHttpServletRequest().apply {
                addHeader(CorrelationIdFilter.HEADER_NAME, "trace-018f95df-0c7b")
            }
        val response = MockHttpServletResponse()
        var idInsideChain: String? = null

        filter.doFilter(
            request,
            response,
            FilterChain { _, _ -> idInsideChain = MDC.get(CorrelationIdFilter.MDC_KEY) },
        )

        idInsideChain shouldBe "trace-018f95df-0c7b"
        response.getHeader(CorrelationIdFilter.HEADER_NAME) shouldBe "trace-018f95df-0c7b"
        MDC.get(CorrelationIdFilter.MDC_KEY) shouldBe null
    }

    @Test
    fun `gera correlation id seguro quando header esta ausente ou invalido`() {
        val request =
            MockHttpServletRequest().apply {
                addHeader(CorrelationIdFilter.HEADER_NAME, "../../etc/passwd")
            }
        val response = MockHttpServletResponse()
        var idInsideChain: String? = null

        filter.doFilter(
            request,
            response,
            FilterChain { _, _ -> idInsideChain = MDC.get(CorrelationIdFilter.MDC_KEY) },
        )

        idInsideChain?.shouldHaveLength(36)
        response.getHeader(CorrelationIdFilter.HEADER_NAME) shouldBe idInsideChain
        MDC.get(CorrelationIdFilter.MDC_KEY) shouldBe null
    }

    @Test
    fun `gera correlation id quando header esta ausente`() {
        val request = MockHttpServletRequest()
        val response = MockHttpServletResponse()
        var idInsideChain: String? = null

        filter.doFilter(
            request,
            response,
            FilterChain { _, _ -> idInsideChain = MDC.get(CorrelationIdFilter.MDC_KEY) },
        )

        idInsideChain?.shouldHaveLength(36)
        response.getHeader(CorrelationIdFilter.HEADER_NAME) shouldBe idInsideChain
        MDC.get(CorrelationIdFilter.MDC_KEY) shouldBe null
    }
}
