package com.neuralcast.radioplayer.util

import androidx.media3.common.MediaMetadata

object MetadataHelper {
    fun extractNowPlaying(mediaMetadata: MediaMetadata, currentStationName: String?): String? {
        val title = mediaMetadata.title?.toString()?.trim()
        val displayTitle = mediaMetadata.displayTitle?.toString()?.trim()
        val artist = mediaMetadata.artist?.toString()?.trim()

        val resolvedTitle = when {
            !title.isNullOrBlank() -> title
            !displayTitle.isNullOrBlank() -> displayTitle
            else -> null
        }

        // Filter out if the title is just the station name
        if (!resolvedTitle.isNullOrBlank() && resolvedTitle == currentStationName) {
            return null
        }

        val currentTrack = when {
            !artist.isNullOrBlank() && !resolvedTitle.isNullOrBlank() -> "$artist - $resolvedTitle"
            !resolvedTitle.isNullOrBlank() -> resolvedTitle
            else -> null
        }

        return currentTrack
    }
}
