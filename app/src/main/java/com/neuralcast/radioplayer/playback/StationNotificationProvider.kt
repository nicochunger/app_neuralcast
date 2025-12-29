package com.neuralcast.radioplayer.playback

import android.content.Context
import androidx.media3.common.MediaMetadata
import androidx.media3.session.DefaultMediaNotificationProvider

class StationNotificationProvider(context: Context) : DefaultMediaNotificationProvider(context) {
    override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? {
        return metadata.subtitle ?: metadata.station ?: metadata.title
    }

    override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? {
        return metadata.title ?: metadata.artist
    }
}
