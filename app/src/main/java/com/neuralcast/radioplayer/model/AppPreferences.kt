package com.neuralcast.radioplayer.model

enum class AppTheme {
    SYSTEM, LIGHT, DARK
}

enum class BufferSize {
    NORMAL, LARGE
}

data class AppPreferences(
    val theme: AppTheme = AppTheme.SYSTEM,
    val bufferSize: BufferSize = BufferSize.NORMAL,
    val defaultVolume: Float = 1.0f
)
