package com.neuralcast.radioplayer.model

const val HOST_ADMIN_OPERATION_FORCE_ARCHETYPE = "force_archetype"
const val HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR = "schedule_generator"
const val HOST_ADMIN_SCHEDULE_SEED_MODE_STABLE_WEEK = "stable_week"
const val HOST_ADMIN_SCHEDULE_SEED_MODE_FRESH = "fresh"
const val HOST_ADMIN_SCHEDULE_SEED_MODE_CUSTOM = "custom"

data class HostAdminOperationCapability(
    val dryRunSupported: Boolean = false,
    val trackFocusSupported: Boolean = false,
    val forceApplySupported: Boolean = false,
    val weekStartDateSupported: Boolean = false,
    val supportedSeedModes: List<String> = emptyList(),
    val defaultSeedMode: String? = null,
    val supportedTuningFields: Set<String> = emptySet()
)

data class HostAdminCapabilities(
    val stations: List<String> = emptyList(),
    val archetypes: List<String> = emptyList(),
    val trackFocusValues: List<String> = emptyList(),
    val trackFocusArchetypes: Set<String> = emptySet(),
    val operations: Map<String, HostAdminOperationCapability> = emptyMap()
)

data class HostAdminScheduleOptions(
    val forceApply: Boolean = false,
    val seedMode: String? = null,
    val seedSalt: String? = null,
    val weekStartDate: String? = null,
    val openRatioMin: Double? = null,
    val openRatioMax: Double? = null,
    val minOpenSlots: Int? = null,
    val maxOpenSlots: Int? = null,
    val minBlockMinutes: Int? = null,
    val maxBlockMinutes: Int? = null
)

data class HostAdminJob(
    val jobId: String,
    val operation: String,
    val station: String,
    val archetype: String? = null,
    val trackFocus: String? = null,
    val dryRun: Boolean,
    val scheduleOptions: HostAdminScheduleOptions? = null,
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
    val isLoadingCapabilities: Boolean = false,
    val capabilitiesStatusMessage: String? = null,
    val isCapabilitiesStatusError: Boolean = false,
    val availableStations: List<String> = emptyList(),
    val availableArchetypes: List<String> = emptyList(),
    val availableTrackFocusValues: List<String> = emptyList(),
    val trackFocusArchetypes: Set<String> = emptySet(),
    val operationCapabilities: Map<String, HostAdminOperationCapability> = emptyMap(),
    val selectedStationId: String? = null,
    val selectedArchetype: String? = null,
    val selectedTrackFocus: String? = null,
    val forceArchetypeDryRun: Boolean = false,
    val scheduleGeneratorDryRun: Boolean = false,
    val scheduleGeneratorForceApply: Boolean = false,
    val selectedScheduleGeneratorSeedMode: String = HOST_ADMIN_SCHEDULE_SEED_MODE_FRESH,
    val scheduleGeneratorSeedSalt: String = "",
    val scheduleGeneratorWeekStartDate: String = "",
    val scheduleGeneratorOpenRatioMin: String = "",
    val scheduleGeneratorOpenRatioMax: String = "",
    val scheduleGeneratorMinOpenSlots: String = "",
    val scheduleGeneratorMaxOpenSlots: String = "",
    val scheduleGeneratorMinBlockMinutes: String = "",
    val scheduleGeneratorMaxBlockMinutes: String = "",
    val submittingOperation: String? = null,
    val activeJob: HostAdminJob? = null,
    val isPollingJob: Boolean = false
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && token.isNotBlank()

    val isSubmitting: Boolean
        get() = submittingOperation != null

    val supportsTrackFocus: Boolean
        get() = operationCapabilities[HOST_ADMIN_OPERATION_FORCE_ARCHETYPE]?.trackFocusSupported == true &&
            doesArchetypeSupportTrackFocus(selectedArchetype, trackFocusArchetypes)

    val supportsForceArchetypeDryRun: Boolean
        get() = operationCapabilities[HOST_ADMIN_OPERATION_FORCE_ARCHETYPE]?.dryRunSupported == true

    val supportsScheduleGeneratorDryRun: Boolean
        get() = operationCapabilities[HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR]?.dryRunSupported == true

    val supportsScheduleGeneratorForceApply: Boolean
        get() = operationCapabilities[HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR]?.forceApplySupported == true

    val supportsScheduleGeneratorWeekStartDate: Boolean
        get() = operationCapabilities[HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR]?.weekStartDateSupported == true

    val supportedScheduleGeneratorSeedModes: List<String>
        get() = operationCapabilities[HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR]
            ?.supportedSeedModes
            .orEmpty()

    val defaultScheduleGeneratorSeedMode: String
        get() = operationCapabilities[HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR]
            ?.defaultSeedMode
            ?.takeIf { it.isNotBlank() }
            ?: supportedScheduleGeneratorSeedModes.firstOrNull()
            ?: HOST_ADMIN_SCHEDULE_SEED_MODE_FRESH

    val normalizedScheduleGeneratorSeedMode: String
        get() = selectedScheduleGeneratorSeedMode
            .takeIf { it in supportedScheduleGeneratorSeedModes }
            ?.takeIf { it.isNotBlank() }
            ?: defaultScheduleGeneratorSeedMode

    fun supportsOperation(operation: String): Boolean {
        return operationCapabilities.containsKey(operation)
    }

    fun isSubmitting(operation: String): Boolean {
        return submittingOperation == operation
    }

    fun supportsScheduleGeneratorTuningField(field: String): Boolean {
        return field in (operationCapabilities[HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR]
            ?.supportedTuningFields
            ?: emptySet())
    }
}

fun doesArchetypeSupportTrackFocus(
    archetype: String?,
    supportedArchetypes: Collection<String>
): Boolean {
    return archetype != null && archetype in supportedArchetypes
}
