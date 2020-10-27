package org.domnikl.gaia

import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

class DiscordMessageListener : ListenerAdapter() {
    private val commands = mapOf(
        "!ping" to ::ping,
        "!help" to ::help
    )

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot) {
            return
        }

        commands[event.message.contentDisplay]?.invoke(event)
    }

    private fun ping(event: MessageReceivedEvent) {
        event.channel.sendMessage("pong!").queue()
    }

    private fun help(event: MessageReceivedEvent) {
        event.channel.sendMessage("This is Gaia, your bot to check the washing machine and dish washer. Commands: ${commands.keys.joinToString(", ")}").queue()
    }
}
