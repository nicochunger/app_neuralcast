package com.neuralcast.radioplayer.data

import com.neuralcast.radioplayer.model.HostAdminCapabilities
import com.neuralcast.radioplayer.model.HostAdminJob
import com.neuralcast.radioplayer.model.HostAdminOperationCapability
import com.neuralcast.radioplayer.model.HostAdminScheduleOptions
import com.neuralcast.radioplayer.model.HOST_ADMIN_OPERATION_FORCE_ARCHETYPE
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

class HostAdminRepository {

    suspend fun getCapabilities(baseUrl: String, token: String): HostAdminCapabilities = withContext(Dispatchers.IO) {
        val response = executeRequest(
            method = "GET",
            url = "${normalizeBaseUrl(baseUrl)}/admin/capabilities",
            token = token
        )
        if (response.code !in 200..299) {
            throw IOException(buildErrorMessage(response, "Unable to load host admin capabilities."))
        }

        val payload = JSONObject(response.body)
        HostAdminCapabilities(
            stations = payload.optJSONArray("stations").toStringList(),
            archetypes = payload.optJSONArray("archetypes").toStringList(),
            trackFocusValues = payload.optJSONArray("track_focus_values").toStringList(),
            trackFocusArchetypes = payload.optJSONArray("track_focus_archetypes").toStringList().toSet(),
            operations = payload.optJSONObject("operations").toOperationCapabilities()
        )
    }

    suspend fun submitForceArchetype(
        baseUrl: String,
        token: String,
        station: String,
        archetype: String,
        trackFocus: String? = null,
        dryRun: Boolean = false
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("station", station)
            .put("archetype", archetype)
            .put("dry_run", dryRun)
        trackFocus?.takeIf { it.isNotBlank() }?.let { focus ->
            body.put("track_focus", focus)
        }

        val response = executeRequest(
            method = "POST",
            url = "${normalizeBaseUrl(baseUrl)}/admin/force-archetype",
            token = token,
            jsonBody = body.toString()
        )
        if (response.code !in 200..299) {
            throw IOException(buildErrorMessage(response, "Unable to start a forced host run."))
        }

        val payload = JSONObject(response.body)
        payload.optString("job_id").takeIf { it.isNotBlank() }
            ?: throw IOException("The admin API accepted the request but did not return a job ID.")
    }

    suspend fun submitScheduleGenerator(
        baseUrl: String,
        token: String,
        station: String,
        dryRun: Boolean = false,
        forceApply: Boolean = false,
        seedMode: String? = null,
        seedSalt: String? = null,
        weekStartDate: String? = null,
        openRatioMin: Double? = null,
        openRatioMax: Double? = null,
        minOpenSlots: Int? = null,
        maxOpenSlots: Int? = null,
        minBlockMinutes: Int? = null,
        maxBlockMinutes: Int? = null
    ): String = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("station", station)
            .put("dry_run", dryRun)
            .put("force_apply", forceApply)
        seedMode?.takeIf { it.isNotBlank() }?.let { body.put("seed_mode", it) }
        seedSalt?.takeIf { it.isNotBlank() }?.let { body.put("seed_salt", it) }
        weekStartDate?.takeIf { it.isNotBlank() }?.let { body.put("week_start_date", it) }
        openRatioMin?.let { body.put("open_ratio_min", it) }
        openRatioMax?.let { body.put("open_ratio_max", it) }
        minOpenSlots?.let { body.put("min_open_slots", it) }
        maxOpenSlots?.let { body.put("max_open_slots", it) }
        minBlockMinutes?.let { body.put("min_block_minutes", it) }
        maxBlockMinutes?.let { body.put("max_block_minutes", it) }

        val response = executeRequest(
            method = "POST",
            url = "${normalizeBaseUrl(baseUrl)}/admin/run-schedule-generator",
            token = token,
            jsonBody = body.toString()
        )
        if (response.code !in 200..299) {
            throw IOException(buildErrorMessage(response, "Unable to start the schedule generator."))
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
                operation = payload.optString("operation").ifBlank { HOST_ADMIN_OPERATION_FORCE_ARCHETYPE },
                station = payload.optString("station"),
                archetype = payload.optNullableString("archetype"),
                trackFocus = payload.optNullableString("track_focus"),
                dryRun = payload.optBoolean("dry_run", false),
                scheduleOptions = payload.optJSONObject("schedule_options").toScheduleOptions(),
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

    private fun JSONObject?.toOperationCapabilities(): Map<String, HostAdminOperationCapability> {
        if (this == null) return emptyMap()

        val capabilities = linkedMapOf<String, HostAdminOperationCapability>()
        val keys = keys()
        while (keys.hasNext()) {
            val operation = keys.next()
            val payload = optJSONObject(operation) ?: continue
            capabilities[operation] = HostAdminOperationCapability(
                dryRunSupported = payload.optBoolean("dry_run_supported", false),
                trackFocusSupported = payload.optBoolean("track_focus_supported", false),
                forceApplySupported = payload.optBoolean("force_apply_supported", false),
                weekStartDateSupported = payload.optBoolean("week_start_date_supported", false),
                supportedSeedModes = payload.optJSONArray("supported_seed_modes").toStringList(),
                defaultSeedMode = payload.optNullableString("default_seed_mode"),
                supportedTuningFields = payload.optJSONArray("supported_tuning_fields").toStringList().toSet()
            )
        }
        return capabilities
    }

    private fun JSONObject?.toScheduleOptions(): HostAdminScheduleOptions? {
        if (this == null) return null
        return HostAdminScheduleOptions(
            forceApply = optBoolean("force_apply", false),
            seedMode = optNullableString("seed_mode"),
            seedSalt = optNullableString("seed_salt"),
            weekStartDate = optNullableString("week_start_date"),
            openRatioMin = optNullableDouble("open_ratio_min"),
            openRatioMax = optNullableDouble("open_ratio_max"),
            minOpenSlots = optNullableInt("min_open_slots"),
            maxOpenSlots = optNullableInt("max_open_slots"),
            minBlockMinutes = optNullableInt("min_block_minutes"),
            maxBlockMinutes = optNullableInt("max_block_minutes")
        )
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

    private fun JSONObject.optNullableDouble(key: String): Double? {
        return if (isNull(key)) {
            null
        } else {
            runCatching { getDouble(key) }.getOrNull()
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
