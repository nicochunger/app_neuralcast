package com.neuralcast.radioplayer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.neuralcast.radioplayer.model.PlaybackStatus
import com.neuralcast.radioplayer.model.RadioStation
import com.neuralcast.radioplayer.model.UiState

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun RadioScreen(
    uiState: UiState,
    onPlayToggle: (RadioStation) -> Unit,
    onErrorShown: () -> Unit
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
                title = { Text(text = "NeuralCast Radio") }
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
                    isActive = station.id == uiState.activeStationId,
                    playbackStatus = uiState.playbackStatus,
                    nowPlaying = if (station.id == uiState.activeStationId) uiState.nowPlaying else null,
                    onPlayToggle = { onPlayToggle(station) }
                )
            }
        }
    }
}

@Composable
private fun StationCard(
    station: RadioStation,
    isActive: Boolean,
    playbackStatus: PlaybackStatus,
    nowPlaying: String?,
    onPlayToggle: () -> Unit
) {
    val statusText = when {
        !isActive -> "Idle"
        playbackStatus == PlaybackStatus.Buffering -> "Buffering"
        playbackStatus == PlaybackStatus.Playing -> "Playing"
        playbackStatus == PlaybackStatus.Error -> "Error"
        else -> "Idle"
    }
    val nowPlayingText = when {
        !isActive -> "Now playing: -"
        nowPlaying.isNullOrBlank() -> "Now playing: Waiting for metadata"
        else -> "Now playing: $nowPlaying"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = station.name,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = nowPlayingText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onPlayToggle,
                modifier = Modifier.align(Alignment.End)
            ) {
                if (isActive && playbackStatus != PlaybackStatus.Idle) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Stop ${station.name}"
                    )
                    Text(
                        text = "Stop",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play ${station.name}"
                    )
                    Text(
                        text = "Play",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}
