package com.neuralcast.radioplayer.model

enum class AppTheme {
    SYSTEM, LIGHT, DARK
}

data class AppPreferences(
    val theme: AppTheme = AppTheme.SYSTEM
)
