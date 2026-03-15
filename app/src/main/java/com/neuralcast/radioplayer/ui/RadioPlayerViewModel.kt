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
import com.neuralcast.radioplayer.data.HostAdminRepository
import com.neuralcast.radioplayer.data.SettingsRepository
import com.neuralcast.radioplayer.data.SongRequestRepository
import com.neuralcast.radioplayer.data.StationProvider
import com.neuralcast.radioplayer.data.StationStatusRepository
import com.neuralcast.radioplayer.model.HostAdminConsoleState
import com.neuralcast.radioplayer.model.HostAdminJob
import com.neuralcast.radioplayer.model.HOST_ADMIN_OPERATION_FORCE_ARCHETYPE
import com.neuralcast.radioplayer.model.HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR
import com.neuralcast.radioplayer.model.HOST_ADMIN_SCHEDULE_SEED_MODE_CUSTOM
import com.neuralcast.radioplayer.model.HOST_ADMIN_SCHEDULE_SEED_MODE_FRESH
import com.neuralcast.radioplayer.model.HostAdminScheduleOptions
import com.neuralcast.radioplayer.model.doesArchetypeSupportTrackFocus
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
    private val hostAdminRepository = HostAdminRepository()
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
    private var hostAdminPollJob: kotlinx.coroutines.Job? = null

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
            settingsRepository.hostAdminConfig.collect { hostConfig ->
                val configChanged = hostConfig.baseUrl != _uiState.value.hostAdminConsole.baseUrl ||
                    hostConfig.token != _uiState.value.hostAdminConsole.token
                if (configChanged) {
                    hostAdminPollJob?.cancel()
                }
                _uiState.update { current ->
                    current.copy(
                        hostAdminConsole = if (configChanged) {
                            HostAdminConsoleState(
                                baseUrl = hostConfig.baseUrl,
                                token = hostConfig.token
                            )
                        } else {
                            current.hostAdminConsole.copy(
                                baseUrl = hostConfig.baseUrl,
                                token = hostConfig.token
                            )
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
        hostAdminPollJob?.cancel()
        viewModelScope.launch {
            settingsRepository.clearAdminSession()
        }
        _uiState.update { current ->
            current.copy(
                isAdminModeEnabled = false,
                isAdminModeAuthenticating = false,
                skippingStationId = null,
                hostAdminConsole = resetHostAdminRuntimeState(current.hostAdminConsole),
                errorMessage = "Admin mode disabled."
            )
        }
    }

    fun saveHostAdminConfig(baseUrl: String, token: String) {
        val normalizedBaseUrl = baseUrl.trim().trimEnd('/')
        val normalizedToken = token.trim()
        if (normalizedBaseUrl.isBlank() && normalizedToken.isBlank()) {
            hostAdminPollJob?.cancel()
            viewModelScope.launch {
                settingsRepository.clearHostAdminConfig()
            }
            _uiState.update { current ->
                current.copy(
                    hostAdminConsole = HostAdminConsoleState(),
                    errorMessage = "Host admin settings cleared."
                )
            }
            return
        }

        if (normalizedBaseUrl.isBlank() || normalizedToken.isBlank()) {
            _uiState.update { current ->
                current.copy(errorMessage = "Enter both the host admin API URL and token.")
            }
            return
        }

        if (!normalizedBaseUrl.startsWith("https://")) {
            _uiState.update { current ->
                current.copy(errorMessage = "Enter a valid HTTPS host admin API URL starting with https://")
            }
            return
        }

        hostAdminPollJob?.cancel()
        viewModelScope.launch {
            settingsRepository.setHostAdminConfig(normalizedBaseUrl, normalizedToken)
        }
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = HostAdminConsoleState(
                    baseUrl = normalizedBaseUrl,
                    token = normalizedToken
                ),
                errorMessage = "Host admin settings saved."
            )
        }
    }

    fun loadHostAdminCapabilities() {
        val hostAdminState = _uiState.value.hostAdminConsole
        if (!hostAdminState.isConfigured) {
            _uiState.update { current ->
                current.copy(errorMessage = "Save the host admin API URL and token first.")
            }
            return
        }
        if (hostAdminState.isLoadingCapabilities) {
            return
        }

        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(
                    isLoadingCapabilities = true,
                    capabilitiesStatusMessage = "Loading admin capabilities...",
                    isCapabilitiesStatusError = false
                )
            )
        }

        viewModelScope.launch {
            runCatching {
                hostAdminRepository.getCapabilities(
                    baseUrl = hostAdminState.baseUrl,
                    token = hostAdminState.token
                )
            }.onSuccess { capabilities ->
                _uiState.update { current ->
                    val selectedStation = resolveSelectedStation(
                        availableStations = capabilities.stations,
                        currentSelection = current.hostAdminConsole.selectedStationId,
                        activeStationId = current.activeStationId
                    )
                    val selectedArchetype = resolveSelectedArchetype(
                        availableArchetypes = capabilities.archetypes,
                        currentSelection = current.hostAdminConsole.selectedArchetype
                    )
                    val scheduleCapability = capabilities.operations[HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR]
                    val supportedSeedModes = scheduleCapability?.supportedSeedModes.orEmpty()
                    val defaultSeedMode = scheduleCapability?.defaultSeedMode
                        ?.takeIf { it in supportedSeedModes }
                        ?: supportedSeedModes.firstOrNull()
                        ?: HOST_ADMIN_SCHEDULE_SEED_MODE_FRESH
                    val selectedScheduleSeedMode = current.hostAdminConsole.selectedScheduleGeneratorSeedMode
                        .takeIf { it in supportedSeedModes }
                        ?.takeIf { it.isNotBlank() }
                        ?: defaultSeedMode
                    current.copy(
                        hostAdminConsole = current.hostAdminConsole.copy(
                            isLoadingCapabilities = false,
                            capabilitiesStatusMessage = "Loaded ${capabilities.stations.size} stations, ${capabilities.archetypes.size} archetypes, and ${capabilities.operations.size} operations.",
                            isCapabilitiesStatusError = false,
                            availableStations = capabilities.stations,
                            availableArchetypes = capabilities.archetypes,
                            availableTrackFocusValues = capabilities.trackFocusValues,
                            trackFocusArchetypes = capabilities.trackFocusArchetypes,
                            operationCapabilities = capabilities.operations,
                            selectedStationId = selectedStation,
                            selectedArchetype = selectedArchetype,
                            selectedTrackFocus = current.hostAdminConsole.selectedTrackFocus
                                ?.takeIf { it in capabilities.trackFocusValues }
                                ?.takeIf {
                                    capabilities.operations[HOST_ADMIN_OPERATION_FORCE_ARCHETYPE]
                                        ?.trackFocusSupported == true &&
                                        doesArchetypeSupportTrackFocus(
                                            selectedArchetype,
                                            capabilities.trackFocusArchetypes
                                        )
                                },
                            scheduleGeneratorForceApply = current.hostAdminConsole.scheduleGeneratorForceApply
                                .takeIf { scheduleCapability?.forceApplySupported == true }
                                ?: false,
                            selectedScheduleGeneratorSeedMode = selectedScheduleSeedMode,
                            scheduleGeneratorSeedSalt = current.hostAdminConsole.scheduleGeneratorSeedSalt
                                .takeIf { selectedScheduleSeedMode == HOST_ADMIN_SCHEDULE_SEED_MODE_CUSTOM }
                                .orEmpty()
                        )
                    )
                }
            }.onFailure { error ->
                _uiState.update { current ->
                    val message = error.message ?: "Unable to load host admin capabilities."
                    current.copy(
                        hostAdminConsole = current.hostAdminConsole.copy(
                            isLoadingCapabilities = false,
                            capabilitiesStatusMessage = message,
                            isCapabilitiesStatusError = true
                        ),
                        errorMessage = message
                    )
                }
            }
        }
    }

    fun selectHostAdminStation(stationId: String) {
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(selectedStationId = stationId)
            )
        }
    }

    fun selectHostAdminArchetype(archetype: String) {
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(
                    selectedArchetype = archetype,
                    selectedTrackFocus = current.hostAdminConsole.selectedTrackFocus
                        ?.takeIf {
                            current.hostAdminConsole.operationCapabilities[HOST_ADMIN_OPERATION_FORCE_ARCHETYPE]
                                ?.trackFocusSupported == true &&
                                doesArchetypeSupportTrackFocus(
                                    archetype,
                                    current.hostAdminConsole.trackFocusArchetypes
                                )
                        }
                )
            )
        }
    }

    fun selectHostAdminTrackFocus(trackFocus: String?) {
        val hostAdminState = _uiState.value.hostAdminConsole
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(
                    selectedTrackFocus = trackFocus?.takeIf {
                        it in hostAdminState.availableTrackFocusValues &&
                            hostAdminState.supportsTrackFocus
                    }
                )
            )
        }
    }

    fun setHostAdminForceArchetypeDryRun(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(forceArchetypeDryRun = enabled)
            )
        }
    }

    fun setHostAdminScheduleGeneratorDryRun(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(
                    scheduleGeneratorDryRun = enabled,
                    scheduleGeneratorForceApply = if (enabled) {
                        false
                    } else {
                        current.hostAdminConsole.scheduleGeneratorForceApply
                    }
                )
            )
        }
    }

    fun setHostAdminScheduleGeneratorForceApply(enabled: Boolean) {
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(
                    scheduleGeneratorForceApply = enabled &&
                        !current.hostAdminConsole.scheduleGeneratorDryRun
                )
            )
        }
    }

    fun selectHostAdminScheduleGeneratorSeedMode(seedMode: String) {
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(
                    selectedScheduleGeneratorSeedMode = seedMode,
                    scheduleGeneratorSeedSalt = current.hostAdminConsole.scheduleGeneratorSeedSalt
                        .takeIf { seedMode == HOST_ADMIN_SCHEDULE_SEED_MODE_CUSTOM }
                        .orEmpty()
                )
            )
        }
    }

    fun setHostAdminScheduleGeneratorSeedSalt(value: String) {
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(scheduleGeneratorSeedSalt = value)
            )
        }
    }

    fun setHostAdminScheduleGeneratorWeekStartDate(value: String) {
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(scheduleGeneratorWeekStartDate = value)
            )
        }
    }

    fun setHostAdminScheduleGeneratorOpenRatioMin(value: String) {
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(scheduleGeneratorOpenRatioMin = value)
            )
        }
    }

    fun setHostAdminScheduleGeneratorOpenRatioMax(value: String) {
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(scheduleGeneratorOpenRatioMax = value)
            )
        }
    }

    fun setHostAdminScheduleGeneratorMinOpenSlots(value: String) {
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(scheduleGeneratorMinOpenSlots = value)
            )
        }
    }

    fun setHostAdminScheduleGeneratorMaxOpenSlots(value: String) {
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(scheduleGeneratorMaxOpenSlots = value)
            )
        }
    }

    fun setHostAdminScheduleGeneratorMinBlockMinutes(value: String) {
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(scheduleGeneratorMinBlockMinutes = value)
            )
        }
    }

    fun setHostAdminScheduleGeneratorMaxBlockMinutes(value: String) {
        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(scheduleGeneratorMaxBlockMinutes = value)
            )
        }
    }

    fun runForcedArchetype() {
        val hostAdminState = _uiState.value.hostAdminConsole
        if (!hostAdminState.isConfigured) {
            _uiState.update { current ->
                current.copy(errorMessage = "Save the host admin API URL and token first.")
            }
            return
        }

        val stationId = hostAdminState.selectedStationId
        val archetype = hostAdminState.selectedArchetype
        if (stationId.isNullOrBlank() || archetype.isNullOrBlank()) {
            _uiState.update { current ->
                current.copy(errorMessage = "Select both a station and an archetype first.")
            }
            return
        }
        if (!hostAdminState.supportsOperation(HOST_ADMIN_OPERATION_FORCE_ARCHETYPE)) {
            _uiState.update { current ->
                current.copy(errorMessage = "Force archetype is not supported by this server.")
            }
            return
        }

        if (hostAdminState.isSubmitting || hostAdminState.isPollingJob) {
            return
        }

        val trackFocus = hostAdminState.selectedTrackFocus
            ?.takeIf { it in hostAdminState.availableTrackFocusValues }
            ?.takeIf {
                hostAdminState.operationCapabilities[HOST_ADMIN_OPERATION_FORCE_ARCHETYPE]
                    ?.trackFocusSupported == true &&
                    doesArchetypeSupportTrackFocus(archetype, hostAdminState.trackFocusArchetypes)
            }
        val dryRun = hostAdminState.forceArchetypeDryRun
            .takeIf { hostAdminState.supportsForceArchetypeDryRun } ?: false

        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(
                    submittingOperation = HOST_ADMIN_OPERATION_FORCE_ARCHETYPE
                )
            )
        }

        viewModelScope.launch {
            runCatching {
                hostAdminRepository.submitForceArchetype(
                    baseUrl = hostAdminState.baseUrl,
                    token = hostAdminState.token,
                    station = stationId,
                    archetype = archetype,
                    trackFocus = trackFocus,
                    dryRun = dryRun
                )
            }.onSuccess { jobId ->
                _uiState.update { current ->
                    current.copy(
                        hostAdminConsole = current.hostAdminConsole.copy(
                            submittingOperation = null,
                            activeJob = HostAdminJob(
                                jobId = jobId,
                                operation = HOST_ADMIN_OPERATION_FORCE_ARCHETYPE,
                                station = stationId,
                                archetype = archetype,
                                trackFocus = trackFocus,
                                dryRun = dryRun,
                                status = "accepted"
                            ),
                            isPollingJob = true
                        ),
                        errorMessage = "Force archetype request accepted."
                    )
                }
                startPollingHostAdminJob(
                    jobId = jobId,
                    baseUrl = hostAdminState.baseUrl,
                    token = hostAdminState.token
                )
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        hostAdminConsole = current.hostAdminConsole.copy(submittingOperation = null),
                        errorMessage = error.message ?: "Unable to start a forced host run."
                    )
                }
            }
        }
    }

    fun runScheduleGenerator() {
        val hostAdminState = _uiState.value.hostAdminConsole
        if (!hostAdminState.isConfigured) {
            _uiState.update { current ->
                current.copy(errorMessage = "Save the host admin API URL and token first.")
            }
            return
        }

        val stationId = hostAdminState.selectedStationId
        if (stationId.isNullOrBlank()) {
            _uiState.update { current ->
                current.copy(errorMessage = "Select a station first.")
            }
            return
        }
        if (!hostAdminState.supportsOperation(HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR)) {
            _uiState.update { current ->
                current.copy(errorMessage = "Schedule generator is not supported by this server.")
            }
            return
        }

        if (hostAdminState.isSubmitting || hostAdminState.isPollingJob) {
            return
        }

        val dryRun = hostAdminState.scheduleGeneratorDryRun
            .takeIf { hostAdminState.supportsScheduleGeneratorDryRun } ?: false
        val forceApply = hostAdminState.scheduleGeneratorForceApply
            .takeIf { hostAdminState.supportsScheduleGeneratorForceApply && !dryRun } ?: false
        val seedMode = hostAdminState.normalizedScheduleGeneratorSeedMode
            .takeIf { hostAdminState.supportedScheduleGeneratorSeedModes.isNotEmpty() }
        val seedSalt = when (seedMode) {
            HOST_ADMIN_SCHEDULE_SEED_MODE_CUSTOM -> hostAdminState.scheduleGeneratorSeedSalt
                .trim()
                .takeIf { it.isNotBlank() }
            else -> null
        }
        if (seedMode == HOST_ADMIN_SCHEDULE_SEED_MODE_CUSTOM && seedSalt == null) {
            _uiState.update { current ->
                current.copy(errorMessage = "Enter a custom seed key first.")
            }
            return
        }

        val weekStartDate = hostAdminState.scheduleGeneratorWeekStartDate
            .trim()
            .takeIf { hostAdminState.supportsScheduleGeneratorWeekStartDate }
            ?.takeIf { it.isNotBlank() }
        val openRatioMin = parseOptionalHostAdminDouble(
            label = "Open ratio min",
            rawValue = hostAdminState.scheduleGeneratorOpenRatioMin,
            enabled = hostAdminState.supportsScheduleGeneratorTuningField("open_ratio_min")
        ) ?: return
        val openRatioMax = parseOptionalHostAdminDouble(
            label = "Open ratio max",
            rawValue = hostAdminState.scheduleGeneratorOpenRatioMax,
            enabled = hostAdminState.supportsScheduleGeneratorTuningField("open_ratio_max")
        ) ?: return
        val minOpenSlots = parseOptionalHostAdminInt(
            label = "Min open slots",
            rawValue = hostAdminState.scheduleGeneratorMinOpenSlots,
            enabled = hostAdminState.supportsScheduleGeneratorTuningField("min_open_slots")
        ) ?: return
        val maxOpenSlots = parseOptionalHostAdminInt(
            label = "Max open slots",
            rawValue = hostAdminState.scheduleGeneratorMaxOpenSlots,
            enabled = hostAdminState.supportsScheduleGeneratorTuningField("max_open_slots")
        ) ?: return
        val minBlockMinutes = parseOptionalHostAdminInt(
            label = "Min block minutes",
            rawValue = hostAdminState.scheduleGeneratorMinBlockMinutes,
            enabled = hostAdminState.supportsScheduleGeneratorTuningField("min_block_minutes")
        ) ?: return
        val maxBlockMinutes = parseOptionalHostAdminInt(
            label = "Max block minutes",
            rawValue = hostAdminState.scheduleGeneratorMaxBlockMinutes,
            enabled = hostAdminState.supportsScheduleGeneratorTuningField("max_block_minutes")
        ) ?: return

        _uiState.update { current ->
            current.copy(
                hostAdminConsole = current.hostAdminConsole.copy(
                    submittingOperation = HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR
                )
            )
        }

        viewModelScope.launch {
            runCatching {
                hostAdminRepository.submitScheduleGenerator(
                    baseUrl = hostAdminState.baseUrl,
                    token = hostAdminState.token,
                    station = stationId,
                    dryRun = dryRun,
                    forceApply = forceApply,
                    seedMode = seedMode,
                    seedSalt = seedSalt,
                    weekStartDate = weekStartDate,
                    openRatioMin = openRatioMin,
                    openRatioMax = openRatioMax,
                    minOpenSlots = minOpenSlots,
                    maxOpenSlots = maxOpenSlots,
                    minBlockMinutes = minBlockMinutes,
                    maxBlockMinutes = maxBlockMinutes
                )
            }.onSuccess { jobId ->
                _uiState.update { current ->
                    current.copy(
                        hostAdminConsole = current.hostAdminConsole.copy(
                            submittingOperation = null,
                            activeJob = HostAdminJob(
                                jobId = jobId,
                                operation = HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR,
                                station = stationId,
                                dryRun = dryRun,
                                scheduleOptions = HostAdminScheduleOptions(
                                    forceApply = forceApply,
                                    seedMode = seedMode,
                                    seedSalt = seedSalt,
                                    weekStartDate = weekStartDate,
                                    openRatioMin = openRatioMin,
                                    openRatioMax = openRatioMax,
                                    minOpenSlots = minOpenSlots,
                                    maxOpenSlots = maxOpenSlots,
                                    minBlockMinutes = minBlockMinutes,
                                    maxBlockMinutes = maxBlockMinutes
                                ),
                                status = "accepted"
                            ),
                            isPollingJob = true
                        ),
                        errorMessage = "Schedule generator request accepted."
                    )
                }
                startPollingHostAdminJob(
                    jobId = jobId,
                    baseUrl = hostAdminState.baseUrl,
                    token = hostAdminState.token
                )
            }.onFailure { error ->
                _uiState.update { current ->
                    current.copy(
                        hostAdminConsole = current.hostAdminConsole.copy(submittingOperation = null),
                        errorMessage = error.message ?: "Unable to start the schedule generator."
                    )
                }
            }
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
        hostAdminPollJob?.cancel()
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

    private fun startPollingHostAdminJob(jobId: String, baseUrl: String, token: String) {
        hostAdminPollJob?.cancel()
        hostAdminPollJob = viewModelScope.launch {
            while (isActive) {
                val result = runCatching {
                    hostAdminRepository.getJobStatus(baseUrl = baseUrl, token = token, jobId = jobId)
                }
                if (result.isFailure) {
                    _uiState.update { current ->
                        current.copy(
                            hostAdminConsole = current.hostAdminConsole.copy(
                                isPollingJob = false,
                                submittingOperation = null
                            ),
                            errorMessage = result.exceptionOrNull()?.message
                                ?: "Unable to refresh host job status."
                        )
                    }
                    break
                }

                val job = result.getOrThrow()
                val isTerminal = isTerminalJobStatus(job.status)
                _uiState.update { current ->
                    current.copy(
                        hostAdminConsole = current.hostAdminConsole.copy(
                            activeJob = job,
                            isPollingJob = !isTerminal
                        )
                    )
                }

                if (isTerminal) {
                    _uiState.update { current ->
                        current.copy(
                            errorMessage = buildHostAdminJobStatusMessage(job)
                        )
                    }
                    break
                }

                kotlinx.coroutines.delay(HOST_ADMIN_POLL_INTERVAL_MS)
            }
        }
    }

    private fun resolveSelectedStation(
        availableStations: List<String>,
        currentSelection: String?,
        activeStationId: String?
    ): String? {
        return when {
            currentSelection != null && currentSelection in availableStations -> currentSelection
            activeStationId != null && activeStationId in availableStations -> activeStationId
            else -> availableStations.firstOrNull()
        }
    }

    private fun resolveSelectedArchetype(
        availableArchetypes: List<String>,
        currentSelection: String?
    ): String? {
        return when {
            currentSelection != null && currentSelection in availableArchetypes -> currentSelection
            else -> availableArchetypes.firstOrNull()
        }
    }

    private fun isTerminalJobStatus(status: String): Boolean {
        return status.equals("succeeded", ignoreCase = true) ||
            status.equals("failed", ignoreCase = true)
    }

    private fun buildHostAdminJobStatusMessage(job: HostAdminJob): String {
        val operationLabel = when (job.operation) {
            HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR -> "Schedule generator"
            else -> "Force archetype"
        }
        return when (job.status.lowercase()) {
            "succeeded" -> "$operationLabel completed for ${job.station}."
            else -> {
                val exitSuffix = job.exitCode?.let { " (exit code $it)" }.orEmpty()
                "$operationLabel failed for ${job.station}$exitSuffix."
            }
        }
    }

    private fun resetHostAdminRuntimeState(state: HostAdminConsoleState): HostAdminConsoleState {
        return state.copy(
            isLoadingCapabilities = false,
            capabilitiesStatusMessage = null,
            isCapabilitiesStatusError = false,
            availableStations = emptyList(),
            availableArchetypes = emptyList(),
            availableTrackFocusValues = emptyList(),
            trackFocusArchetypes = emptySet(),
            operationCapabilities = emptyMap(),
            selectedStationId = null,
            selectedArchetype = null,
            selectedTrackFocus = null,
            forceArchetypeDryRun = false,
            scheduleGeneratorDryRun = false,
            scheduleGeneratorForceApply = false,
            selectedScheduleGeneratorSeedMode = HOST_ADMIN_SCHEDULE_SEED_MODE_FRESH,
            scheduleGeneratorSeedSalt = "",
            scheduleGeneratorWeekStartDate = "",
            scheduleGeneratorOpenRatioMin = "",
            scheduleGeneratorOpenRatioMax = "",
            scheduleGeneratorMinOpenSlots = "",
            scheduleGeneratorMaxOpenSlots = "",
            scheduleGeneratorMinBlockMinutes = "",
            scheduleGeneratorMaxBlockMinutes = "",
            submittingOperation = null,
            activeJob = null,
            isPollingJob = false
        )
    }

    private fun parseOptionalHostAdminDouble(
        label: String,
        rawValue: String,
        enabled: Boolean
    ): Double? {
        if (!enabled) return null
        val normalized = rawValue.trim()
        if (normalized.isBlank()) return null
        return normalized.toDoubleOrNull() ?: run {
            _uiState.update { current ->
                current.copy(errorMessage = "$label must be a number.")
            }
            null
        }
    }

    private fun parseOptionalHostAdminInt(
        label: String,
        rawValue: String,
        enabled: Boolean
    ): Int? {
        if (!enabled) return null
        val normalized = rawValue.trim()
        if (normalized.isBlank()) return null
        return normalized.toIntOrNull() ?: run {
            _uiState.update { current ->
                current.copy(errorMessage = "$label must be a whole number.")
            }
            null
        }
    }

    private companion object {
        private const val LISTENER_REFRESH_INTERVAL_MS = 3 * 60 * 1000L
        private const val HOST_ADMIN_POLL_INTERVAL_MS = 3_000L
    }
}
