package org.domnikl.gaia

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono

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
        event.appliance.discordNotificationStarted?.let { send(it) }
    }

    @EventListener
    fun handleApplianceStopped(event: ApplianceStoppedEvent) {
        event.appliance.discordNotificationStopped?.let { send(it) }
    }

    private fun send(message: String) {
        client.post()
            .bodyValue(Webhook(message, properties.username))
            .exchangeToMono {
                if (it.statusCode().is2xxSuccessful) {
                    Mono.empty<String>()
                } else {
                    it.createException().flatMap { e -> Mono.error(e) }
                }
            }
            .block()
    }

    data class Webhook(val content: String, val username: String)
}
