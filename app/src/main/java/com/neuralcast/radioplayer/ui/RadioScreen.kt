package com.neuralcast.radioplayer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.neuralcast.radioplayer.model.PlaybackHistoryEntry
import com.neuralcast.radioplayer.model.PlaybackStatus
import com.neuralcast.radioplayer.model.RadioStation
import com.neuralcast.radioplayer.model.RequestableSong
import com.neuralcast.radioplayer.model.SongRequestState
import com.neuralcast.radioplayer.model.UiState
import java.text.DateFormat

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RadioScreen(
    uiState: UiState,
    onPlayToggle: (RadioStation) -> Unit,
    onSongRequestClick: (RadioStation) -> Unit,
    onSkipTrack: (RadioStation) -> Unit,
    onSongRequestSubmit: (RequestableSong) -> Unit,
    onSongRequestDismiss: () -> Unit,
    onSleepTimerSet: (Int?) -> Unit,
    onErrorShown: () -> Unit,
    onAdminConsoleClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = uiState.errorMessage

    if (errorMessage != null) {
        LaunchedEffect(errorMessage) {
            snackbarHostState.showSnackbar(errorMessage)
            onErrorShown()
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "NeuralCast")
                        AnimatedVisibility(visible = uiState.sleepTimerRemaining != null) {
                            Text(
                                text = "Sleep timer: ${formatSleepTimer(uiState.sleepTimerRemaining ?: 0L)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onSettingsClick) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                },
                actions = {
                    if (uiState.hostAdminConsole.isConfigured) {
                        IconButton(onClick = onAdminConsoleClick) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Open Admin Console"
                            )
                        }
                    }
                    SleepTimerMenu(
                        timerRemaining = uiState.sleepTimerRemaining,
                        onTimerSet = onSleepTimerSet
                    )
                }
            )
        },

        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(uiState.stations, key = { it.id }) { station ->
                StationCard(
                    station = station,
                    listenerCount = uiState.listenerCounts[station.id],
                    isActive = station.id == uiState.activeStationId,
                    playbackStatus = uiState.playbackStatus,
                    nowPlaying = if (station.id == uiState.activeStationId) uiState.nowPlaying else null,
                    onPlayToggle = { onPlayToggle(station) },
                    onSongRequestClick = { onSongRequestClick(station) },
                    showSkipTrack = uiState.isAdminModeEnabled,
                    isSkippingTrack = uiState.skippingStationId == station.id,
                    onSkipTrack = { onSkipTrack(station) }
                )
            }

            if (uiState.recentlyPlayed.isNotEmpty()) {
                item {
                    Text(
                        text = "Recently Played",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 8.dp)
                    )
                }
                items(uiState.recentlyPlayed, key = { "${it.track}-${it.playedAt}" }) { entry ->
                    HistoryItem(entry = entry)
                }
            }
        }
    }

    SongRequestDialog(
        requestState = uiState.songRequestState,
        onRequestSong = onSongRequestSubmit,
        onDismiss = onSongRequestDismiss
    )
}

private fun formatSleepTimer(remainingMillis: Long): String {
    val clampedMillis = remainingMillis.coerceAtLeast(0L)
    val totalSeconds = clampedMillis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun SleepTimerMenu(
    timerRemaining: Long?,
    onTimerSet: (Int?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.Timer,
                contentDescription = "Sleep Timer",
                tint = if (timerRemaining != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (timerRemaining != null) {
                DropdownMenuItem(
                    text = { Text("Cancel Timer (${timerRemaining / 60000}m left)") },
                    onClick = {
                        onTimerSet(null)
                        expanded = false
                    }
                )
            } else {
                listOf(15, 30, 45, 60).forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text("$minutes minutes") },
                        onClick = {
                            onTimerSet(minutes)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryItem(entry: PlaybackHistoryEntry) {
    val timeFormatter = remember { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.History,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Column(
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Text(
                text = entry.track,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = timeFormatter.format(entry.playedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun StationCard(
    station: RadioStation,
    listenerCount: Int?,
    isActive: Boolean,
    playbackStatus: PlaybackStatus,
    nowPlaying: String?,
    onPlayToggle: () -> Unit,
    onSongRequestClick: () -> Unit,
    showSkipTrack: Boolean,
    isSkippingTrack: Boolean,
    onSkipTrack: () -> Unit
) {
    val cardShape = RoundedCornerShape(28.dp)
    val overlayBrush = Brush.verticalGradient(
        0.0f to Color.Black.copy(alpha = 0.1f),
        0.55f to Color.Black.copy(alpha = 0.45f),
        1.0f to Color.Black.copy(alpha = 0.8f)
    )
    val statusText = when {
        !isActive -> "Idle"
        playbackStatus == PlaybackStatus.Buffering -> "Buffering"
        playbackStatus == PlaybackStatus.Playing -> "Playing"
        playbackStatus == PlaybackStatus.Error -> "Error"
        else -> "Idle"
    }
    val nowPlayingValue = when {
        !isActive -> "-"
        nowPlaying.isNullOrBlank() -> "Waiting for metadata"
        else -> nowPlaying
    }
    val listenerText = "Listeners: ${listenerCount?.toString() ?: "--"}"

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = spring(stiffness = Spring.StiffnessLow)),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f) // Keep main image aspect ratio constant
        ) {
            Image(
                painter = painterResource(id = station.backgroundResId),
                contentDescription = "${station.name} background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(overlayBrush)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = station.name,
                            style = MaterialTheme.typography.titleLarge.copy(
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = statusText,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                            Surface(
                                modifier = Modifier.padding(start = 8.dp),
                                color = Color.Black.copy(alpha = 0.45f),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                Text(
                                    text = listenerText,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White
                                )
                            }
                            if (isActive && playbackStatus == PlaybackStatus.Playing) {
                                WaveformIndicator(
                                    modifier = Modifier.padding(start = 8.dp),
                                    barColor = Color.White
                                )
                            }
                        }
                    }
                }

                // Footer
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Now playing",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White.copy(alpha = 0.75f)
                        )
                        Text(
                            text = nowPlayingValue,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.95f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                    ) {
                        if (showSkipTrack) {
                            OutlinedButton(
                                modifier = Modifier.weight(1f),
                                onClick = onSkipTrack,
                                enabled = !isSkippingTrack,
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(999.dp)
                            ) {
                                if (isSkippingTrack) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(16.dp),
                                        strokeWidth = 2.dp,
                                        color = Color.White
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.SkipNext,
                                        contentDescription = "Skip current track on ${station.name}"
                                    )
                                }
                                Text(
                                    text = "Skip",
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }

                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            onClick = onSongRequestClick,
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LibraryMusic,
                                contentDescription = "Request song for ${station.name}"
                            )
                            Text(
                                text = "Request",
                                maxLines = 1,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        Button(
                            modifier = Modifier.weight(1f),
                            onClick = onPlayToggle,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            if (isActive && playbackStatus != PlaybackStatus.Idle) {
                                Icon(
                                    imageVector = Icons.Default.Stop,
                                    contentDescription = "Stop ${station.name}"
                                )
                                Text(
                                    text = "Stop",
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Play ${station.name}"
                                )
                                Text(
                                    text = "Play",
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SongRequestDialog(
    requestState: SongRequestState,
    onRequestSong: (RequestableSong) -> Unit,
    onDismiss: () -> Unit
) {
    if (!requestState.isVisible) return

    var query by remember(requestState.stationId) { mutableStateOf("") }
    val filteredSongs = remember(query, requestState.songs) {
        if (query.isBlank()) {
            requestState.songs
        } else {
            requestState.songs.filter { song ->
                song.displayText.contains(query, ignoreCase = true)
            }
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Request a Song",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = requestState.stationName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                when {
                    requestState.isLoading -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(28.dp))
                        }
                    }

                    requestState.songs.isEmpty() -> {
                        Text(
                            text = "No songs are currently available for request.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    else -> {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Search songs") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = null
                                )
                            }
                        )

                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (filteredSongs.isEmpty()) {
                                item {
                                    Text(
                                        text = "No songs matched your search.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(filteredSongs, key = { it.requestId }) { song ->
                                    RequestSongItem(
                                        song = song,
                                        isSubmitting = requestState.submittingRequestId == song.requestId,
                                        isAnySubmissionInProgress = requestState.submittingRequestId != null,
                                        onRequestSong = onRequestSong
                                    )
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                }
            }
        }
    }
}

@Composable
private fun RequestSongItem(
    song: RequestableSong,
    isSubmitting: Boolean,
    isAnySubmissionInProgress: Boolean,
    onRequestSong: (RequestableSong) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = song.displayText,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            TextButton(
                onClick = { onRequestSong(song) },
                enabled = !isAnySubmissionInProgress
            ) {
                if (isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Request")
                }
            }
        }
    }
}
