package com.neuralcast.radioplayer.util

import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.extractor.metadata.icy.IcyInfo

object MetadataHelper {
    fun extractNowPlaying(mediaMetadata: MediaMetadata, currentStationName: String?): String? =
        resolveTrack(
            title = mediaMetadata.title?.toString()?.trim(),
            displayTitle = mediaMetadata.displayTitle?.toString()?.trim(),
            artist = mediaMetadata.artist?.toString()?.trim(),
            currentStationName = currentStationName
        )

    fun extractNowPlaying(metadata: Metadata, currentStationName: String?): String? {
        var icyTitle: String? = null
        for (index in 0 until metadata.length()) {
            val icyInfo = metadata[index] as? IcyInfo ?: continue
            icyTitle = icyInfo.title?.trim()
            if (!icyTitle.isNullOrBlank()) break
        }
        return resolveTrack(
            title = icyTitle,
            displayTitle = null,
            artist = null,
            currentStationName = currentStationName
        )
    }

    private fun resolveTrack(
        title: String?,
        displayTitle: String?,
        artist: String?,
        currentStationName: String?
    ): String? {
        val resolvedTitle = when {
            !title.isNullOrBlank() -> title
            !displayTitle.isNullOrBlank() -> displayTitle
            else -> null
        }
        if (!resolvedTitle.isNullOrBlank() && resolvedTitle.equals(currentStationName, ignoreCase = true)) {
            return null
        }

        return when {
            !artist.isNullOrBlank() && !resolvedTitle.isNullOrBlank() -> "$artist - $resolvedTitle"
            !resolvedTitle.isNullOrBlank() -> resolvedTitle
            else -> null
        }
    }
}
