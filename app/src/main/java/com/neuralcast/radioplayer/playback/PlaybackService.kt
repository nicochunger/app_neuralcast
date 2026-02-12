package com.neuralcast.radioplayer.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.core.os.bundleOf
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Metadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaConstants
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.neuralcast.radioplayer.MainActivity
import com.neuralcast.radioplayer.R
import com.neuralcast.radioplayer.data.StationProvider
import com.neuralcast.radioplayer.model.RadioStation
import com.neuralcast.radioplayer.playback.PlaybackConstants.EXTRA_NOW_PLAYING
import com.neuralcast.radioplayer.playback.NowPlayingStore
import com.neuralcast.radioplayer.util.MetadataHelper
import kotlin.math.min

@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
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
            if (!nowPlaying.isNullOrBlank() && nowPlaying != currentMeta.title?.toString()) {
                NowPlayingStore.nowPlaying = nowPlaying
                mediaLibrarySession?.setSessionExtras(
                    bundleOf(EXTRA_NOW_PLAYING to nowPlaying)
                )

                // Update MediaItem Metadata to trigger Notification update
                val newMeta = currentMeta.buildUpon()
                    .setTitle(nowPlaying)
                    .setArtist(stationName)
                    .setStation(stationName) // Ensure station info is preserved
                    .build()
                
                val newItem = currentItem.buildUpon()
                    .setMediaMetadata(newMeta)
                    .build()
                
                player?.replaceMediaItem(player?.currentMediaItemIndex ?: 0, newItem)
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
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle(getString(R.string.auto_root_title))
                        .build()
                )
                .build()
            return Futures.immediateFuture(
                LibraryResult.ofItem(rootItem, withCarContentStyle(params))
            )
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val station = StationProvider.getStation(mediaId)
                ?: return Futures.immediateFuture(
                    LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
                )
            return Futures.immediateFuture(
                LibraryResult.ofItem(buildPlayableStationItem(station), withCarContentStyle(null))
            )
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val responseParams = withCarContentStyle(params)
            if (parentId == ROOT_ID) {
                val items = StationProvider.stations
                    .map(::buildPlayableStationItem)
                    .let { paginate(it, page, pageSize) }
                return Futures.immediateFuture(LibraryResult.ofItemList(items, responseParams))
            }
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.of(), responseParams)
            )
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> {
            val responseParams = withCarContentStyle(params)
            val resultCount = getSearchMatches(query).size
            session.notifySearchResultChanged(browser, query, resultCount, responseParams)
            return Futures.immediateFuture(LibraryResult.ofVoid(responseParams))
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val responseParams = withCarContentStyle(params)
            val items = getSearchMatches(query)
                .map(::buildPlayableStationItem)
                .let { paginate(it, page, pageSize) }
            return Futures.immediateFuture(LibraryResult.ofItemList(items, responseParams))
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val updatedItems = mediaItems.map { item ->
                val station = resolveStationForRequest(item)
                if (station != null) {
                    buildPlayableStationItem(station)
                } else {
                    item
                }
            }.toMutableList()
            return Futures.immediateFuture(updatedItems)
        }
    }

    private fun buildPlayableStationItem(station: RadioStation): MediaItem {
        val artworkUri = Uri.parse("android.resource://${packageName}/${station.artworkResId}")
        return MediaItem.Builder()
            .setMediaId(station.id)
            .setUri(station.streamUrl)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setTitle(station.name)
                    .setSubtitle(station.description)
                    .setArtist(station.name)
                    .setArtworkUri(artworkUri)
                    .setStation(station.name)
                    .build()
            )
            .build()
    }

    private fun getSearchMatches(query: String): List<RadioStation> {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isBlank()) {
            return StationProvider.stations
        }
        return StationProvider.stations.filter { station ->
            station.name.contains(normalizedQuery, ignoreCase = true) ||
                station.description.orEmpty().contains(normalizedQuery, ignoreCase = true)
        }
    }

    private fun resolveStationForRequest(item: MediaItem): RadioStation? {
        StationProvider.getStation(item.mediaId)?.let { return it }
        val searchQuery = item.requestMetadata.searchQuery?.trim().orEmpty()
        if (searchQuery.isEmpty()) {
            return null
        }
        return getSearchMatches(searchQuery).firstOrNull()
    }

    private fun withCarContentStyle(params: LibraryParams?): LibraryParams {
        val extras = Bundle(params?.extras ?: Bundle()).apply {
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_BROWSABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
            putInt(
                MediaConstants.EXTRAS_KEY_CONTENT_STYLE_PLAYABLE,
                MediaConstants.EXTRAS_VALUE_CONTENT_STYLE_LIST_ITEM
            )
            putInt(MediaConstants.EXTRAS_KEY_ROOT_CHILDREN_LIMIT, StationProvider.stations.size)
        }
        return LibraryParams.Builder()
            .setRecent(params?.isRecent ?: false)
            .setOffline(params?.isOffline ?: false)
            .setSuggested(params?.isSuggested ?: false)
            .setExtras(extras)
            .build()
    }

    private fun paginate(items: List<MediaItem>, page: Int, pageSize: Int): List<MediaItem> {
        if (page < 0 || pageSize <= 0) {
            return emptyList()
        }
        val fromIndex = page * pageSize
        if (fromIndex >= items.size) {
            return emptyList()
        }
        val toIndex = min(fromIndex + pageSize, items.size)
        return items.subList(fromIndex, toIndex)
    }

    companion object {
        const val SESSION_ID = "neuralcast_session"
        private const val ROOT_ID = "root"
    }
}
