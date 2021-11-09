package org.domnikl.gaia

import com.typesafe.config.ConfigFactory
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Tag
import io.micrometer.prometheus.PrometheusMeterRegistry
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.micrometer.MicrometerMetricsOptions
import io.vertx.micrometer.VertxPrometheusOptions
import io.vertx.micrometer.backends.BackendRegistries
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.File


fun main(args: Array<String>): Unit = runBlocking {
    require(args.isNotEmpty()) { "Please provide a config file" }

    val config = ConfigFactory.parseFile(File(args[0]))
    val vertx = Vertx.vertx(
        VertxOptions().setMetricsOptions(
            MicrometerMetricsOptions()
                .setPrometheusOptions(VertxPrometheusOptions().setEnabled(true))
                .setEnabled(true)
        ))

    val registry = BackendRegistries.getDefaultNow() as PrometheusMeterRegistry

    vertx.deployVerticle(WebVerticle())

    val factory = Factory(config)
    val logger = factory.logger
    val fritzBox = factory.fritzBox
    val notificationChannel = factory.notificationChannel
    val gauges = mutableMapOf<String, Double>()
    val triggeredStart = mutableMapOf<String, Double>()
    val triggeredEnd = mutableMapOf<String, Double>()

    try {
        val actors = config.getObject("actors").keys.map { key ->
            val ain = config.getString("actors.$key.ain")
            val name = config.getString("actors.$key.name")
            val messageStart = config.getString("actors.$key.messageStart")
            val messageEnd = config.getString("actors.$key.messageEnd")

            val queueSize = config.getInt("actors.$key.queue.size")
            val thresholdStart = config.getDouble("actors.$key.queue.thresholdStart").toFloat()
            val thresholdEnd = config.getDouble("actors.$key.queue.thresholdEnd").toFloat()

            gauges[ain] = 0.0
            triggeredStart[ain] = 0.0
            triggeredEnd[ain] = 0.0

            Gauge
                .builder("gaia_actor_power", gauges[ain]) { gauges[ain]!! }
                .tags(listOf(Tag.of("name", name), Tag.of("ain", ain)))
                .strongReference(true)
                .register(registry)

            Gauge
                .builder("gaia_actor_triggered", triggeredStart[ain]) { triggeredStart[ain]!! }
                .tags(listOf(Tag.of("name", name), Tag.of("ain", ain), Tag.of("event", "start")))
                .strongReference(true)
                .register(registry)

            Gauge
                .builder("gaia_actor_triggered", triggeredEnd[ain]) { triggeredEnd[ain]!! }
                .tags(listOf(Tag.of("name", name), Tag.of("ain", ain), Tag.of("event", "end")))
                .strongReference(true)
                .register(registry)

            Actor(
                ain,
                name,
                listOf(
                    TriggeringQueue(queueSize, TriggeringQueue.Trigger({ f: Float -> f > thresholdStart }) {
                        notificationChannel.sendMessage(messageStart).queue()

                        triggeredStart[ain] = triggeredStart[ain]!! + 1

                        logger.info("Triggered start: $name ($ain)")
                    }),
                    TriggeringQueue(queueSize, TriggeringQueue.Trigger({ f: Float -> f < thresholdEnd }) {
                        notificationChannel.sendMessage(messageEnd).queue()

                        triggeredEnd[ain] = triggeredEnd[ain]!! + 1

                        logger.info("Triggered end: $name ($ain)")
                    })
                )
            )
        }

        while (true) {
            actors.forEach { actor ->
                val power = fritzBox.power(actor.ain).also { actor.add(it) }

                gauges[actor.ain] = power.toDouble()
                logger.info("Value from FritzBox actor '${actor.name}' (${actor.ain}) $power")
            }

            delay(1000)
        }
    } catch (e: Exception) {
        factory.jda.shutdownNow()
        throw e
    }
}
