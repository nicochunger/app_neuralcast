package com.neuralcast.radioplayer.model

data class RadioStation(
    val id: String,
    val name: String,
    val streamUrl: String,
    val backgroundResId: Int,
    val artworkResId: Int,
    val description: String? = null
)

enum class PlaybackStatus {
    Idle,
    Buffering,
    Playing,
    Error
}

data class UiState(
    val stations: List<RadioStation> = emptyList(),
    val activeStationId: String? = null,
    val playbackStatus: PlaybackStatus = PlaybackStatus.Idle,
    val nowPlaying: String? = null,
    val errorMessage: String? = null
)
