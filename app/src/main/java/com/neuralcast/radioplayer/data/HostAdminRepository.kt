package com.neuralcast.radioplayer.data

import com.neuralcast.radioplayer.model.HostAdminJob
import com.neuralcast.radioplayer.model.HostAdminOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HostAdminRepository {

    suspend fun getOptions(baseUrl: String, token: String): HostAdminOptions = withContext(Dispatchers.IO) {
        val response = executeRequest(
            method = "GET",
            url = "${normalizeBaseUrl(baseUrl)}/admin/options",
            token = token
        )
        if (response.code !in 200..299) {
            throw IOException(buildErrorMessage(response, "Unable to load host admin options."))
        }

        val payload = JSONObject(response.body)
        HostAdminOptions(
            stations = payload.optJSONArray("stations").toStringList(),
            archetypes = payload.optJSONArray("archetypes").toStringList()
        )
    }

    suspend fun submitForceArchetype(
        baseUrl: String,
        token: String,
        station: String,
        archetype: String,
        dryRun: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("station", station)
            .put("archetype", archetype)
            .put("dry_run", dryRun)
            .toString()

        val response = executeRequest(
            method = "POST",
            url = "${normalizeBaseUrl(baseUrl)}/admin/force-archetype",
            token = token,
            jsonBody = body
        )
        if (response.code !in 200..299) {
            throw IOException(buildErrorMessage(response, "Unable to start a forced host run."))
        }

        val payload = JSONObject(response.body)
        payload.optString("job_id").takeIf { it.isNotBlank() }
            ?: throw IOException("The admin API accepted the request but did not return a job ID.")
    }

    suspend fun getJobStatus(baseUrl: String, token: String, jobId: String): HostAdminJob =
        withContext(Dispatchers.IO) {
            val response = executeRequest(
                method = "GET",
                url = "${normalizeBaseUrl(baseUrl)}/admin/jobs/$jobId",
                token = token
            )
            if (response.code !in 200..299) {
                throw IOException(buildErrorMessage(response, "Unable to load host job status."))
            }

            val payload = JSONObject(response.body)
            HostAdminJob(
                jobId = payload.optString("job_id").ifBlank { jobId },
                station = payload.optString("station"),
                archetype = payload.optString("archetype"),
                dryRun = payload.optBoolean("dry_run", false),
                status = payload.optString("status").ifBlank { "unknown" },
                acceptedAt = payload.optNullableString("accepted_at"),
                startedAt = payload.optNullableString("started_at"),
                finishedAt = payload.optNullableString("finished_at"),
                exitCode = payload.optNullableInt("exit_code"),
                logTail = payload.optNullableString("log_tail")
            )
        }

    private fun executeRequest(
        method: String,
        url: String,
        token: String,
        jsonBody: String? = null
    ): HttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            connectTimeout = NETWORK_TIMEOUT_MS
            readTimeout = NETWORK_TIMEOUT_MS
            instanceFollowRedirects = true
            if (jsonBody != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                outputStream.bufferedWriter().use { writer ->
                    writer.write(jsonBody)
                }
            }
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

    private fun buildErrorMessage(response: HttpResponse, fallback: String): String {
        if (response.body.isNotBlank()) {
            runCatching {
                val payload = JSONObject(response.body)
                val detail = payload.opt("detail")
                when (detail) {
                    is String -> detail
                    is JSONObject -> detail.optString("message")
                    is JSONArray -> ""
                    else -> payload.optString("message")
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { detail ->
                return detail
            }
        }

        return when (response.code) {
            401 -> "Invalid host admin token."
            404 -> "The requested host admin job was not found."
            409 -> "A host job is already running for this station."
            422 -> "The host admin request was invalid."
            503 -> "The host admin service is misconfigured."
            else -> fallback
        }
    }

    private fun normalizeBaseUrl(baseUrl: String): String {
        return baseUrl.trim().trimEnd('/')
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        val items = mutableListOf<String>()
        for (index in 0 until length()) {
            val value = optString(index)
            if (value.isNotBlank()) {
                items += value
            }
        }
        return items
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (isNull(key)) return null
        val value = optString(key)
        return value.takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        return if (isNull(key)) {
            null
        } else {
            runCatching { getInt(key) }.getOrNull()
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
