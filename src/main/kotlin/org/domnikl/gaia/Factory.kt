package org.domnikl.gaia

import com.typesafe.config.Config
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.TextChannel
import okhttp3.OkHttpClient
import org.apache.logging.log4j.kotlin.logger
import java.net.URL

class Factory(config: Config) {
    val logger = logger("Gaia")

    private val httpClient = OkHttpClient()

    val fritzBox by lazy {
        FritzBox(
            config.getString("fritzBox.password"),
            config.getString("fritzBox.user"),
            URL(config.getString("fritzBox.url")),
            httpClient
        )
    }

    private val jda by lazy {
        JDABuilder.createDefault(config.getString("discord.token"))
            .build()
            .also { it.addEventListener(DiscordMessageListener()) }
            .awaitReady()
    }

    val notificationChannel: TextChannel by lazy {
        jda.getTextChannelsByName(config.getString("discord.channel"), true).first()
    }
}
