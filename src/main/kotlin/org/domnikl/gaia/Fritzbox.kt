package org.domnikl.gaia

import jakarta.xml.bind.annotation.XmlAccessType
import jakarta.xml.bind.annotation.XmlAccessorType
import jakarta.xml.bind.annotation.XmlElement
import jakarta.xml.bind.annotation.XmlRootElement
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.codec.xml.Jaxb2XmlDecoder
import org.springframework.http.codec.xml.Jaxb2XmlEncoder
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.ExchangeStrategies
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.bodyToMono
import java.nio.charset.StandardCharsets
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.experimental.xor

@ConfigurationProperties(prefix = "gaia.fritzbox")
@EnableConfigurationProperties
data class FritzBoxProperties(
    val username: String,
    val password: String,
    val baseUrl: String,
    val appliances: Map<String, Appliance>
)

@Component
class FritzBox(
    private val properties: FritzBoxProperties,
    builder: WebClient.Builder
) {
    private var sid: String = ""
    private val client = builder
        .baseUrl(properties.baseUrl)
        .exchangeStrategies(ExchangeStrategies.builder().codecs {
            it.defaultCodecs().jaxb2Decoder(Jaxb2XmlDecoder())
            it.defaultCodecs().jaxb2Encoder(Jaxb2XmlEncoder())
        }.build())
        .build()

    fun list(): List<String> {
        return list<String>("getswitchlist").split(",").dropLast(1)
    }

    // power returns the Watt currently consumed/produced by the device on ain
    fun name(ain: String): String {
        return request<String>(ain, "getswitchname").trim()
    }

    // power returns the Watt currently consumed/produced by the device on ain
    fun power(ain: String): Double {
        return request<String>(ain, "getswitchpower").toDouble() * 0.001F
    }

    // energy returns the Watt hours consumed by the device on ain
    fun energy(ain: String): Double {
        return request<String>(ain, "getswitchenergy").toDouble()
    }

    // power returns the temperature in Celsius
    fun temperature(ain: String): Double {
        return request<String>(ain, "gettemperature").toDouble() / 10.0
    }

    private inline fun <reified T : Any> request(ain: String, command: String): T {
        login()
        val uri = "/webservices/homeautoswitch.lua?sid=$sid&ain=$ain&switchcmd=$command"

        return client.get()
            .uri(uri)
            .retrieve()
            .bodyToMono<T>()
            .block() ?: throw IllegalStateException("Failed to GET $uri")
    }

    private inline fun <reified T : Any> list(command: String): T {
        login()
        val uri = "/webservices/homeautoswitch.lua?sid=$sid&switchcmd=$command"

        return client.get()
            .uri(uri)
            .retrieve()
            .bodyToMono<T>()
            .block() ?: throw IllegalStateException("Failed to GET $uri")
    }

    private fun login() {
        val sessionInfo = client.get()
            .uri("/login_sid.lua?version=2&username=${properties.username}&sid=$sid")
            .retrieve()
            .bodyToMono<SessionInfo>()
            .block() ?: throw IllegalStateException("Failed to login")

        if (sessionInfo.isValid()) {
            sid = sessionInfo.sid
            return
        }

        val challenge = solveChallenge(sessionInfo.challenge, properties.password)

        val loginSessionInfo = client.get()
            .uri("/login_sid.lua?version=2&username=${properties.username}&response=$challenge")
            .retrieve()
            .bodyToMono<SessionInfo>()
            .block() ?: throw IllegalStateException("Failed to fetch login session info")

        if (loginSessionInfo.isValid()) {
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

    @XmlRootElement(name = "SessionInfo")
    @XmlAccessorType(XmlAccessType.FIELD)
    private class SessionInfo {
        @field:XmlElement(name="SID") var sid: String = ""
        @field:XmlElement(name="Challenge") var challenge: String = ""
        @field:XmlElement(name="BlockTime") var blockTime: Int = 0

        fun isValid() = sid != "0000000000000000"
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
