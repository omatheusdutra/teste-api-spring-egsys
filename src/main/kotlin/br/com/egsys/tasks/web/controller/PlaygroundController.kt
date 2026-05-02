package br.com.egsys.tasks.web.controller

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.context.annotation.Profile
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
@Profile("dev")
@RequestMapping("/playground")
@Hidden
class PlaygroundController {
    @GetMapping(produces = [MediaType.TEXT_HTML_VALUE])
    @ResponseBody
    @PreAuthorize("permitAll()")
    fun page(): ResponseEntity<ByteArray> {
        val bytes = ClassPathResource(RESOURCE_PATH).inputStream.use { it.readAllBytes() }
        return ResponseEntity
            .ok()
            .contentType(MediaType.valueOf("text/html;charset=UTF-8"))
            .header("X-Robots-Tag", "noindex")
            .body(bytes)
    }

    private companion object {
        const val RESOURCE_PATH = "playground/playground.html"
    }
}
