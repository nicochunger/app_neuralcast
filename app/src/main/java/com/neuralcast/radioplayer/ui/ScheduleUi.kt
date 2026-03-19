package com.neuralcast.radioplayer.ui

import com.neuralcast.radioplayer.model.ScheduleSegmentKind
import com.neuralcast.radioplayer.model.StationScheduleSegment
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal fun scheduleSegmentTitle(segment: StationScheduleSegment): String {
    return when (segment.kind) {
        ScheduleSegmentKind.OpenSlot -> "Open slot"
        ScheduleSegmentKind.OpenRotation -> "Open rotation"
        ScheduleSegmentKind.Scheduled -> when (segment.playlistNames.size) {
            0 -> "Scheduled"
            1 -> segment.playlistNames.first()
            else -> "${segment.playlistNames.first()} +${segment.playlistNames.lastIndex} more"
        }
    }
}

internal fun scheduleSegmentDetail(
    segment: StationScheduleSegment,
    maxPlaylistNames: Int = 3,
    multiline: Boolean = false
): String? {
    val separator = if (multiline) "\n" else " • "
    val preview = previewPlaylistNames(
        names = segment.playlistNames,
        maxPlaylistNames = maxPlaylistNames,
        separator = separator
    )

    return when (segment.kind) {
        ScheduleSegmentKind.OpenSlot -> "Nothing is scheduled in this window."
        ScheduleSegmentKind.OpenRotation -> {
            val count = segment.playlistNames.size
            if (preview.isBlank()) {
                "$count playlists in rotation"
            } else {
                "$count playlists in rotation${if (multiline) "\n" else " · "}$preview"
            }
        }
        ScheduleSegmentKind.Scheduled -> preview.ifBlank { null }
    }
}

internal fun scheduleSegmentKindLabel(kind: ScheduleSegmentKind): String {
    return when (kind) {
        ScheduleSegmentKind.Scheduled -> "Scheduled"
        ScheduleSegmentKind.OpenSlot -> "Open slot"
        ScheduleSegmentKind.OpenRotation -> "Open rotation"
    }
}

internal fun formatScheduleTimeRange(segment: StationScheduleSegment, zoneId: ZoneId): String {
    return "${formatScheduleTime(segment.startMillis, zoneId)} - ${formatScheduleTime(segment.endMillis, zoneId)}"
}

private fun previewPlaylistNames(
    names: List<String>,
    maxPlaylistNames: Int,
    separator: String
): String {
    if (names.isEmpty()) return ""
    val visibleNames = names.take(maxPlaylistNames.coerceAtLeast(1))
    val hiddenCount = names.size - visibleNames.size
    val suffix = if (hiddenCount > 0) {
        "${if (visibleNames.isEmpty()) "" else separator}+$hiddenCount more"
    } else {
        ""
    }
    return visibleNames.joinToString(separator) + suffix
}

private fun formatScheduleTime(epochMillis: Long, zoneId: ZoneId): String {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(zoneId)
        .format(SCHEDULE_TIME_FORMATTER)
}

private val SCHEDULE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
