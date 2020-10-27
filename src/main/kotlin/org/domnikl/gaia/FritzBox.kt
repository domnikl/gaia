package org.domnikl.gaia

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URL
import java.security.MessageDigest
import javax.xml.bind.DatatypeConverter

class FritzBox(
    private val password: String,
    private val userName: String = "",
    private val baseURL: URL = URL("http://fritz.box"),
    private val client: OkHttpClient
) {
    private var sid: String = ""

    // power returns the kW/h consumed by the device on ain
    suspend fun power(ain: String): Float {
        val body = withContext(Dispatchers.IO) {
            login()

            val response = request(ain, "getswitchpower")
            response.body?.source()?.readString(Charsets.UTF_8)
        }

        return body?.toFloat()?.let { it * 0.001F } ?: throw IllegalStateException("Could not get power")
    }

    private fun request(ain: String, command: String): Response {
        val request = Request.Builder()
            .url("$baseURL/webservices/homeautoswitch.lua?sid=$sid&ain=$ain&switchcmd=$command")
            .build()

        return client.newCall(request).execute()
    }

    private fun login() {
        val request = Request.Builder()
            .url("$baseURL/login_sid.lua?sid=$sid")
            .build()

        val response = client.newCall(request).execute()
        val mapper = XmlMapper().registerKotlinModule()
        val sessionInfo = mapper.readValue(response.body?.byteStream(), SessionInfo::class.java)

        if (sessionInfo.valid) {
            sid = sessionInfo.sid
            return
        }

        val challenge = solveChallenge(sessionInfo)

        val loginRequest = Request.Builder()
            .url("$baseURL/login_sid.lua?username=${userName}&response=$challenge")
            .build()

        val loginResponse = client.newCall(loginRequest).execute()
        val loginSessionInfo = mapper.readValue(loginResponse.body?.byteStream(), SessionInfo::class.java)

        if (loginSessionInfo.valid) {
            sid = loginSessionInfo.sid
            return
        } else {
            throw RuntimeException("$javaClass rejected auth credentials")
        }
    }

    private fun solveChallenge(s: SessionInfo): String {
        val md5 = MessageDigest.getInstance("MD5").also {
            it.update("${s.challenge}-$password".toByteArray(Charsets.UTF_16LE))
        }

        val hash = DatatypeConverter.printHexBinary(md5.digest()).toLowerCase()

        return "${s.challenge}-$hash"
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private data class SessionInfo(
        @JsonProperty("SID") val sid: String,
        @JsonProperty("Challenge") val challenge: String) {

        val valid = sid != "0000000000000000"
    }
}
