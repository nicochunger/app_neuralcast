package com.neuralcast.radioplayer.data

import com.neuralcast.radioplayer.model.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class StationStatusRepository {

    suspend fun getCurrentListeners(station: RadioStation): Int = withContext(Dispatchers.IO) {
        val endpoint = "${extractBaseUrl(station.streamUrl)}/api/nowplaying/${station.id}"
        val response = executeGet(endpoint)
        if (response.code !in 200..299) {
            throw IOException("Unable to load listener count for ${station.name}.")
        }
        parseCurrentListeners(response.body)
    }

    private fun parseCurrentListeners(rawBody: String): Int {
        if (rawBody.isBlank()) {
            throw IOException("Listener response was empty.")
        }
        val payload = JSONObject(rawBody)
        val listeners = payload.optJSONObject("listeners")
            ?: throw IOException("Listener metadata was missing.")

        val current = when (val value = listeners.opt("current")) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        } ?: when (val value = listeners.opt("total")) {
            is Number -> value.toInt()
            is String -> value.toIntOrNull()
            else -> null
        } ?: throw IOException("Listener metadata was missing.")

        return current.coerceAtLeast(0)
    }

    private fun executeGet(url: String): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            instanceFollowRedirects = true
        }

        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResponse(code = code, body = body)
        } finally {
            connection.disconnect()
        }
    }

    private fun extractBaseUrl(streamUrl: String): String {
        val uri = URI(streamUrl)
        val host = uri.host ?: throw IOException("Invalid station URL: $streamUrl")
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val scheme = uri.scheme ?: throw IOException("Invalid station URL: $streamUrl")
        return "$scheme://$host$port"
    }

    private data class HttpResponse(
        val code: Int,
        val body: String
    )

    private companion object {
        private const val NETWORK_TIMEOUT_MS = 15_000
    }
}
