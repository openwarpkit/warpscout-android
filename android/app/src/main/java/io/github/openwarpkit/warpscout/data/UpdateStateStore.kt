package io.github.openwarpkit.warpscout.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updateStateDataStore by preferencesDataStore("update_state")

@Singleton
class UpdateStateStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    val state: Flow<StoredUpdateState> = context.updateStateDataStore.data.map { preferences ->
        val version = preferences[AVAILABLE_VERSION]
        StoredUpdateState(
            availableUpdate = version?.let {
                AvailableUpdate(
                    version = it,
                    releaseUrl = trustedReleasePageUrl(preferences[RELEASE_URL].orEmpty()),
                    apkUrl = preferences[APK_URL],
                    apkName = preferences[APK_NAME],
                    apkSize = preferences[APK_SIZE] ?: 0,
                    apkSha256 = preferences[APK_SHA256]
                )
            },
            dismissedUntilMillis = preferences[DISMISSED_UNTIL] ?: 0,
            downloadId = preferences[DOWNLOAD_ID] ?: 0,
            downloadReady = preferences[DOWNLOAD_READY] ?: false,
            downloadError = preferences[DOWNLOAD_ERROR]
        )
    }

    suspend fun snapshot(): StoredUpdateState = state.first()

    suspend fun saveAvailableUpdate(update: AvailableUpdate) {
        context.updateStateDataStore.edit { preferences ->
            val artifactChanged = preferences[AVAILABLE_VERSION] != update.version ||
                preferences[APK_URL] != update.apkUrl ||
                preferences[APK_NAME] != update.apkName ||
                preferences[APK_SIZE] != update.apkSize ||
                preferences[APK_SHA256] != update.apkSha256
            preferences[AVAILABLE_VERSION] = update.version
            preferences[RELEASE_URL] = update.releaseUrl
            update.apkUrl?.let { preferences[APK_URL] = it } ?: preferences.remove(APK_URL)
            update.apkName?.let { preferences[APK_NAME] = it } ?: preferences.remove(APK_NAME)
            preferences[APK_SIZE] = update.apkSize
            update.apkSha256?.let { preferences[APK_SHA256] = it } ?: preferences.remove(APK_SHA256)
            if (artifactChanged) clearDownload(preferences)
        }
    }

    suspend fun dismissAutomatically(nowMillis: Long) {
        context.updateStateDataStore.edit {
            it[DISMISSED_UNTIL] = nowMillis + AUTOMATIC_DISMISS_MILLIS
        }
    }

    suspend fun setDownloadStarted(downloadId: Long) {
        context.updateStateDataStore.edit {
            it[DOWNLOAD_ID] = downloadId
            it[DOWNLOAD_READY] = false
            it.remove(DOWNLOAD_ERROR)
        }
    }

    suspend fun setDownloadReady() {
        context.updateStateDataStore.edit {
            it[DOWNLOAD_READY] = true
            it.remove(DOWNLOAD_ERROR)
        }
    }

    suspend fun setDownloadFailed(reason: String) {
        context.updateStateDataStore.edit {
            it[DOWNLOAD_READY] = false
            it[DOWNLOAD_ERROR] = reason
        }
    }

    suspend fun clearDownload() {
        context.updateStateDataStore.edit { clearDownload(it) }
    }

    suspend fun clearAvailableUpdate() {
        context.updateStateDataStore.edit { it.clear() }
    }

    private fun clearDownload(preferences: androidx.datastore.preferences.core.MutablePreferences) {
        preferences.remove(DOWNLOAD_ID)
        preferences.remove(DOWNLOAD_READY)
        preferences.remove(DOWNLOAD_ERROR)
    }

    companion object {
        const val AUTOMATIC_DISMISS_MILLIS = 7L * 24 * 60 * 60 * 1000
        private val AVAILABLE_VERSION = stringPreferencesKey("available_version")
        private val RELEASE_URL = stringPreferencesKey("release_url")
        private val APK_URL = stringPreferencesKey("apk_url")
        private val APK_NAME = stringPreferencesKey("apk_name")
        private val APK_SIZE = longPreferencesKey("apk_size")
        private val APK_SHA256 = stringPreferencesKey("apk_sha256")
        private val DISMISSED_UNTIL = longPreferencesKey("dismissed_until")
        private val DOWNLOAD_ID = longPreferencesKey("download_id")
        private val DOWNLOAD_READY = booleanPreferencesKey("download_ready")
        private val DOWNLOAD_ERROR = stringPreferencesKey("download_error")
    }
}
