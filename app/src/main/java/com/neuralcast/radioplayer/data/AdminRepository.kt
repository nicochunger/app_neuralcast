package com.neuralcast.radioplayer.data

import com.neuralcast.radioplayer.model.RadioStation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class AdminRepository {

    suspend fun validateAdminApiKey(station: RadioStation, apiKey: String) = withContext(Dispatchers.IO) {
        val endpoint = "${extractBaseUrl(station.streamUrl)}/api/admin/stations"
        val response = executeGet(endpoint, apiKey)
        if (response.code !in 200..299) {
            throw IOException(buildErrorMessage(response, "Invalid admin API key or insufficient permissions."))
        }
    }

    suspend fun skipCurrentTrack(station: RadioStation, apiKey: String): String = withContext(Dispatchers.IO) {
        val baseUrl = extractBaseUrl(station.streamUrl)
        val fallbackMessage = "Unable to skip the current track."
        val attempts = listOf(
            SkipAttempt(
                method = "POST",
                url = "$baseUrl/api/station/${station.id}/backend/skip"
            ),
            SkipAttempt(
                method = "PUT",
                url = "$baseUrl/api/admin/debug/station/${station.id}/telnet?command=radio.skip"
            ),
            SkipAttempt(
                method = "PUT",
                url = "$baseUrl/api/admin/debug/station/${station.id}/telnet?command=skip"
            ),
            SkipAttempt(
                method = "PUT",
                url = "$baseUrl/api/admin/debug/station/${station.id}/telnet",
                jsonBody = "{\"command\":\"radio.skip\"}"
            ),
            SkipAttempt(
                method = "PUT",
                url = "$baseUrl/api/admin/debug/station/${station.id}/telnet",
                jsonBody = "{\"command\":\"skip\"}"
            ),
            SkipAttempt(
                method = "PUT",
                url = "$baseUrl/api/admin/debug/station/${station.id}/nextsong",
                treatUriOnlyAsPreview = true
            )
        )

        var lastErrorMessage = fallbackMessage
        for (attempt in attempts) {
            val response = executeRequest(attempt, apiKey)
            if (response.code !in 200..299) {
                lastErrorMessage = buildErrorMessage(response, fallbackMessage)
                continue
            }

            try {
                val message = parseSuccessMessage(
                    rawBody = response.body,
                    fallback = "Skipped current track on ${station.name}.",
                    treatUriOnlyAsPreview = attempt.treatUriOnlyAsPreview
                )
                if (message == null) {
                    lastErrorMessage = "The server accepted a debug next-song call, but did not confirm a force skip."
                    continue
                }
                return@withContext message
            } catch (error: IOException) {
                lastErrorMessage = error.message ?: fallbackMessage
            }
        }

        throw IOException(lastErrorMessage)
    }

    private fun executeGet(url: String, apiKey: String): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            configureConnection(apiKey)
        }
        return execute(connection)
    }

    private fun executeRequest(request: SkipAttempt, apiKey: String): HttpResponse {
        val connection = (URL(request.url).openConnection() as HttpURLConnection).apply {
            requestMethod = request.method
            configureConnection(apiKey)
            request.jsonBody?.let { body ->
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.bufferedWriter().use { writer ->
                    writer.write(body)
                }
            }
        }
        return execute(connection)
    }

    private fun HttpURLConnection.configureConnection(apiKey: String) {
        setRequestProperty("Accept", "application/json")
        setRequestProperty("Authorization", "Bearer $apiKey")
        setRequestProperty("X-API-Key", apiKey)
        connectTimeout = NETWORK_TIMEOUT_MS
        readTimeout = NETWORK_TIMEOUT_MS
        instanceFollowRedirects = true
    }

    private fun execute(connection: HttpURLConnection): HttpResponse {
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

    private fun parseSuccessMessage(
        rawBody: String,
        fallback: String,
        treatUriOnlyAsPreview: Boolean = false
    ): String? {
        if (rawBody.isBlank()) return fallback

        runCatching {
            val payload = JSONObject(rawBody)
            val successValue = payload.opt("success")
            val isSuccess = when (successValue) {
                is Boolean -> successValue
                is String -> successValue.equals("true", ignoreCase = true)
                else -> true
            }
            val message = payload.optString("formatted_message")
                .ifBlank { payload.optString("message") }
            val uri = payload.optString("uri")

            if (!isSuccess) {
                throw IOException(message.ifBlank { fallback })
            }

            if (treatUriOnlyAsPreview && message.isBlank() && uri.isNotBlank()) {
                return null
            }

            message.ifBlank { fallback }
        }.getOrNull()?.let { parsed ->
            if (parsed.isNotBlank()) return parsed
        }

        val plainText = rawBody
            .substringBefore('\n')
            .removePrefix("Error:")
            .substringBefore(" on /")
            .trim()
        return plainText.ifBlank { fallback }
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

    private data class HttpResponse(
        val code: Int,
        val body: String
    )

    private data class SkipAttempt(
        val method: String,
        val url: String,
        val jsonBody: String? = null,
        val treatUriOnlyAsPreview: Boolean = false
    )

    private companion object {
        private const val NETWORK_TIMEOUT_MS = 15_000
    }
}
