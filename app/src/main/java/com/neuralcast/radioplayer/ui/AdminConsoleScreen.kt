package com.neuralcast.radioplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neuralcast.radioplayer.data.StationProvider
import com.neuralcast.radioplayer.model.HostAdminConsoleState
import com.neuralcast.radioplayer.model.HostAdminJob

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminConsoleScreen(
    hostAdminState: HostAdminConsoleState,
    onNavigateBack: () -> Unit,
    onRefreshOptions: () -> Unit,
    onStationSelected: (String) -> Unit,
    onArchetypeSelected: (String) -> Unit,
    onRunForcedArchetype: () -> Unit
) {
    var archetypeMenuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(hostAdminState.baseUrl, hostAdminState.token) {
        if (hostAdminState.isConfigured &&
            hostAdminState.availableArchetypes.isEmpty() &&
            !hostAdminState.isLoadingOptions
        ) {
            onRefreshOptions()
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
                        text = "Host Orchestrator",
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
                        OutlinedButton(onClick = onRefreshOptions, enabled = !hostAdminState.isLoadingOptions) {
                            if (hostAdminState.isLoadingOptions) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.Sync,
                                    contentDescription = "Refresh options"
                                )
                            }
                            Text(
                                text = "Refresh Options",
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        val optionsMessage = hostAdminState.optionsStatusMessage
                        if (optionsMessage != null) {
                            Text(
                                text = optionsMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (hostAdminState.isOptionsStatusError) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                }
                            )
                        } else {
                            Text(
                                text = "Refresh options to load the live station and archetype list from the VPS.",
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
                        if (hostAdminState.availableArchetypes.isEmpty() && !hostAdminState.isLoadingOptions) {
                            Text(
                                text = "No archetypes are loaded yet. Refresh options to fetch them from the server.",
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
                        Button(
                            onClick = onRunForcedArchetype,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = hostAdminState.selectedStationId != null &&
                                hostAdminState.selectedArchetype != null &&
                                !hostAdminState.isLoadingOptions &&
                                !hostAdminState.isSubmitting &&
                                !hostAdminState.isPollingJob
                        ) {
                            if (hostAdminState.isSubmitting) {
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
                                    hostAdminState.isSubmitting -> "Submitting..."
                                    hostAdminState.isPollingJob -> "Job Running..."
                                    else -> "Run Forced Archetype"
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            )
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
                        text = "${stationLabel(job.station)} · ${job.archetype.toArchetypeLabel()}",
                        style = MaterialTheme.typography.bodyMedium
                    )
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
