import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.domnikl.gaia.Factory
import org.domnikl.gaia.TriggeringQueue
import java.io.File

data class Actor(
    val ain: String,
    val name: String,
    val triggeringQueues: List<TriggeringQueue>
) {
    suspend fun add(measurement: Float) {
        triggeringQueues.forEach { it.add(measurement) }
    }
}

fun main(args: Array<String>) = runBlocking {
    require(args.isNotEmpty()) { "Please provide a config file" }

    val config = ConfigFactory.parseFile(File(args[0]))
    val factory = Factory(config)
    val logger = factory.logger
    val fritzBox = factory.fritzBox
    val notificationChannel = factory.notificationChannel

    try {
        val actors = config.getObject("actors").keys.map { key ->
            val ain = config.getString("actors.$key.ain")
            val name = config.getString("actors.$key.name")
            val messageStart = config.getString("actors.$key.messageStart")
            val messageEnd = config.getString("actors.$key.messageEnd")

            val queueSize = config.getInt("actors.$key.queue.size")
            val thresholdStart = config.getDouble("actors.$key.queue.thresholdStart").toFloat()
            val thresholdEnd = config.getDouble("actors.$key.queue.thresholdEnd").toFloat()

            Actor(
                ain,
                name,
                listOf(
                    TriggeringQueue(queueSize, TriggeringQueue.Trigger({ f: Float -> f > thresholdStart }) {
                        notificationChannel.sendMessage(messageStart).queue()
                        logger.info("Triggered start: $name ($ain)")
                    }),
                    TriggeringQueue(queueSize, TriggeringQueue.Trigger({ f: Float -> f < thresholdEnd }) {
                        notificationChannel.sendMessage(messageEnd).queue()
                        logger.info("Triggered end: $name ($ain)")
                    })
                )
            )
        }

        while (true) {
            actors.forEach { actor ->
                val power = fritzBox.power(actor.ain).also { actor.add(it) }
                logger.info("Value from FritzBox actor '${actor.name}' (${actor.ain}) $power")
            }

            delay(1000)
        }
    } catch (e: Exception) {
        factory.jda.shutdownNow()
        throw e
    }
}
