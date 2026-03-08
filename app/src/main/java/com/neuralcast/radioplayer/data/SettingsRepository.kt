package com.neuralcast.radioplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.neuralcast.radioplayer.model.AppPreferences
import com.neuralcast.radioplayer.model.AppTheme
import com.neuralcast.radioplayer.model.PlaybackHistoryEntry
import org.json.JSONArray
import org.json.JSONObject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    private val THEME_KEY = stringPreferencesKey("theme")
    private val ACTIVE_STATION_KEY = stringPreferencesKey("active_station_id")
    private val RECENTLY_PLAYED_KEY = stringPreferencesKey("recently_played_json")
    private val ADMIN_MODE_ENABLED_KEY = booleanPreferencesKey("admin_mode_enabled")
    private val ADMIN_API_KEY = stringPreferencesKey("admin_api_key")

    val preferences: Flow<AppPreferences> = context.dataStore.data.map { prefs ->
        AppPreferences(
            theme = AppTheme.valueOf(prefs[THEME_KEY] ?: AppTheme.SYSTEM.name)
        )
    }

    val playbackState: Flow<PlaybackSnapshot> = context.dataStore.data.map { prefs ->
        PlaybackSnapshot(
            activeStationId = prefs[ACTIVE_STATION_KEY],
            recentlyPlayed = decodeHistory(prefs[RECENTLY_PLAYED_KEY])
        )
    }

    val adminSession: Flow<AdminSessionSnapshot> = context.dataStore.data.map { prefs ->
        AdminSessionSnapshot(
            isAdminModeEnabled = prefs[ADMIN_MODE_ENABLED_KEY] ?: false,
            apiKey = prefs[ADMIN_API_KEY]
        )
    }

    suspend fun setTheme(theme: AppTheme) {
        context.dataStore.edit { prefs ->
            prefs[THEME_KEY] = theme.name
        }
    }




    suspend fun setActiveStationId(activeStationId: String?) {
        context.dataStore.edit { prefs ->
            if (activeStationId == null) {
                prefs.remove(ACTIVE_STATION_KEY)
            } else {
                prefs[ACTIVE_STATION_KEY] = activeStationId
            }
        }
    }

    suspend fun setRecentlyPlayed(history: List<PlaybackHistoryEntry>) {
        context.dataStore.edit { prefs ->
            if (history.isEmpty()) {
                prefs.remove(RECENTLY_PLAYED_KEY)
            } else {
                prefs[RECENTLY_PLAYED_KEY] = encodeHistory(history)
            }
        }
    }

    suspend fun setAdminSession(apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[ADMIN_MODE_ENABLED_KEY] = true
            prefs[ADMIN_API_KEY] = apiKey
        }
    }

    suspend fun clearAdminSession() {
        context.dataStore.edit { prefs ->
            prefs[ADMIN_MODE_ENABLED_KEY] = false
            prefs.remove(ADMIN_API_KEY)
        }
    }

    private fun encodeHistory(history: List<PlaybackHistoryEntry>): String {
        val array = JSONArray()
        history.forEach { entry ->
            val item = JSONObject()
                .put("track", entry.track)
                .put("playedAt", entry.playedAt)
            array.put(item)
        }
        return array.toString()
    }

    private fun decodeHistory(raw: String?): List<PlaybackHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        val entries = mutableListOf<PlaybackHistoryEntry>()
        try {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val track = item.optString("track")
                val playedAt = item.optLong("playedAt", 0L)
                if (track.isNotBlank() && playedAt > 0L) {
                    entries.add(PlaybackHistoryEntry(track = track, playedAt = playedAt))
                }
            }
        } catch (_: org.json.JSONException) {
            return emptyList()
        }
        return entries
    }

    data class PlaybackSnapshot(
        val activeStationId: String?,
        val recentlyPlayed: List<PlaybackHistoryEntry>
    )

    data class AdminSessionSnapshot(
        val isAdminModeEnabled: Boolean,
        val apiKey: String?
    )
}
