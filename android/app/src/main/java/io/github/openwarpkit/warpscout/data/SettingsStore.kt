package io.github.openwarpkit.warpscout.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore("settings")

data class AppSettings(
    val relayEnabled: Boolean = true,
    val relayUrl: String = "https://edge-client-api.vercel.app",
    val dynamicColor: Boolean = false
)

@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { preferences ->
        AppSettings(
            relayEnabled = preferences[RELAY_ENABLED] ?: true,
            relayUrl = preferences[RELAY_URL] ?: "https://edge-client-api.vercel.app",
            dynamicColor = preferences[DYNAMIC_COLOR] ?: false
        )
    }

    suspend fun setRelayEnabled(enabled: Boolean) {
        context.settingsDataStore.edit { it[RELAY_ENABLED] = enabled }
    }

    suspend fun setRelayUrl(value: String) {
        context.settingsDataStore.edit { it[RELAY_URL] = value.trim() }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.settingsDataStore.edit { it[DYNAMIC_COLOR] = enabled }
    }

    suspend fun clear() {
        context.settingsDataStore.edit { it.clear() }
    }

    private companion object {
        val RELAY_ENABLED = booleanPreferencesKey("relay_enabled")
        val RELAY_URL = stringPreferencesKey("relay_url")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    }
}
