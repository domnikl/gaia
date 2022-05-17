package org.domnikl.gaia

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import com.sun.jdi.request.EventRequestManager
import com.typesafe.config.Config
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.Tag
import io.micrometer.prometheus.PrometheusMeterRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.dv8tion.jda.api.entities.TextChannel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.logging.Logger
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

class FritzboxActor(
    override val id: String,
    private val name: String,
    private val fritzBox: FritzBox,
    registry: PrometheusMeterRegistry,
    private val triggeringQueues: List<TriggeringQueue>,
    observer: Observer,
    notificationChannel: TextChannel,
    messageStart: String,
    messageEnd: String,
    taskMessage: String,
    todoistClient: TodoistClient
) : Actor {
    private val logger = Logger.getLogger(FritzboxActor::class.java.canonicalName)
    private var gauge = 0.0
    private var triggeredStart = 0.0
    private var triggeredEnd = 0.0

    init {
        Gauge
            .builder("gaia_actor_power", gauge) { gauge }
            .tags(listOf(Tag.of("name", name), Tag.of("ain", id)))
            .strongReference(true)
            .register(registry)

        Gauge
            .builder("gaia_actor_triggered", triggeredStart) { triggeredStart }
            .tags(listOf(Tag.of("name", name), Tag.of("ain", id), Tag.of("event", "start")))
            .strongReference(true)
            .register(registry)

        Gauge
            .builder("gaia_actor_triggered", triggeredEnd) { triggeredEnd }
            .tags(listOf(Tag.of("name", name), Tag.of("ain", id), Tag.of("event", "end")))
            .strongReference(true)
            .register(registry)

        observer.subscribe {
            when (it) {
                Event.START -> {
                    notificationChannel.sendMessage(messageStart).queue()
                    triggeredStart += 1
                    logger.info("Triggered start: $name ($id)")
                }
                Event.END -> {
                    notificationChannel.sendMessage(messageEnd).queue()
                    todoistClient.createTask(taskMessage)
                    triggeredEnd += 1

                    logger.info("Triggered end: $name ($id)")
                }
            }
        }
    }

    override suspend fun run(): Double {
        val measurement = fritzBox.power(id)?.also { measurement ->
            triggeringQueues.forEach { it.add(measurement) }
        } ?: 0.0

        gauge = measurement.toDouble()
        logger.info("Value from FritzBox actor '${name}' (${id}) $measurement")

        return measurement.toDouble()
    }

    companion object {
        fun fromConfig(
            key: String,
            fritzBox: FritzBox,
            registry: PrometheusMeterRegistry,
            todoistClient: TodoistClient,
            notificationChannel: TextChannel,
            config: Config
        ): FritzboxActor {
            val ain = config.getString("actors.$key.ain")
            val name = config.getString("actors.$key.name")
            val messageStart = config.getString("actors.$key.messageStart")
            val messageEnd = config.getString("actors.$key.messageEnd")
            val taskEnd = config.getString("actors.$key.taskEnd")

            val queueSize = config.getInt("actors.$key.queue.size")
            val thresholdStart = config.getDouble("actors.$key.queue.thresholdStart").toFloat()
            val thresholdEnd = config.getDouble("actors.$key.queue.thresholdEnd").toFloat()

            val observer = Observer()

            return FritzboxActor(
                ain,
                name,
                fritzBox,
                registry,
                listOf(
                    TriggeringQueue(queueSize, TriggeringQueue.Trigger({ f: Float -> f > thresholdStart }) {
                        observer.notify(Event.START)
                    }),
                    TriggeringQueue(queueSize, TriggeringQueue.Trigger({ f: Float -> f < thresholdEnd }) {
                        observer.notify(Event.END)
                    })
                ),
                observer,
                notificationChannel,
                messageStart,
                messageEnd,
                taskEnd,
                todoistClient
            )
        }
    }

    class FritzBox(
        private val password: String,
        private val userName: String = "",
        private val baseURL: URL = URL("http://fritz.box"),
        private val client: OkHttpClient
    ) {
        private var sid: String = ""

        // power returns the kW/h consumed by the device on ain
        suspend fun power(ain: String): Float? {
            val body = withContext(Dispatchers.IO) {
                login()

                val response = request(ain, "getswitchpower")
                response.body?.source()?.readString(Charsets.UTF_8)
            }

            return try {
                body?.toFloat()?.let { it * 0.001F } ?: throw IllegalStateException("Could not get power")
            } catch (e: NumberFormatException) {
                null
            }
        }

        private fun request(ain: String, command: String): Response {
            val request = Request.Builder()
                .url("$baseURL/webservices/homeautoswitch.lua?sid=$sid&ain=$ain&switchcmd=$command")
                .build()

            return client.newCall(request).execute()
        }

        private fun login() {
            val request = Request.Builder()
                .url("$baseURL/login_sid.lua?version=2&username=$userName&sid=$sid")
                .build()

            val response = client.newCall(request).execute()
            val mapper = XmlMapper().registerKotlinModule()
            val sessionInfo = mapper.readValue(response.body?.byteStream(), SessionInfo::class.java)

            if (sessionInfo.valid) {
                sid = sessionInfo.sid
                return
            }

            val challenge = solveChallenge(sessionInfo.challenge, password)

            val loginRequest = Request.Builder()
                .url("$baseURL/login_sid.lua?version=2&username=$userName&response=$challenge")
                .build()

            val loginResponse = client.newCall(loginRequest).execute()
            val loginSessionInfo = mapper.readValue(loginResponse.body?.byteStream(), SessionInfo::class.java)

            if (loginSessionInfo.valid) {
                sid = loginSessionInfo.sid
                return
            } else {
                throw RuntimeException("$javaClass rejected auth credentials, blocked for ${loginSessionInfo.blockTime} secs.")
            }
        }

        companion object {
            fun solveChallenge(challenge: String, password: String): String {
                val parts = challenge.split('$')
                val iter1 = parts[1].toInt()
                val salt1 = parts[2].toHexByteArray()
                val iter2 = parts[3].toInt()
                val salt2 = parts[4].toHexByteArray()

                val encoded = password.toByteArray(StandardCharsets.UTF_8)

                val hash1 = encrypt(encoded, salt1, iter1)
                val hash2 = encrypt(hash1, salt2, iter2)

                return "${parts[4]}$${hash2.toHexString()}"
            }

            private fun encrypt(password: ByteArray, salt: ByteArray, iterations: Int): ByteArray {
                val alg = "HmacSHA256"
                val sha256mac = Mac.getInstance(alg)

                sha256mac.init(SecretKeySpec(password, alg))

                val ret = ByteArray(sha256mac.macLength)
                var tmp = ByteArray(salt.size + 4)

                System.arraycopy(salt, 0, tmp, 0, salt.size)
                tmp[salt.size + 3] = 1

                for (i in 0 until iterations) {
                    tmp = sha256mac.doFinal(tmp)

                    for (k in ret.indices) {
                        ret[k] = ret[k] xor tmp[k]
                    }
                }

                return ret
            }
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        private data class SessionInfo(
            @JsonProperty("SID") val sid: String,
            @JsonProperty("Challenge") val challenge: String,
            @JsonProperty("BlockTime") val blockTime: Int) {

            val valid = sid != "0000000000000000"
        }
    }
}

private fun ByteArray.toHexString(): String {
    val s: StringBuilder = StringBuilder(this.size * 2)

    this.forEach {
        s.append(String.format("%02x", it))
    }

    return s.toString()
}

private fun String.toHexByteArray(): ByteArray {
    val len: Int = this.length / 2
    val ret = ByteArray(len)

    for (i in 0 until len) {
        ret[i] = this.substring(i * 2, i * 2 + 2).toShort(16).toByte()
    }

    return ret
}