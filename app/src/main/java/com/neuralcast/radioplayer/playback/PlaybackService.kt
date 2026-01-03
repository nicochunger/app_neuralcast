package com.neuralcast.radioplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.os.bundleOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.neuralcast.radioplayer.MainActivity
import com.neuralcast.radioplayer.data.StationProvider
import com.neuralcast.radioplayer.playback.PlaybackConstants.EXTRA_NOW_PLAYING
import com.neuralcast.radioplayer.playback.NowPlayingStore
import com.neuralcast.radioplayer.util.MetadataHelper

class PlaybackService : MediaLibraryService() {
    private var player: ExoPlayer? = null
    private var mediaLibrarySession: MediaLibrarySession? = null
    private val playerMetadataListener = object : Player.Listener {
        override fun onMetadata(metadata: Metadata) {
            val currentItem = player?.currentMediaItem ?: return
            val currentMeta = currentItem.mediaMetadata
            val stationName = player?.mediaMetadata?.station?.toString()
                ?: currentMeta.station?.toString()
                ?: currentMeta.title?.toString()

            val nowPlaying = MetadataHelper.extractNowPlaying(metadata, stationName)
            if (!nowPlaying.isNullOrBlank()) {
                NowPlayingStore.nowPlaying = nowPlaying
                mediaLibrarySession?.setSessionExtras(
                    bundleOf(EXTRA_NOW_PLAYING to nowPlaying)
                )

                // Update Player Metadata to trigger Notification update
                val newMeta = currentMeta.buildUpon()
                    .setTitle(nowPlaying)
                    .setSubtitle(stationName)
                    .setArtist(stationName)
                    .setStation(stationName) // Ensure station info is preserved
                    .build()
                player?.mediaMetadata = newMeta
            }
        }
    }

    override fun onCreate() {
        super.onCreate()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("NeuralCastRadio")
            .setDefaultRequestProperties(mapOf("Icy-MetaData" to "1"))
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
            .apply {
                val attrs = AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build()
                setAudioAttributes(attrs, true)
                setHandleAudioBecomingNoisy(true)
            }

        val sessionActivityIntent = Intent(this, MainActivity::class.java)
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            sessionActivityIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaLibrarySession = MediaLibrarySession.Builder(this, exoPlayer, CustomMediaLibrarySessionCallback())
            .setId(SESSION_ID)
            .setSessionActivity(sessionActivity)
            .build()

        player = exoPlayer
        exoPlayer.addListener(playerMetadataListener)
        setMediaNotificationProvider(StationNotificationProvider(this))
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaLibrarySession
    }

    override fun onDestroy() {
        mediaLibrarySession?.release()
        player?.removeListener(playerMetadataListener)
        player?.release()
        mediaLibrarySession = null
        player = null
        super.onDestroy()
    }

    private inner class CustomMediaLibrarySessionCallback : MediaLibrarySession.Callback {
        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootItem = MediaItem.Builder()
                .setMediaId("root")
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle("Radio Stations")
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(rootItem, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (parentId == "root") {
                val items = StationProvider.stations.map { station ->
                    val artworkUri = Uri.parse("android.resource://${packageName}/${station.artworkResId}")
                    MediaItem.Builder()
                        .setMediaId(station.id)
                        .setUri(station.streamUrl)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setIsBrowsable(false)
                                .setIsPlayable(true)
                                .setTitle(station.name)
                                .setSubtitle(station.description)
                                .setArtworkUri(artworkUri)
                                .setStation(station.name) // Important for Auto
                                .build()
                        )
                        .build()
                }
                return Futures.immediateFuture(LibraryResult.ofItemList(items, params))
            }
            return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val updatedItems = mediaItems.map { item ->
                val station = StationProvider.getStation(item.mediaId)
                if (station != null) {
                    val artworkUri = Uri.parse("android.resource://${packageName}/${station.artworkResId}")
                    item.buildUpon()
                        .setUri(station.streamUrl)
                        .setMediaMetadata(
                            item.mediaMetadata.buildUpon()
                                .setStation(station.name)
                                .setTitle(station.name)
                                .setSubtitle(station.description)
                                .setArtworkUri(artworkUri)
                                .build()
                        )
                        .build()
                } else {
                    item
                }
            }.toMutableList()
            return Futures.immediateFuture(updatedItems)
        }
    }

    companion object {
        const val SESSION_ID = "neuralcast_session"
    }
}
