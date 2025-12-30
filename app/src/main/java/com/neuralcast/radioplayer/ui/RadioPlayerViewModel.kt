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

import com.neuralcast.radioplayer.data.SettingsRepository
import com.neuralcast.radioplayer.data.StationProvider
import com.neuralcast.radioplayer.model.AppTheme
import com.neuralcast.radioplayer.model.BufferSize

class RadioPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val settingsRepository = SettingsRepository(application)
    private val stations = StationProvider.stations

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

    private var sleepTimerJob: kotlinx.coroutines.Job? = null

    init {
        connectToSession()
        viewModelScope.launch {
            settingsRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(appPreferences = prefs) }
                // Apply volume if not set by user yet? Or just respect what the slider says.
                // The slider is bound to uiState.volume.
                // If the player is not connected, we might want to initialize volume from prefs.
                if (controller == null) {
                     _uiState.update { it.copy(volume = prefs.defaultVolume) }
                }
            }
        }
    }

    fun saveTheme(theme: AppTheme) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun saveBufferSize(size: BufferSize) {
        viewModelScope.launch { settingsRepository.setBufferSize(size) }
    }

    fun saveDefaultVolume(volume: Float) {
        viewModelScope.launch { settingsRepository.setDefaultVolume(volume) }
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
    
    fun setVolume(volume: Float) {
        controller?.volume = volume
        _uiState.update { it.copy(volume = volume) }
    }

    fun setSleepTimer(minutes: Int?) {
        sleepTimerJob?.cancel()
        
        if (minutes == null) {
            _uiState.update { it.copy(sleepTimerRemaining = null) }
            return
        }

        val totalMillis = minutes * 60 * 1000L
        val startTime = System.currentTimeMillis()
        
        sleepTimerJob = viewModelScope.launch {
            _uiState.update { it.copy(sleepTimerRemaining = totalMillis) }
            while (System.currentTimeMillis() - startTime < totalMillis) {
                kotlinx.coroutines.delay(1000L) // Update every second
                val remaining = totalMillis - (System.currentTimeMillis() - startTime)
                _uiState.update { it.copy(sleepTimerRemaining = remaining) }
            }
            // Timer finished
            controller?.let { stopPlayback(it) }
            _uiState.update { it.copy(sleepTimerRemaining = null) }
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
                    // Sync initial volume
                     _uiState.update { it.copy(volume = mediaController.volume) }
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
                    .setSubtitle(station.name)
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
        setSleepTimer(null) // Cancel timer on manual stop

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

        val currentTrack = when {
            !artist.isNullOrBlank() && !resolvedTitle.isNullOrBlank() -> "$artist - $resolvedTitle"
            !resolvedTitle.isNullOrBlank() -> resolvedTitle
            else -> null
        }
        
        // Update history if track changed
        if (currentTrack != null && currentTrack != _uiState.value.nowPlaying) {
             _uiState.update { current ->
                 val newHistory = (listOf(currentTrack) + current.recentlyPlayed)
                     .distinct()
                     .take(5)
                 current.copy(recentlyPlayed = newHistory)
             }
        }

        return currentTrack
    }
}
