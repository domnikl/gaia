package org.domnikl.gaia

import com.typesafe.config.ConfigFactory
import io.vertx.core.Vertx
import io.vertx.core.VertxOptions
import io.vertx.micrometer.MicrometerMetricsOptions
import io.vertx.micrometer.VertxPrometheusOptions
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

    vertx.deployVerticle(WebVerticle())

    val factory = Factory(config)

    try {
        val actors = config.getObject("actors").keys.map { key ->
            factory.createActor(key)
        }

        while (true) {
            actors.forEach { actor ->
                actor.run()
            }

            delay(1000)
        }
    } catch (e: Exception) {
        factory.jda.shutdownNow()
        throw e
    }
}
