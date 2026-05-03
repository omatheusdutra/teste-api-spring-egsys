package br.com.egsys.tasks.web.controller

import io.swagger.v3.oas.annotations.Hidden
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.core.io.ClassPathResource
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody

@Controller
@RequestMapping("/playground")
@ConditionalOnProperty(prefix = "egsys.playground", name = ["enabled"], havingValue = "true")
@EnableConfigurationProperties(PlaygroundProperties::class)
@Hidden
class PlaygroundController(
    private val properties: PlaygroundProperties,
) {
    @GetMapping
    @ResponseBody
    @PreAuthorize("permitAll()")
    fun page(): ResponseEntity<ByteArray> = serve("playground.html", "text/html;charset=UTF-8")

    @GetMapping("/config")
    @ResponseBody
    @PreAuthorize("permitAll()")
    fun config(): PlaygroundConfigResponse =
        PlaygroundConfigResponse(
            attackDemosEnabled = properties.attackDemosEnabled,
        )

    @GetMapping("/assets/{name:[a-z0-9.-]{1,64}}")
    @ResponseBody
    @PreAuthorize("permitAll()")
    fun asset(
        @PathVariable name: String,
    ): ResponseEntity<ByteArray> {
        val contentType = contentTypeFor(name) ?: return ResponseEntity.notFound().build()
        val resource = ClassPathResource("playground/$name")
        return if (!resource.exists()) {
            ResponseEntity.notFound().build()
        } else {
            serveBytes(resource.inputStream.use { it.readAllBytes() }, contentType)
        }
    }

    private fun contentTypeFor(name: String): String? =
        when {
            name.endsWith(".js") -> "application/javascript;charset=UTF-8"
            name.endsWith(".css") -> "text/css;charset=UTF-8"
            name.endsWith(".svg") -> "image/svg+xml"
            else -> null
        }

    private fun serve(
        name: String,
        contentType: String,
    ): ResponseEntity<ByteArray> {
        val bytes = ClassPathResource("playground/$name").inputStream.use { it.readAllBytes() }
        return serveBytes(bytes, contentType)
    }

    private fun serveBytes(
        bytes: ByteArray,
        contentType: String,
    ): ResponseEntity<ByteArray> =
        ResponseEntity
            .ok()
            .contentType(MediaType.valueOf(contentType))
            .header("X-Robots-Tag", "noindex")
            .body(bytes)
}

data class PlaygroundConfigResponse(
    val attackDemosEnabled: Boolean,
)
