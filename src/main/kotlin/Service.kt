import com.typesafe.config.ConfigFactory
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.domnikl.gaia.Factory
import org.domnikl.gaia.Queue
import java.io.File

data class Actor(
    val ain: String,
    val name: String,
    val queue: Queue
)

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
            val message = config.getString("actors.$key.message")

            val queueSize = config.getInt("actors.$key.queue.size")
            val queueThreshold = config.getDouble("actors.$key.queue.threshold").toFloat()

            Actor(
                ain,
                name,
                Queue(queueSize, queueThreshold) {
                    notificationChannel.sendMessage(message).queue()
                    logger.info("Triggered: $name")
                }
            )
        }

        while (true) {
            actors.forEach { actor ->
                val power = fritzBox.power(actor.ain).also { actor.queue.add(it) }
                logger.info("Value from FritzBox actor '${actor.name}' (${actor.ain}) $power")
            }

            delay(1000)
        }
    } catch (e: Exception) {
        factory.jda.shutdownNow()
        throw e
    }
}
