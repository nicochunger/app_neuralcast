package com.neuralcast.radioplayer.data

import com.neuralcast.radioplayer.model.RadioStation
import com.neuralcast.radioplayer.model.RequestableSong
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class SongRequestRepository {

    suspend fun getRequestableSongs(station: RadioStation): List<RequestableSong> = withContext(Dispatchers.IO) {
        val endpoint = "${extractBaseUrl(station.streamUrl)}/api/station/${station.id}/requests"
        val response = executeGet(endpoint)
        if (response.code !in 200..299) {
            throw IOException(buildErrorMessage(response, "Unable to load requestable songs."))
        }
        parseRequestableSongs(response.body)
    }

    suspend fun submitSongRequest(station: RadioStation, song: RequestableSong): String = withContext(Dispatchers.IO) {
        val absoluteUrl = resolveUrl(extractBaseUrl(station.streamUrl), song.requestUrl)
        val response = executeGet(absoluteUrl)
        if (response.code !in 200..299) {
            throw IOException(buildErrorMessage(response, "Unable to submit song request."))
        }

        if (response.body.isBlank()) {
            return@withContext "Your request has been submitted."
        }

        runCatching {
            val payload = JSONObject(response.body)
            val success = payload.optBoolean("success", false)
            val message = payload.optString("formatted_message").ifBlank {
                payload.optString("message")
            }
            if (!success) {
                throw IOException(message.ifBlank { "Unable to submit song request." })
            }
            message.ifBlank { "Your request has been submitted." }
        }.getOrElse {
            "Your request has been submitted."
        }
    }

    private fun parseRequestableSongs(rawBody: String): List<RequestableSong> {
        val array = JSONArray(rawBody)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val requestId = entry.optString("request_id").ifBlank {
                    entry.optString("id")
                }
                val requestUrl = entry.optString("request_url")
                val song = entry.optJSONObject("song") ?: JSONObject()
                val text = song.optString("text")
                val artist = song.optString("artist")
                val title = song.optString("title")

                if (requestId.isBlank() || requestUrl.isBlank()) {
                    continue
                }

                add(
                    RequestableSong(
                        requestId = requestId,
                        requestUrl = requestUrl,
                        text = text,
                        artist = artist,
                        title = title
                    )
                )
            }
        }
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
            val body = readStream(connection, code)
            HttpResponse(code = code, body = body)
        } finally {
            connection.disconnect()
        }
    }

    private fun readStream(connection: HttpURLConnection, code: Int): String {
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader()?.use { it.readText() }.orEmpty()
    }

    private fun buildErrorMessage(response: HttpResponse, fallback: String): String {
        if (response.body.isBlank()) return fallback

        runCatching {
            val payload = JSONObject(response.body)
            payload.optString("formatted_message")
                .ifBlank { payload.optString("message") }
                .ifBlank { fallback }
        }.getOrNull()?.let { parsed ->
            if (parsed.isNotBlank()) return parsed
        }

        val plainText = response.body
            .substringBefore('\n')
            .removePrefix("Error:")
            .substringBefore(" on /")
            .trim()

        return plainText.ifBlank { fallback }
    }

    private fun extractBaseUrl(streamUrl: String): String {
        val uri = URI(streamUrl)
        val host = uri.host ?: throw IOException("Invalid station URL: $streamUrl")
        val port = if (uri.port == -1) "" else ":${uri.port}"
        val scheme = uri.scheme ?: throw IOException("Invalid station URL: $streamUrl")
        return "$scheme://$host$port"
    }

    private fun resolveUrl(baseUrl: String, urlOrPath: String): String {
        return if (urlOrPath.startsWith("http://") || urlOrPath.startsWith("https://")) {
            urlOrPath
        } else {
            val normalizedPath = if (urlOrPath.startsWith("/")) urlOrPath else "/$urlOrPath"
            "$baseUrl$normalizedPath"
        }
    }

    private data class HttpResponse(
        val code: Int,
        val body: String
    )

    private companion object {
        private const val NETWORK_TIMEOUT_MS = 15_000
    }
}
