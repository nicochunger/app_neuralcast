package com.neuralcast.radioplayer.playback

import android.content.Context
import androidx.media3.common.MediaMetadata
import androidx.media3.session.DefaultMediaNotificationProvider
import com.neuralcast.radioplayer.util.MetadataHelper

class StationNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {
    override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? {
        val stationName = metadata.station ?: metadata.subtitle
        val nowPlaying = MetadataHelper.extractNowPlaying(metadata, stationName?.toString())
            ?: NowPlayingStore.nowPlaying
        return nowPlaying ?: stationName ?: metadata.title
    }

    override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? {
        // If title is used for "Now Playing", text can be the Station Name
        val stationName = metadata.station ?: metadata.subtitle
        val nowPlaying = MetadataHelper.extractNowPlaying(metadata, stationName?.toString())
            ?: NowPlayingStore.nowPlaying
        
        return if (nowPlaying != null) {
            stationName ?: metadata.artist ?: metadata.title
        } else {
            // Fallback
            metadata.artist
        }
    }
}
