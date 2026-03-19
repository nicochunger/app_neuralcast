package com.neuralcast.radioplayer.model

import java.time.LocalDate

enum class ScheduleSegmentKind {
    Scheduled,
    OpenSlot,
    OpenRotation
}

data class StationScheduleSegment(
    val startMillis: Long,
    val endMillis: Long,
    val kind: ScheduleSegmentKind,
    val playlistNames: List<String> = emptyList()
)

data class StationScheduleDayState(
    val date: LocalDate,
    val zoneId: String,
    val segments: List<StationScheduleSegment> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

data class StationScheduleSummary(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val liveSegment: StationScheduleSegment? = null,
    val upNextSegment: StationScheduleSegment? = null
)
