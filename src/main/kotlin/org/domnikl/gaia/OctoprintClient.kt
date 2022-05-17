package org.domnikl.gaia

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.typesafe.config.Config
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Tag
import io.micrometer.prometheus.PrometheusMeterRegistry
import net.dv8tion.jda.api.entities.TextChannel
import okhttp3.*
import org.slf4j.LoggerFactory

class OctoprintActor(
    override val id: String,
    private val client: OctoprintClient,
    private val triggeringQueues: List<TriggeringQueue>,
    observer: Observer,
    registry: PrometheusMeterRegistry,
    notificationChannel: TextChannel,
    messageStart: String,
    messageEnd: String
) : Actor {
    private val logger = LoggerFactory.getLogger(OctoprintActor::class.java.canonicalName)
    private var gauge = 0.0
    private var triggeredStart = 0.0
    private var triggeredEnd = 0.0

    init {
        Gauge
            .builder("gaia_actor_temperature", gauge) { gauge }
            .tags(listOf(Tag.of("name", "octoprint"), Tag.of("id", id)))
            .strongReference(true)
            .register(registry)

        Gauge
            .builder("gaia_actor_triggered", triggeredStart) { triggeredStart }
            .tags(listOf(Tag.of("name", "octoprint"), Tag.of("id", id), Tag.of("event", "start")))
            .strongReference(true)
            .register(registry)

        Gauge
            .builder("gaia_actor_triggered", triggeredEnd) { triggeredEnd }
            .tags(listOf(Tag.of("name", "octoprint"), Tag.of("id", id), Tag.of("event", "end")))
            .strongReference(true)
            .register(registry)

        observer.subscribe {
            when (it) {
                Event.START -> {
                    notificationChannel.sendMessage(messageStart).queue()
                    triggeredStart += 1
                    logger.info("Triggered start: \"octoprint\" ($id)")
                }
                Event.END -> {
                    notificationChannel.sendMessage(messageEnd).queue()
                    triggeredEnd += 1

                    logger.info("Triggered end: \"octoprint\" ($id)")
                }
            }
        }
    }

    companion object {
        fun fromConfig(
            key: String,
            client: OctoprintClient,
            registry: PrometheusMeterRegistry,
            notificationChannel: TextChannel,
            config: Config
        ): OctoprintActor {
            val messageStart = config.getString("actors.$key.messageStart")
            val messageEnd = config.getString("actors.$key.messageEnd")

            val queueSize = config.getInt("actors.$key.queue.size")
            val thresholdStart = config.getDouble("actors.$key.queue.thresholdStart").toFloat()
            val thresholdEnd = config.getDouble("actors.$key.queue.thresholdEnd").toFloat()
            val observer = Observer()

            return OctoprintActor(
                key,
                client,
                listOf(
                    TriggeringQueue(queueSize, TriggeringQueue.Trigger({ f: Float -> f > thresholdStart }) {
                        observer.notify(Event.START)
                    }),
                    TriggeringQueue(queueSize, TriggeringQueue.Trigger({ f: Float -> f < thresholdEnd }) {
                        observer.notify(Event.END)
                    })
                ),
                observer,
                registry,
                notificationChannel,
                messageStart,
                messageEnd
            )
        }
    }

    override suspend fun run(): Double {
        val temperature = client.getPrinterInfo()["tool0"]!!.actual.also { measurement ->
            triggeringQueues.forEach { it.add(measurement.toFloat()) }
        }

        gauge = temperature
        logger.info("Value from OctoPrint actor $id: $temperature°C")

        return temperature
    }

    class OctoprintClient(private val accessToken: String, private val client: OkHttpClient) {
        private val objectMapper = ObjectMapper()
            .registerKotlinModule()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        fun getPrinterInfo(): Map<String, Temperature> {
            val request = Request.Builder()
                .header("Authorization", "Bearer $accessToken")
                .url("http://unifi-controller:5000/api/printer")
                .build()

            val body = client.newCall(request).execute().body?.string()
            val response = objectMapper.readValue(body, Response::class.java)

            return response.temperature
        }

        data class Response (
            val temperature: Map<String, Temperature>
        )

        data class Temperature (
            val actual: Double,
            val offset: Double,
            val target: Double
        )
    }
}
