package com.neuralcast.radioplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.neuralcast.radioplayer.model.AppPreferences
import com.neuralcast.radioplayer.model.AppTheme
import com.neuralcast.radioplayer.model.BufferSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val THEME_KEY = stringPreferencesKey("theme")
    private val BUFFER_SIZE_KEY = stringPreferencesKey("buffer_size")
    private val VOLUME_KEY = floatPreferencesKey("default_volume")

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            theme = AppTheme.valueOf(prefs[THEME_KEY] ?: AppTheme.SYSTEM.name),
            bufferSize = BufferSize.valueOf(prefs[BUFFER_SIZE_KEY] ?: BufferSize.NORMAL.name),
            defaultVolume = prefs[VOLUME_KEY] ?: 1.0f
        )
    }

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { prefs ->
            prefs[THEME_KEY] = theme.name
        }
    }

    suspend fun setBufferSize(size: BufferSize) {
        context.dataStore.edit { prefs ->
            prefs[BUFFER_SIZE_KEY] = size.name
        }
    }

    suspend fun setDefaultVolume(volume: Float) {
        context.dataStore.edit { prefs ->
            prefs[VOLUME_KEY] = volume
        }
    }
}
