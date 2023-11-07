package org.domnikl.gaia

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient

@ConfigurationProperties(prefix = "gaia.discord")
@EnableConfigurationProperties
data class DiscordProperties(
    val webhookUrl: String,
    val username: String
)

@Component
class DiscordNotifier(private val properties: DiscordProperties, builder: WebClient.Builder) {
    private val client = builder.baseUrl(properties.webhookUrl).build()

    @EventListener
    fun handleApplianceStarted(event: ApplianceStartedEvent) {
        event.appliance.notificationStarted?.let { send(it) }
    }

    @EventListener
    fun handleApplianceStopped(event: ApplianceStoppedEvent) {
        event.appliance.notificationEnded?.let { send(it) }
    }

    private fun send(message: String) {
        client.post()
            .bodyValue(Webhook(message, properties.username))
            .retrieve()
            .toBodilessEntity()
            .block() ?: throw IllegalStateException("Failed to send message via Webhook")
    }

    data class Webhook(val content: String, val username: String)
}
