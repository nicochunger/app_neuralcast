package com.neuralcast.radioplayer.model

data class HostAdminOptions(
    val stations: List<String> = emptyList(),
    val archetypes: List<String> = emptyList()
)

data class HostAdminJob(
    val jobId: String,
    val station: String,
    val archetype: String,
    val dryRun: Boolean,
    val status: String,
    val acceptedAt: String? = null,
    val startedAt: String? = null,
    val finishedAt: String? = null,
    val exitCode: Int? = null,
    val logTail: String? = null
)

data class HostAdminConsoleState(
    val baseUrl: String = "",
    val token: String = "",
    val isLoadingOptions: Boolean = false,
    val optionsStatusMessage: String? = null,
    val isOptionsStatusError: Boolean = false,
    val availableStations: List<String> = emptyList(),
    val availableArchetypes: List<String> = emptyList(),
    val selectedStationId: String? = null,
    val selectedArchetype: String? = null,
    val isSubmitting: Boolean = false,
    val activeJob: HostAdminJob? = null,
    val isPollingJob: Boolean = false
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && token.isNotBlank()
}
