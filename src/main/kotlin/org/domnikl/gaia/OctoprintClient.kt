package org.domnikl.gaia

import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.*

class OctoprintClient(private val accessToken: String, private val client: OkHttpClient) {
    fun getPrinterInfo(content: String): Map<String, Temperature> {
        val request = Request.Builder()
            .header("Authorization", "Bearer $accessToken")
            .url("http://unifi-controller:5000/api/printer")
            .build()

        val body = client.newCall(request).execute().body?.string()
        val response = ObjectMapper().readValue(body, Response::class.java)

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
