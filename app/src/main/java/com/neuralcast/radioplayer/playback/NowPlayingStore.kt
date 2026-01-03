package com.neuralcast.radioplayer.playback

object NowPlayingStore {
    @Volatile
    var nowPlaying: String? = null
}
