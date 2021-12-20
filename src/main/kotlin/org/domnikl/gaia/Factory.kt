package org.domnikl.gaia

import com.typesafe.config.Config
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.TextChannel
import okhttp3.OkHttpClient
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.net.URL

class Factory(config: Config) {
    val logger: Logger = LoggerFactory.getLogger("Gaia")

    private val httpClient = OkHttpClient()

    val fritzBox by lazy {
        FritzBox(
            config.getString("fritzBox.password"),
            config.getString("fritzBox.user"),
            URL(config.getString("fritzBox.url")),
            httpClient
        )
    }

    val jda by lazy {
        JDABuilder.createDefault(config.getString("discord.token"))
            .build()
            .also { it.addEventListener(DiscordMessageListener()) }
            .awaitReady()
    }

    val notificationChannel: TextChannel by lazy {
        jda.getTextChannelsByName(config.getString("discord.channel"), true).first()
    }
}
