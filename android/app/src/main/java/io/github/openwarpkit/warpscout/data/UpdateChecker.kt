package io.github.openwarpkit.warpscout.data

import io.github.openwarpkit.warpscout.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

data class UpdateResult(
    val latestVersion: String,
    val releaseUrl: String,
    val updateAvailable: Boolean,
    val releaseFound: Boolean
)

data class AndroidRelease(
    val tag: String,
    val url: String,
    val draft: Boolean,
    val prerelease: Boolean
)

class UpdateChecker @Inject constructor() {
    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("User-Agent", "warpscout-android/${BuildConfig.VERSION_NAME}")
        try {
            check(connection.responseCode == HttpURLConnection.HTTP_OK)
            val releases = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            val release = selectAndroidRelease(
                (0 until releases.length()).map { index ->
                    releases.getJSONObject(index).let {
                        AndroidRelease(
                            tag = it.optString("tag_name"),
                            url = it.optString("html_url"),
                            draft = it.optBoolean("draft"),
                            prerelease = it.optBoolean("prerelease")
                        )
                    }
                }
            ) ?: return@withContext UpdateResult("", "", updateAvailable = false, releaseFound = false)
            val latest = release.tag.removePrefix("android-v")
            UpdateResult(
                latestVersion = latest,
                releaseUrl = release.url,
                updateAvailable = compareVersions(latest, BuildConfig.VERSION_NAME.substringBefore('-')) > 0,
                releaseFound = true
            )
        } finally {
            connection.disconnect()
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        val a = left.split('.').map { it.toIntOrNull() ?: 0 }
        val b = right.split('.').map { it.toIntOrNull() ?: 0 }
        return (0 until maxOf(a.size, b.size))
            .map { (a.getOrElse(it) { 0 }).compareTo(b.getOrElse(it) { 0 }) }
            .firstOrNull { it != 0 } ?: 0
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/openwarpkit/warpscout-android/releases?per_page=20"
    }
}

fun selectAndroidRelease(releases: List<AndroidRelease>): AndroidRelease? = releases.firstOrNull {
    !it.draft && !it.prerelease && it.tag.startsWith("android-v")
}
