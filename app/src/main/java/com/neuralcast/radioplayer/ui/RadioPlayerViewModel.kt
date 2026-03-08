package com.neuralcast.radioplayer.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.neuralcast.radioplayer.data.AdminRepository
import com.neuralcast.radioplayer.data.SettingsRepository
import com.neuralcast.radioplayer.data.SongRequestRepository
import com.neuralcast.radioplayer.data.StationProvider
import com.neuralcast.radioplayer.data.StationStatusRepository
import com.neuralcast.radioplayer.model.PlaybackStatus
import com.neuralcast.radioplayer.model.RequestableSong
import com.neuralcast.radioplayer.model.RadioStation
import com.neuralcast.radioplayer.model.SongRequestState
import com.neuralcast.radioplayer.model.UiState
import com.neuralcast.radioplayer.playback.PlaybackService
import com.neuralcast.radioplayer.model.AppTheme
import com.neuralcast.radioplayer.model.PlaybackHistoryEntry
import com.neuralcast.radioplayer.playback.PlaybackConstants
import com.neuralcast.radioplayer.util.MetadataHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class RadioPlayerViewModel(application: Application) : AndroidViewModel(application) {
    private val adminRepository = AdminRepository()
    private val stationStatusRepository = StationStatusRepository()
    private val settingsRepository = SettingsRepository(application)
    private val songRequestRepository = SongRequestRepository()
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
    private var adminApiKey: String? = null

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
            persistActiveStationId(stationId)
        }

        override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
            val stationName = stations.firstOrNull { it.id == _uiState.value.activeStationId }?.name
            val nowPlaying = MetadataHelper.extractNowPlaying(mediaMetadata, stationName)
            if (nowPlaying != null) updateNowPlaying(nowPlaying)
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            _uiState.update { current ->
                current.copy(
                    playbackStatus = PlaybackStatus.Error,
                    errorMessage = error.message ?: "Playback error"
                )
            }
        }

        override fun onMetadata(metadata: Metadata) {
            val stationName = stations.firstOrNull { it.id == _uiState.value.activeStationId }?.name
            val nowPlaying = MetadataHelper.extractNowPlaying(metadata, stationName)
            if (nowPlaying != null) updateNowPlaying(nowPlaying)
        }
    }
    private val controllerListener = object : MediaController.Listener {
        override fun onExtrasChanged(controller: MediaController, extras: android.os.Bundle) {
            extras.getString(PlaybackConstants.EXTRA_NOW_PLAYING)?.let(::updateNowPlaying)
        }
    }

    private var sleepTimerJob: kotlinx.coroutines.Job? = null
    private var listenerRefreshJob: kotlinx.coroutines.Job? = null

    init {
        connectToSession()
        startListenerCountUpdates()
        viewModelScope.launch {
            settingsRepository.preferences.collect { prefs ->
                _uiState.update { it.copy(appPreferences = prefs) }
            }
        }
        viewModelScope.launch {
            settingsRepository.adminSession.collect { adminSession ->
                val persistedApiKey = adminSession.apiKey?.takeIf { it.isNotBlank() }
                adminApiKey = persistedApiKey
                val isAdminModeEnabled = adminSession.isAdminModeEnabled && persistedApiKey != null
                _uiState.update { current ->
                    current.copy(
                        isAdminModeEnabled = isAdminModeEnabled,
                        skippingStationId = if (isAdminModeEnabled) {
                            current.skippingStationId
                        } else {
                            null
                        }
                    )
                }
            }
        }
        viewModelScope.launch {
            settingsRepository.playbackState.collect { snapshot ->
                val activeStationId = snapshot.activeStationId
                    ?.takeIf { stationId -> stations.any { it.id == stationId } }
                _uiState.update { current ->
                    val resolvedActiveStationId = if (current.activeStationId == null) {
                        activeStationId
                    } else {
                        current.activeStationId
                    }
                    current.copy(
                        activeStationId = resolvedActiveStationId,
                        recentlyPlayed = snapshot.recentlyPlayed
                    )
                }
            }
        }
    }

    fun saveTheme(theme: AppTheme) {
        viewModelScope.launch { settingsRepository.setTheme(theme) }
    }

    fun enableAdminMode(apiKey: String) {
        val normalizedApiKey = apiKey.trim()
        if (normalizedApiKey.isBlank()) {
            _uiState.update { current ->
                current.copy(errorMessage = "Enter your admin API key to enable admin mode.")
            }
            return
        }

        if (_uiState.value.isAdminModeAuthenticating) {
            return
        }

        val validationStation = stations.firstOrNull()
        if (validationStation == null) {
            _uiState.update { current ->
                current.copy(errorMessage = "No stations available to validate admin credentials.")
            }
            return
        }

        _uiState.update { current ->
            current.copy(isAdminModeAuthenticating = true)
        }

        viewModelScope.launch {
            runCatching {
                adminRepository.validateAdminApiKey(validationStation, normalizedApiKey)
                settingsRepository.setAdminSession(normalizedApiKey)
            }.onSuccess {
                adminApiKey = normalizedApiKey
                _uiState.update { current ->
                    current.copy(
                        isAdminModeEnabled = true,
                        isAdminModeAuthenticating = false,
                        errorMessage = "Admin mode enabled."
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        isAdminModeEnabled = false,
                        isAdminModeAuthenticating = false,
                        errorMessage = error.message
                            ?: "Invalid admin API key or insufficient permissions."
                    )
                }
            }
        }
    }

    fun disableAdminMode() {
        adminApiKey = null
        viewModelScope.launch {
            settingsRepository.clearAdminSession()
        }
        _uiState.update { current ->
            current.copy(
                isAdminModeEnabled = false,
                isAdminModeAuthenticating = false,
                skippingStationId = null,
                errorMessage = "Admin mode disabled."
            )
        }
    }

    fun onSkipTrack(station: RadioStation) {
        val currentState = _uiState.value
        if (!currentState.isAdminModeEnabled) {
            _uiState.update { current ->
                current.copy(errorMessage = "Enable admin mode in Settings to skip tracks.")
            }
            return
        }

        val apiKey = adminApiKey
        if (apiKey.isNullOrBlank()) {
            viewModelScope.launch {
                settingsRepository.clearAdminSession()
            }
            _uiState.update { current ->
                current.copy(
                    isAdminModeEnabled = false,
                    errorMessage = "Admin session expired. Re-enter your admin API key."
                )
            }
            return
        }

        if (currentState.skippingStationId != null) {
            return
        }

        _uiState.update { current ->
            current.copy(skippingStationId = station.id)
        }

        viewModelScope.launch {
            runCatching {
                adminRepository.skipCurrentTrack(station, apiKey)
            }.onSuccess { message ->
                refreshStreamAfterSkip(station)
                _uiState.update { current ->
                    current.copy(
                        skippingStationId = null,
                        errorMessage = message
                    )
                }
            }.onFailure { error ->
                val shouldResetAdminMode = isAuthenticationError(error.message)
                if (shouldResetAdminMode) {
                    adminApiKey = null
                    settingsRepository.clearAdminSession()
                }
                _uiState.update { current ->
                    current.copy(
                        skippingStationId = null,
                        isAdminModeEnabled = if (shouldResetAdminMode) false else current.isAdminModeEnabled,
                        errorMessage = if (shouldResetAdminMode) {
                            "Admin session expired. Re-enter your admin API key."
                        } else {
                            error.message ?: "Unable to skip the current track."
                        }
                    )
                }
            }
        }
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

    fun onSongRequestClick(station: RadioStation) {
        _uiState.update { current ->
            current.copy(
                songRequestState = SongRequestState(
                    stationId = station.id,
                    stationName = station.name,
                    isLoading = true
                )
            )
        }

        viewModelScope.launch {
            runCatching {
                songRequestRepository.getRequestableSongs(station)
            }.onSuccess { songs ->
                _uiState.update { current ->
                    if (current.songRequestState.stationId != station.id) {
                        current
                    } else {
                        current.copy(
                            songRequestState = current.songRequestState.copy(
                                isLoading = false,
                                songs = songs
                            )
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    if (current.songRequestState.stationId != station.id) {
                        current
                    } else {
                        current.copy(
                            songRequestState = current.songRequestState.copy(
                                isLoading = false,
                                songs = emptyList(),
                                submittingRequestId = null
                            ),
                            errorMessage = error.message ?: "Unable to load requestable songs."
                        )
                    }
                }
            }
        }
    }

    fun onSongRequestSubmit(song: RequestableSong) {
        val requestState = _uiState.value.songRequestState
        if (requestState.submittingRequestId != null) {
            return
        }

        val station = stations.firstOrNull { it.id == requestState.stationId }
        if (station == null) {
            _uiState.update { current ->
                current.copy(errorMessage = "Station not found for song request.")
            }
            return
        }

        _uiState.update { current ->
            current.copy(
                songRequestState = current.songRequestState.copy(
                    submittingRequestId = song.requestId
                )
            )
        }

        viewModelScope.launch {
            runCatching {
                songRequestRepository.submitSongRequest(station, song)
            }.onSuccess { message ->
                _uiState.update { current ->
                    current.copy(
                        songRequestState = SongRequestState(),
                        errorMessage = message
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        songRequestState = current.songRequestState.copy(submittingRequestId = null),
                        errorMessage = error.message ?: "Unable to submit song request."
                    )
                }
            }
        }
    }

    fun onSongRequestDismiss() {
        _uiState.update { current ->
            current.copy(songRequestState = SongRequestState())
        }
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
        listenerRefreshJob?.cancel()
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
        val future = MediaController.Builder(context, sessionToken)
            .setListener(controllerListener)
            .buildAsync()
        controllerFuture = future

        future.addListener(
            {
                try {
                    val mediaController = future.get()
                    controller = mediaController
                    mediaController.addListener(playerListener)
                    mediaController.sessionExtras.getString(PlaybackConstants.EXTRA_NOW_PLAYING)
                        ?.let(::updateNowPlaying)
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
        persistActiveStationId(station.id)
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
        persistActiveStationId(null)
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

    private fun updateNowPlaying(nowPlaying: String) {
        val timestamp = System.currentTimeMillis()
        _uiState.update { current ->
            val newHistory = if (nowPlaying != current.nowPlaying) {
                val newEntry = PlaybackHistoryEntry(track = nowPlaying, playedAt = timestamp)
                (listOf(newEntry) + current.recentlyPlayed.filterNot { it.track == nowPlaying })
                    .take(5)
            } else {
                current.recentlyPlayed
            }
            current.copy(nowPlaying = nowPlaying, recentlyPlayed = newHistory)
        }
        persistRecentlyPlayed()
    }

    private fun persistActiveStationId(activeStationId: String?) {
        viewModelScope.launch {
            settingsRepository.setActiveStationId(activeStationId)
        }
    }

    private fun persistRecentlyPlayed() {
        val history = _uiState.value.recentlyPlayed
        viewModelScope.launch {
            settingsRepository.setRecentlyPlayed(history)
        }
    }

    private fun startListenerCountUpdates() {
        listenerRefreshJob?.cancel()
        listenerRefreshJob = viewModelScope.launch {
            while (isActive) {
                refreshListenerCounts()
                kotlinx.coroutines.delay(LISTENER_REFRESH_INTERVAL_MS)
            }
        }
    }

    private suspend fun refreshListenerCounts() {
        val updatedCounts = _uiState.value.listenerCounts.toMutableMap()
        stations.forEach { station ->
            runCatching {
                stationStatusRepository.getCurrentListeners(station)
            }.onSuccess { listenerCount ->
                updatedCounts[station.id] = listenerCount
            }
        }
        _uiState.update { current ->
            current.copy(listenerCounts = updatedCounts)
        }
    }

    private fun refreshStreamAfterSkip(station: RadioStation) {
        val mediaController = controller ?: return
        val isActiveStation = _uiState.value.activeStationId == station.id
        if (!isActiveStation) return
        if (mediaController.playbackState != Player.STATE_READY || !mediaController.isPlaying) return

        // Reconnect to the same stream so buffered audio doesn't mask a successful server-side skip.
        startPlayback(mediaController, station)
    }

    private fun isAuthenticationError(message: String?): Boolean {
        if (message.isNullOrBlank()) return false
        val normalized = message.lowercase()
        return normalized.contains("unauthorized") ||
            normalized.contains("forbidden") ||
            normalized.contains("invalid api key") ||
            normalized.contains("access denied")
    }

    private companion object {
        private const val LISTENER_REFRESH_INTERVAL_MS = 3 * 60 * 1000L
    }
}
