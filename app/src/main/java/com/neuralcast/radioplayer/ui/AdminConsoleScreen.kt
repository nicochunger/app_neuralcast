package com.neuralcast.radioplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.neuralcast.radioplayer.data.StationProvider
import com.neuralcast.radioplayer.model.HostAdminConsoleState
import com.neuralcast.radioplayer.model.HostAdminJob
import com.neuralcast.radioplayer.model.HOST_ADMIN_OPERATION_FORCE_ARCHETYPE
import com.neuralcast.radioplayer.model.HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR
import com.neuralcast.radioplayer.model.HOST_ADMIN_SCHEDULE_SEED_MODE_CUSTOM

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConsoleScreen(
    hostAdminState: HostAdminConsoleState,
    onNavigateBack: () -> Unit,
    onRefreshCapabilities: () -> Unit,
    onStationSelected: (String) -> Unit,
    onArchetypeSelected: (String) -> Unit,
    onTrackFocusSelected: (String?) -> Unit,
    onForceArchetypeDryRunChanged: (Boolean) -> Unit,
    onScheduleGeneratorDryRunChanged: (Boolean) -> Unit,
    onScheduleGeneratorForceApplyChanged: (Boolean) -> Unit,
    onScheduleGeneratorSeedModeSelected: (String) -> Unit,
    onScheduleGeneratorSeedSaltChanged: (String) -> Unit,
    onScheduleGeneratorWeekStartDateChanged: (String) -> Unit,
    onScheduleGeneratorOpenRatioMinChanged: (String) -> Unit,
    onScheduleGeneratorOpenRatioMaxChanged: (String) -> Unit,
    onScheduleGeneratorMinOpenSlotsChanged: (String) -> Unit,
    onScheduleGeneratorMaxOpenSlotsChanged: (String) -> Unit,
    onScheduleGeneratorMinBlockMinutesChanged: (String) -> Unit,
    onScheduleGeneratorMaxBlockMinutesChanged: (String) -> Unit,
    onRunForcedArchetype: () -> Unit,
    onRunScheduleGenerator: () -> Unit
) {
    var archetypeMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(hostAdminState.baseUrl, hostAdminState.token) {
        if (hostAdminState.isConfigured &&
            hostAdminState.operationCapabilities.isEmpty() &&
            !hostAdminState.isLoadingCapabilities
        ) {
            onRefreshCapabilities()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Admin Console") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Host Admin",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = if (hostAdminState.isConfigured) {
                            "Connected to ${hostAdminState.baseUrl}"
                        } else {
                            "Save the host admin API URL and token in Settings before using this console."
                        },
                        style = MaterialTheme.typography.bodyMedium
                    )
                    if (hostAdminState.isConfigured) {
                        OutlinedButton(
                            onClick = onRefreshCapabilities,
                            enabled = !hostAdminState.isLoadingCapabilities
                        ) {
                            if (hostAdminState.isLoadingCapabilities) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Refresh capabilities"
                                )
                            }
                            Text(
                                text = "Refresh Capabilities",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        val capabilitiesMessage = hostAdminState.capabilitiesStatusMessage
                        if (capabilitiesMessage != null) {
                            Text(
                                text = capabilitiesMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hostAdminState.isCapabilitiesStatusError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        } else {
                            Text(
                                text = "Refresh capabilities to load the live admin features from the VPS.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (hostAdminState.isConfigured) {
                if (hostAdminState.availableStations.isNotEmpty()) {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Station",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                hostAdminState.availableStations.forEach { stationId ->
                                    FilterChip(
                                        selected = stationId == hostAdminState.selectedStationId,
                                        onClick = { onStationSelected(stationId) },
                                        label = { Text(stationLabel(stationId)) },
                                        leadingIcon = if (stationId == hostAdminState.selectedStationId) {
                                            {
                                                Icon(
                                                    imageVector = Icons.Default.Radio,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        } else {
                                            null
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (hostAdminState.supportsOperation(HOST_ADMIN_OPERATION_FORCE_ARCHETYPE)) {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Force Archetype",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (hostAdminState.availableArchetypes.isEmpty() &&
                                !hostAdminState.isLoadingCapabilities
                            ) {
                                Text(
                                    text = "No archetypes are loaded yet. Refresh capabilities to fetch them from the server.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            ExposedDropdownMenuBox(
                                expanded = archetypeMenuExpanded,
                                onExpandedChange = {
                                    if (hostAdminState.availableArchetypes.isNotEmpty()) {
                                        archetypeMenuExpanded = !archetypeMenuExpanded
                                    }
                                }
                            ) {
                                OutlinedTextField(
                                    value = hostAdminState.selectedArchetype?.toArchetypeLabel().orEmpty(),
                                    onValueChange = {},
                                    modifier = Modifier
                                        .menuAnchor()
                                        .fillMaxWidth(),
                                    readOnly = true,
                                    enabled = hostAdminState.availableArchetypes.isNotEmpty(),
                                    label = { Text("Archetype") },
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = archetypeMenuExpanded)
                                    }
                                )
                                DropdownMenu(
                                    expanded = archetypeMenuExpanded,
                                    onDismissRequest = { archetypeMenuExpanded = false }
                                ) {
                                    hostAdminState.availableArchetypes.forEach { archetype ->
                                        DropdownMenuItem(
                                            text = { Text(archetype.toArchetypeLabel()) },
                                            onClick = {
                                                onArchetypeSelected(archetype)
                                                archetypeMenuExpanded = false
                                            },
                                            contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                        )
                                    }
                                }
                            }
                            if (hostAdminState.supportsForceArchetypeDryRun) {
                                FocusChip(
                                    label = "Dry run",
                                    selected = hostAdminState.forceArchetypeDryRun,
                                    onClick = {
                                        onForceArchetypeDryRunChanged(!hostAdminState.forceArchetypeDryRun)
                                    }
                                )
                            }
                            if (hostAdminState.supportsTrackFocus) {
                                Text(
                                    text = "Track focus",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Optional. Leave it unset to let the server pick the focus automatically.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    hostAdminState.availableTrackFocusValues.forEach { trackFocusValue ->
                                        FocusChip(
                                            label = trackFocusValue.toTrackFocusLabel(),
                                            selected = hostAdminState.selectedTrackFocus == trackFocusValue,
                                            onClick = { onTrackFocusSelected(trackFocusValue) }
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { onTrackFocusSelected(null) },
                                    enabled = hostAdminState.selectedTrackFocus != null
                                ) {
                                    Text("Use server default")
                                }
                            }
                            Button(
                                onClick = onRunForcedArchetype,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = hostAdminState.selectedStationId != null &&
                                    hostAdminState.selectedArchetype != null &&
                                    !hostAdminState.isLoadingCapabilities &&
                                    !hostAdminState.isSubmitting &&
                                    !hostAdminState.isPollingJob
                            ) {
                                if (hostAdminState.isSubmitting(HOST_ADMIN_OPERATION_FORCE_ARCHETYPE)) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null
                                    )
                                }
                                Text(
                                    text = when {
                                        hostAdminState.isSubmitting(HOST_ADMIN_OPERATION_FORCE_ARCHETYPE) -> "Submitting..."
                                        hostAdminState.isPollingJob &&
                                            hostAdminState.activeJob?.operation == HOST_ADMIN_OPERATION_FORCE_ARCHETYPE -> "Job Running..."
                                        else -> "Run Force Archetype"
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }

                if (hostAdminState.supportsOperation(HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR)) {
                    Card {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "Schedule Generator",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Run the schedule generator for the selected station.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (hostAdminState.supportsScheduleGeneratorDryRun ||
                                hostAdminState.supportsScheduleGeneratorForceApply
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (hostAdminState.supportsScheduleGeneratorDryRun) {
                                        FocusChip(
                                            label = "Dry run",
                                            selected = hostAdminState.scheduleGeneratorDryRun,
                                            onClick = {
                                                onScheduleGeneratorDryRunChanged(!hostAdminState.scheduleGeneratorDryRun)
                                            }
                                        )
                                    }
                                    if (hostAdminState.supportsScheduleGeneratorForceApply) {
                                        FocusChip(
                                            label = "Force apply",
                                            selected = hostAdminState.scheduleGeneratorForceApply,
                                            enabled = !hostAdminState.scheduleGeneratorDryRun,
                                            onClick = {
                                                onScheduleGeneratorForceApplyChanged(
                                                    !hostAdminState.scheduleGeneratorForceApply
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                            if (hostAdminState.supportedScheduleGeneratorSeedModes.isNotEmpty()) {
                                Text(
                                    text = "Seed mode",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Use stable mode for deterministic weekly plans, fresh for a reroll, or custom to reproduce a manual variation.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    hostAdminState.supportedScheduleGeneratorSeedModes.forEach { seedMode ->
                                        FocusChip(
                                            label = seedMode.toSeedModeLabel(),
                                            selected = hostAdminState.normalizedScheduleGeneratorSeedMode == seedMode,
                                            onClick = { onScheduleGeneratorSeedModeSelected(seedMode) }
                                        )
                                    }
                                }
                            }
                            if (hostAdminState.normalizedScheduleGeneratorSeedMode ==
                                HOST_ADMIN_SCHEDULE_SEED_MODE_CUSTOM
                            ) {
                                OutlinedTextField(
                                    value = hostAdminState.scheduleGeneratorSeedSalt,
                                    onValueChange = onScheduleGeneratorSeedSaltChanged,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Custom seed key") }
                                )
                            }
                            if (hostAdminState.supportsScheduleGeneratorWeekStartDate) {
                                OutlinedTextField(
                                    value = hostAdminState.scheduleGeneratorWeekStartDate,
                                    onValueChange = onScheduleGeneratorWeekStartDateChanged,
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    label = { Text("Week start date (YYYY-MM-DD)") }
                                )
                            }
                            if (hostAdminState.supportsScheduleGeneratorTuningField("open_ratio_min") ||
                                hostAdminState.supportsScheduleGeneratorTuningField("open_ratio_max") ||
                                hostAdminState.supportsScheduleGeneratorTuningField("min_open_slots") ||
                                hostAdminState.supportsScheduleGeneratorTuningField("max_open_slots") ||
                                hostAdminState.supportsScheduleGeneratorTuningField("min_block_minutes") ||
                                hostAdminState.supportsScheduleGeneratorTuningField("max_block_minutes")
                            ) {
                                Text(
                                    text = "Tuning",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Leave fields blank to use the server defaults.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (hostAdminState.supportsScheduleGeneratorTuningField("open_ratio_min")) {
                                NumericOptionField(
                                    value = hostAdminState.scheduleGeneratorOpenRatioMin,
                                    onValueChange = onScheduleGeneratorOpenRatioMinChanged,
                                    label = "Open ratio min",
                                    keyboardType = KeyboardType.Decimal
                                )
                            }
                            if (hostAdminState.supportsScheduleGeneratorTuningField("open_ratio_max")) {
                                NumericOptionField(
                                    value = hostAdminState.scheduleGeneratorOpenRatioMax,
                                    onValueChange = onScheduleGeneratorOpenRatioMaxChanged,
                                    label = "Open ratio max",
                                    keyboardType = KeyboardType.Decimal
                                )
                            }
                            if (hostAdminState.supportsScheduleGeneratorTuningField("min_open_slots")) {
                                NumericOptionField(
                                    value = hostAdminState.scheduleGeneratorMinOpenSlots,
                                    onValueChange = onScheduleGeneratorMinOpenSlotsChanged,
                                    label = "Min open slots",
                                    keyboardType = KeyboardType.Number
                                )
                            }
                            if (hostAdminState.supportsScheduleGeneratorTuningField("max_open_slots")) {
                                NumericOptionField(
                                    value = hostAdminState.scheduleGeneratorMaxOpenSlots,
                                    onValueChange = onScheduleGeneratorMaxOpenSlotsChanged,
                                    label = "Max open slots",
                                    keyboardType = KeyboardType.Number
                                )
                            }
                            if (hostAdminState.supportsScheduleGeneratorTuningField("min_block_minutes")) {
                                NumericOptionField(
                                    value = hostAdminState.scheduleGeneratorMinBlockMinutes,
                                    onValueChange = onScheduleGeneratorMinBlockMinutesChanged,
                                    label = "Min block minutes",
                                    keyboardType = KeyboardType.Number
                                )
                            }
                            if (hostAdminState.supportsScheduleGeneratorTuningField("max_block_minutes")) {
                                NumericOptionField(
                                    value = hostAdminState.scheduleGeneratorMaxBlockMinutes,
                                    onValueChange = onScheduleGeneratorMaxBlockMinutesChanged,
                                    label = "Max block minutes",
                                    keyboardType = KeyboardType.Number
                                )
                            }
                            Button(
                                onClick = onRunScheduleGenerator,
                                modifier = Modifier.fillMaxWidth(),
                                enabled = hostAdminState.selectedStationId != null &&
                                    !hostAdminState.isLoadingCapabilities &&
                                    !hostAdminState.isSubmitting &&
                                    !hostAdminState.isPollingJob
                            ) {
                                if (hostAdminState.isSubmitting(HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR)) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Sync,
                                        contentDescription = null
                                    )
                                }
                                Text(
                                    text = when {
                                        hostAdminState.isSubmitting(HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR) -> "Submitting..."
                                        hostAdminState.isPollingJob &&
                                            hostAdminState.activeJob?.operation == HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR -> "Job Running..."
                                        else -> "Run Schedule Generator"
                                    },
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }

                hostAdminState.activeJob?.let { job ->
                    HostAdminJobCard(job = job, isPolling = hostAdminState.isPollingJob)
                }
            }
        }
    }
}

@Composable
private fun HostAdminJobCard(job: HostAdminJob, isPolling: Boolean) {
    val (statusLabel, statusIcon) = when (job.status.lowercase()) {
        "accepted" -> "Accepted" to null
        "running" -> "Running" to null
        "succeeded" -> "Succeeded" to Icons.Default.CheckCircle
        else -> "Failed" to Icons.Default.ErrorOutline
    }

    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Latest Host Job",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${stationLabel(job.station)} · ${job.operation.toOperationLabel()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    job.archetype?.let { archetype ->
                        Text(
                            text = "Archetype: ${archetype.toArchetypeLabel()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    job.trackFocus?.let { trackFocus ->
                        Text(
                            text = "Focus: ${trackFocus.toTrackFocusLabel()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (statusIcon != null) {
                    Icon(
                        imageVector = statusIcon,
                        contentDescription = statusLabel,
                        tint = if (job.status.equals("succeeded", ignoreCase = true)) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                    )
                } else if (isPolling) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                }
            }

            Text(
                text = "Status: $statusLabel",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )

            if (job.dryRun) {
                Text(
                    text = "Mode: Dry run",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (job.operation == HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR) {
                job.scheduleOptions?.let { scheduleOptions ->
                    val scheduleLines = listOfNotNull(
                        scheduleOptions.forceApply.takeIf { it }?.let { "Force apply enabled" },
                        scheduleOptions.seedMode?.let { "Seed mode: ${it.toSeedModeLabel()}" },
                        scheduleOptions.seedSalt?.takeIf { it.isNotBlank() }?.let {
                            if (scheduleOptions.seedMode == HOST_ADMIN_SCHEDULE_SEED_MODE_CUSTOM) {
                                "Custom seed: $it"
                            } else {
                                "Seed salt: $it"
                            }
                        },
                        scheduleOptions.weekStartDate?.let { "Week start: $it" },
                        scheduleOptions.openRatioMin?.let { "Open ratio min: $it" },
                        scheduleOptions.openRatioMax?.let { "Open ratio max: $it" },
                        scheduleOptions.minOpenSlots?.let { "Min open slots: $it" },
                        scheduleOptions.maxOpenSlots?.let { "Max open slots: $it" },
                        scheduleOptions.minBlockMinutes?.let { "Min block minutes: $it" },
                        scheduleOptions.maxBlockMinutes?.let { "Max block minutes: $it" }
                    )
                    scheduleLines.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (job.exitCode != null) {
                Text(
                    text = "Exit code: ${job.exitCode}",
                    style = MaterialTheme.typography.bodySmall
                )
            }

            val timestamps = listOfNotNull(
                job.acceptedAt?.let { "Accepted: $it" },
                job.startedAt?.let { "Started: $it" },
                job.finishedAt?.let { "Finished: $it" }
            )
            timestamps.forEach { line ->
                Text(
                    text = line,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            job.logTail?.takeIf { it.isNotBlank() }?.let { logTail ->
                Card {
                    Box(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = logTail.trim(),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusChip(
    label: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        enabled = enabled,
        onClick = onClick,
        label = { Text(label) }
    )
}

@Composable
private fun NumericOptionField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
    )
}

private fun stationLabel(stationId: String): String {
    return StationProvider.getStation(stationId)?.name
        ?: stationId.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private fun String.toArchetypeLabel(): String {
    return split('_')
        .joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }
}

private fun String.toTrackFocusLabel(): String {
    return when (this) {
        "current" -> "Current track"
        "next" -> "Next track"
        else -> split('_')
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}

private fun String.toSeedModeLabel(): String {
    return when (this) {
        "stable_week" -> "Stable week"
        "fresh" -> "Fresh"
        "custom" -> "Custom"
        else -> split('_')
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}

private fun String.toOperationLabel(): String {
    return when (this) {
        HOST_ADMIN_OPERATION_FORCE_ARCHETYPE -> "Force Archetype"
        HOST_ADMIN_OPERATION_SCHEDULE_GENERATOR -> "Schedule Generator"
        else -> split('_')
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            }
    }
}
