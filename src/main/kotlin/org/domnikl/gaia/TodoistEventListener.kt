package org.domnikl.gaia

import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.event.EventListener
import org.springframework.http.HttpStatus
import org.springframework.http.HttpStatusCode
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.bodyToMono
import org.springframework.web.reactive.function.client.createExceptionAndAwait
import reactor.core.publisher.Mono

@ConfigurationProperties(prefix = "gaia.todoist")
@EnableConfigurationProperties
data class TodoistProperties(
    val webhookUrl: String,
    val accessToken: String,
    val projectId: String
)

@Component
class TodoistNotifier(private val properties: TodoistProperties, builder: WebClient.Builder) {
    private val client = builder.baseUrl(properties.webhookUrl).build()

    @EventListener
    fun handleApplianceStarted(event: ApplianceStartedEvent) {
        event.appliance.todoistTaskStarted?.let { send(it) }
    }

    @EventListener
    fun handleApplianceStopped(event: ApplianceStoppedEvent) {
        event.appliance.todoistTaskStopped?.let { send(it) }
    }

    private fun send(message: String) {
        client.post()
            .headers {
                it.add("Authorization", "Bearer ${properties.accessToken}")
            }
            .bodyValue(Task(message, "today", "en", properties.projectId))
            .exchangeToMono {
                if (it.statusCode().is2xxSuccessful) {
                    Mono.empty<String>()
                } else {
                    it.createException().flatMap { e -> Mono.error(e) }
                }
            }
            .block()
    }

    data class Task(
        val content: String,
        @JsonProperty("due_string")
        val dueString: String,
        @JsonProperty("due_lang")
        val dueLang: String,
        @JsonProperty("project_id")
        val projectId: String
    )
}
