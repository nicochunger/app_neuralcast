package com.neuralcast.radioplayer.data

import com.neuralcast.radioplayer.model.RadioStation
import com.neuralcast.radioplayer.model.ScheduleSegmentKind
import com.neuralcast.radioplayer.model.StationScheduleDayState
import com.neuralcast.radioplayer.model.StationScheduleSegment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class StationScheduleRepository {

    suspend fun getScheduleForDay(station: RadioStation, date: LocalDate): StationScheduleDayState =
        withContext(Dispatchers.IO) {
            val zoneId = ZoneId.of(station.timezoneId)
            val dayStart = date.atStartOfDay(zoneId)
            val nextDayStart = dayStart.plusDays(1)
            val encodedNow = URLEncoder.encode(
                dayStart.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
                "UTF-8"
            )
            val endpoint = buildString {
                append(extractBaseUrl(station.streamUrl))
                append("/api/station/")
                append(station.id)
                append("/schedule?rows=")
                append(SCHEDULE_ROWS)
                append("&now=")
                append(encodedNow)
            }

            val response = executeGet(endpoint)
            if (response.code !in 200..299) {
                throw IOException(buildErrorMessage(response, "Unable to load schedule for ${station.name}."))
            }

            val entries = parseEntries(response.body)
            val segments = buildSegments(
                station = station,
                entries = entries,
                dayStart = dayStart.toInstant(),
                dayEnd = nextDayStart.toInstant(),
                zoneId = zoneId
            )

            StationScheduleDayState(
                date = date,
                zoneId = zoneId.id,
                segments = segments
            )
        }

    private fun buildSegments(
        station: RadioStation,
        entries: List<RawScheduleEntry>,
        dayStart: Instant,
        dayEnd: Instant,
        zoneId: ZoneId
    ): List<StationScheduleSegment> {
        val relevantEntries = entries.filter { entry ->
            entry.end.isAfter(dayStart) && entry.start.isBefore(dayEnd)
        }

        val boundaries = buildSet {
            add(dayStart.toEpochMilli())
            add(dayEnd.toEpochMilli())
            relevantEntries.forEach { entry ->
                add(maxOf(entry.start.toEpochMilli(), dayStart.toEpochMilli()))
                add(minOf(entry.end.toEpochMilli(), dayEnd.toEpochMilli()))
            }
        }.sorted()

        if (boundaries.size < 2) {
            return listOf(
                StationScheduleSegment(
                    startMillis = dayStart.toEpochMilli(),
                    endMillis = dayEnd.toEpochMilli(),
                    kind = ScheduleSegmentKind.OpenSlot
                )
            )
        }

        val rawSegments = buildList {
            for (index in 0 until boundaries.lastIndex) {
                val startMillis = boundaries[index]
                val endMillis = boundaries[index + 1]
                if (endMillis <= startMillis) continue

                val activeNames = relevantEntries
                    .filter { entry ->
                        entry.start.toEpochMilli() < endMillis && entry.end.toEpochMilli() > startMillis
                    }
                    .map { it.title }
                    .distinct()
                    .sorted()

                val kind = when {
                    activeNames.isEmpty() -> ScheduleSegmentKind.OpenSlot
                    station.openRotationThreshold != null &&
                        activeNames.size >= station.openRotationThreshold -> {
                        ScheduleSegmentKind.OpenRotation
                    }
                    else -> ScheduleSegmentKind.Scheduled
                }

                add(
                    StationScheduleSegment(
                        startMillis = startMillis,
                        endMillis = endMillis,
                        kind = kind,
                        playlistNames = activeNames
                    )
                )
            }
        }

        return mergeAdjacentSegments(rawSegments, zoneId)
    }

    private fun mergeAdjacentSegments(
        segments: List<StationScheduleSegment>,
        zoneId: ZoneId
    ): List<StationScheduleSegment> {
        if (segments.isEmpty()) return emptyList()

        val merged = mutableListOf<StationScheduleSegment>()
        segments.forEach { segment ->
            val previous = merged.lastOrNull()
            if (
                previous != null &&
                previous.endMillis == segment.startMillis &&
                previous.kind == segment.kind &&
                previous.playlistNames == segment.playlistNames &&
                sameCalendarDay(previous.startMillis, segment.startMillis, zoneId)
            ) {
                merged[merged.lastIndex] = previous.copy(endMillis = segment.endMillis)
            } else {
                merged += segment
            }
        }
        return merged
    }

    private fun sameCalendarDay(startMillis: Long, otherMillis: Long, zoneId: ZoneId): Boolean {
        val firstDay = Instant.ofEpochMilli(startMillis).atZone(zoneId).toLocalDate()
        val secondDay = Instant.ofEpochMilli(otherMillis).atZone(zoneId).toLocalDate()
        return firstDay == secondDay
    }

    private fun parseEntries(rawBody: String): List<RawScheduleEntry> {
        val array = JSONArray(rawBody)
        return buildList(array.length()) {
            for (index in 0 until array.length()) {
                val entry = array.optJSONObject(index) ?: continue
                val title = entry.optString("title")
                    .ifBlank { entry.optString("name") }
                    .ifBlank { entry.optString("description") }
                val start = entry.optString("start")
                val end = entry.optString("end")

                if (title.isBlank() || start.isBlank() || end.isBlank()) {
                    continue
                }

                add(
                    RawScheduleEntry(
                        title = title,
                        start = parseDateTime(start),
                        end = parseDateTime(end)
                    )
                )
            }
        }
    }

    private fun parseDateTime(value: String): Instant {
        return runCatching {
            ZonedDateTime.parse(value).toInstant()
        }.getOrElse {
            Instant.parse(value)
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
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            HttpResponse(code = code, body = body)
        } finally {
            connection.disconnect()
        }
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

    private data class RawScheduleEntry(
        val title: String,
        val start: Instant,
        val end: Instant
    )

    private data class HttpResponse(
        val code: Int,
        val body: String
    )

    private companion object {
        private const val NETWORK_TIMEOUT_MS = 15_000
        private const val SCHEDULE_ROWS = 300
    }
}
