package org.domnikl.gaia

import com.typesafe.config.Config
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.vertx.micrometer.backends.BackendRegistries
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.TextChannel
import okhttp3.OkHttpClient
import org.domnikl.gaia.FritzboxActor.Companion.fromConfig
import java.net.URL

class Factory(val config: Config) {
    private val httpClient = OkHttpClient()

    private val fritzBox by lazy {
        FritzboxActor.FritzBox(
            config.getString("fritzBox.password"),
            config.getString("fritzBox.user"),
            URL(config.getString("fritzBox.url")),
            httpClient
        )
    }

    val octoprintClient by lazy {
        OctoprintClient(config.getString("octoprint.accessToken"), httpClient)
    }

    val jda by lazy {
        JDABuilder.createDefault(config.getString("discord.token"))
            .build()
            .also { it.addEventListener(DiscordMessageListener()) }
            .awaitReady()
    }

    private val notificationChannel: TextChannel by lazy {
        jda.getTextChannelsByName(config.getString("discord.channel"), true).first()
    }

    private val todoistClient: TodoistClient by lazy {
        TodoistClient(config.getString("todoist.accessToken"), config.getString("todoist.projectId"), httpClient)
    }

    private val registry by lazy {
        BackendRegistries.getDefaultNow() as PrometheusMeterRegistry
    }

    fun createActor(id: String): Actor {
        return when (val type = config.getString("actors.$id.type")) {
            "fritzbox" -> fromConfig(id, fritzBox, registry, todoistClient, notificationChannel, config)
            else -> throw IllegalArgumentException("Unknown type: $type")
        }
    }
}
