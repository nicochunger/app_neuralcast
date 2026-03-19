package com.neuralcast.radioplayer.model

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val backgroundResId: Int,
    val artworkResId: Int,
    val description: String? = null,
    val timezoneId: String = "Europe/Zurich",
    val openRotationThreshold: Int? = null
)

enum class PlaybackStatus {
    Idle,
    Buffering,
    Playing,
    Error
}

data class PlaybackHistoryEntry(
    val track: String,
    val playedAt: Long
)

data class RequestableSong(
    val requestId: String,
    val requestUrl: String,
    val text: String,
    val artist: String,
    val title: String
) {
    val displayText: String
        get() = when {
            artist.isBlank() && title.isNotBlank() -> title
            artist.isNotBlank() && title.isNotBlank() -> "$artist - $title"
            text.isNotBlank() -> text
            else -> "Unknown Song"
        }
}

data class SongRequestState(
    val stationId: String? = null,
    val stationName: String = "",
    val isLoading: Boolean = false,
    val songs: List<RequestableSong> = emptyList(),
    val submittingRequestId: String? = null
) {
    val isVisible: Boolean
        get() = stationId != null
}

data class UiState(
    val stations: List<RadioStation> = emptyList(),
    val listenerCounts: Map<String, Int> = emptyMap(),
    val scheduleSummaries: Map<String, StationScheduleSummary> = emptyMap(),
    val scheduleDays: Map<String, StationScheduleDayState> = emptyMap(),
    val activeStationId: String? = null,
    val playbackStatus: PlaybackStatus = PlaybackStatus.Idle,
    val nowPlaying: String? = null,
    val errorMessage: String? = null,
    val songRequestState: SongRequestState = SongRequestState(),
    val sleepTimerRemaining: Long? = null,
    val recentlyPlayed: List<PlaybackHistoryEntry> = emptyList(),
    val appPreferences: AppPreferences = AppPreferences(),
    val isAdminModeEnabled: Boolean = false,
    val isAdminModeAuthenticating: Boolean = false,
    val skippingStationId: String? = null,
    val hostAdminConsole: HostAdminConsoleState = HostAdminConsoleState()
)
