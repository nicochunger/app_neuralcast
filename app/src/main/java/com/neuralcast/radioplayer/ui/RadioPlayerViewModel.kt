package com.neuralcast.radioplayer.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.neuralcast.radioplayer.model.PlaybackStatus
import com.neuralcast.radioplayer.model.RadioStation
import com.neuralcast.radioplayer.model.UiState
import com.neuralcast.radioplayer.playback.PlaybackService
import com.neuralcast.radioplayer.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RadioPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val stations = listOf(
        RadioStation(
            id = "neuralcast",
            name = "NeuralCast",
            streamUrl = "https://neuralcast.duckdns.org/listen/neuralcast/radio.mp3",
            backgroundResId = R.drawable.neuralcast_bg,
            artworkResId = R.drawable.neuralcast_art
        ),
        RadioStation(
            id = "neuralforge",
            name = "NeuralForge",
            streamUrl = "https://neuralcast.duckdns.org/listen/neuralforge/radio.mp3",
            backgroundResId = R.drawable.neuralforge_bg,
            artworkResId = R.drawable.neuralforge_art
        )
    )

    private val _uiState = MutableStateFlow(
        UiState(
            stations = stations,
            activeStationId = null,
            playbackStatus = PlaybackStatus.Idle,
            nowPlaying = null,
            errorMessage = null
        )
    )
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var controller: MediaController? = null
    private var controllerFuture: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updatePlaybackState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val stationId = mediaItem?.mediaId?.takeIf { it.isNotBlank() }
            _uiState.update { current ->
                current.copy(activeStationId = stationId, nowPlaying = null)
            }
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val nowPlaying = extractNowPlaying(mediaMetadata)
            if (nowPlaying != null) {
                _uiState.update { current ->
                    current.copy(nowPlaying = nowPlaying)
                }
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _uiState.update { current ->
                current.copy(
                    playbackStatus = PlaybackStatus.Error,
                    errorMessage = error.message ?: "Playback error"
                )
            }
        }
    }

    init {
        connectToSession()
    }

    fun onPlayToggle(station: RadioStation) {
        val mediaController = controller
        if (mediaController == null) {
            _uiState.update { current ->
                current.copy(errorMessage = "Player is not ready yet.")
            }
            return
        }

        val isSameStation = _uiState.value.activeStationId == station.id
        if (isSameStation) {
            stopPlayback(mediaController)
        } else {
            startPlayback(mediaController, station)
        }
    }

    fun onErrorShown() {
        _uiState.update { current ->
            current.copy(errorMessage = null)
        }
    }

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
        controllerFuture?.cancel(true)
        controllerFuture = null
        super.onCleared()
    }

    private fun connectToSession() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future

        future.addListener(
            {
                try {
                    val mediaController = future.get()
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    updatePlaybackState()
                } catch (error: Exception) {
                    _uiState.update { current ->
                        current.copy(errorMessage = "Unable to connect to player.")
                    }
                }
            },
            ContextCompat.getMainExecutor(context)
        )
    }

    private fun startPlayback(mediaController: MediaController, station: RadioStation) {
        val artworkUri = Uri.parse("android.resource://${getApplication<Application>().packageName}/${station.artworkResId}")
        val mediaItem = MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(station.streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setStation(station.name)
                    .setArtworkUri(artworkUri)
                    .build()
            )
            .build()

        mediaController.setMediaItem(mediaItem)
        mediaController.prepare()
        mediaController.play()

        _uiState.update { current ->
            current.copy(
                activeStationId = station.id,
                playbackStatus = PlaybackStatus.Buffering,
                nowPlaying = null,
                errorMessage = null
            )
        }
    }

    private fun stopPlayback(mediaController: MediaController) {
        mediaController.stop()
        mediaController.clearMediaItems()

        _uiState.update { current ->
            current.copy(
                activeStationId = null,
                playbackStatus = PlaybackStatus.Idle,
                nowPlaying = null
            )
        }
    }

    private fun updatePlaybackState() {
        val mediaController = controller ?: return
        val playbackStatus = when (mediaController.playbackState) {
            Player.STATE_BUFFERING -> PlaybackStatus.Buffering
            Player.STATE_READY -> if (mediaController.isPlaying) PlaybackStatus.Playing else PlaybackStatus.Idle
            Player.STATE_IDLE -> PlaybackStatus.Idle
            Player.STATE_ENDED -> PlaybackStatus.Idle
            else -> PlaybackStatus.Idle
        }

        viewModelScope.launch {
            _uiState.update { current ->
                current.copy(playbackStatus = playbackStatus)
            }
        }
    }

    private fun extractNowPlaying(mediaMetadata: MediaMetadata): String? {
        val title = mediaMetadata.title?.toString()?.trim()
        val displayTitle = mediaMetadata.displayTitle?.toString()?.trim()
        val artist = mediaMetadata.artist?.toString()?.trim()
        val stationName = stations.firstOrNull { it.id == _uiState.value.activeStationId }?.name

        val resolvedTitle = when {
            !title.isNullOrBlank() -> title
            !displayTitle.isNullOrBlank() -> displayTitle
            else -> null
        }

        if (!resolvedTitle.isNullOrBlank() && resolvedTitle == stationName) {
            return null
        }

        return when {
            !artist.isNullOrBlank() && !resolvedTitle.isNullOrBlank() -> "$artist - $resolvedTitle"
            !resolvedTitle.isNullOrBlank() -> resolvedTitle
            else -> null
        }
    }
}
