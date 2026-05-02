package br.com.egsys.tasks.web.controller

import br.com.egsys.tasks.infrastructure.security.JwtService
import br.com.egsys.tasks.infrastructure.security.RateLimiterService
import br.com.egsys.tasks.infrastructure.security.SecurityConfig
import com.ninjasquad.springmockk.MockkBean
import org.hamcrest.Matchers.containsString
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@WebMvcTest(controllers = [HomeController::class])
@Import(SecurityConfig::class)
@Suppress("UnusedPrivateProperty")
class HomeControllerTest {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @MockkBean
    private lateinit var jwtService: JwtService

    @MockkBean
    private lateinit var rateLimiter: RateLimiterService

    @Test
    fun `home publica apresenta entrada amigavel da API`() {
        mockMvc
            .perform(get("/"))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
            .andExpect(content().string(containsString("EGSYS Tasks API")))
            .andExpect(content().string(containsString("/swagger-ui/index.html")))
            .andExpect(content().string(containsString("JWT RS256")))
            .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
    }
}
