package io.github.openwarpkit.warpscout.data

import android.os.Build
import io.github.openwarpkit.warpscout.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import javax.inject.Inject

data class UpdateResult(
    val latestVersion: String,
    val releaseUrl: String,
    val updateAvailable: Boolean,
    val releaseFound: Boolean,
    val apkAsset: ReleaseAsset? = null
)

data class ReleaseAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val digest: String? = null
)

data class AndroidRelease(
    val tag: String,
    val url: String,
    val draft: Boolean,
    val prerelease: Boolean,
    val assets: List<ReleaseAsset> = emptyList()
)

class UpdateChecker @Inject constructor() {
    suspend fun check(): UpdateResult = withContext(Dispatchers.IO) {
        val connection = URL(RELEASES_URL).openConnection() as HttpURLConnection
        connection.connectTimeout = 5_000
        connection.readTimeout = 5_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/vnd.github+json")
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
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
                            prerelease = it.optBoolean("prerelease"),
                            assets = it.optJSONArray("assets")?.let { assets ->
                                (0 until assets.length()).map { assetIndex ->
                                    assets.getJSONObject(assetIndex).let { asset ->
                                        ReleaseAsset(
                                            name = asset.optString("name"),
                                            downloadUrl = asset.optString("browser_download_url"),
                                            size = asset.optLong("size"),
                                            digest = asset.optString("digest").takeIf { it.isNotBlank() }
                                        )
                                    }
                                }
                            }.orEmpty()
                        )
                    }
                }
            ) ?: return@withContext UpdateResult("", "", updateAvailable = false, releaseFound = false)
            val latest = release.tag.removePrefix("android-v")
            UpdateResult(
                latestVersion = latest,
                releaseUrl = release.url,
                updateAvailable = compareVersions(latest, BuildConfig.VERSION_NAME.substringBefore('-')) > 0,
                releaseFound = true,
                apkAsset = selectAndroidApkAsset(release.assets, Build.SUPPORTED_ABIS.toList())
            )
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val RELEASES_URL = "https://api.github.com/repos/openwarpkit/warpscout-android/releases?per_page=20"
    }
}

fun selectAndroidRelease(releases: List<AndroidRelease>): AndroidRelease? = releases.firstOrNull {
    !it.draft && !it.prerelease && ANDROID_RELEASE_TAG_REGEX.matches(it.tag)
}

fun selectAndroidApkAsset(assets: List<ReleaseAsset>, supportedAbis: List<String>): ReleaseAsset? {
    val apkAssets = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    supportedAbis.forEach { abi ->
        apkAssets.firstOrNull { it.name.endsWith("_${abi}.apk", ignoreCase = true) }?.let { return it }
    }
    return apkAssets.firstOrNull { it.name.endsWith("_universal.apk", ignoreCase = true) }
}

fun parseSha256Digest(value: String?): String? {
    val match = SHA256_DIGEST_REGEX.matchEntire(value.orEmpty()) ?: return null
    return match.groupValues[1].lowercase(Locale.ROOT)
}

fun isTrustedUpdateAssetUrl(value: String, assetName: String): Boolean {
    if (!UPDATE_ASSET_NAME_REGEX.matches(assetName)) return false
    val uri = runCatching { URI(value) }.getOrNull() ?: return false
    if (uri.scheme != "https" || !uri.host.equals("github.com", ignoreCase = true)) return false
    if (uri.rawUserInfo != null || uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null) return false
    val expectedPrefix = "/openwarpkit/warpscout-android/releases/download/"
    val segments = uri.path.takeIf { it.startsWith(expectedPrefix) }
        ?.removePrefix(expectedPrefix)
        ?.split('/')
        ?: return false
    return segments.size == 2 && ANDROID_RELEASE_TAG_REGEX.matches(segments[0]) && segments[1] == assetName
}

fun canAutomaticallyDownloadUpdate(update: AvailableUpdate): Boolean {
    val url = update.apkUrl ?: return false
    val name = update.apkName ?: return false
    return SHA256_HEX_REGEX.matches(update.apkSha256.orEmpty()) && isTrustedUpdateAssetUrl(url, name)
}

fun trustedReleasePageUrl(value: String): String {
    val uri = runCatching { URI(value) }.getOrNull() ?: return RELEASES_PAGE_URL
    if (uri.scheme != "https" || !uri.host.equals("github.com", ignoreCase = true)) return RELEASES_PAGE_URL
    if (uri.rawUserInfo != null || uri.port != -1 || uri.rawQuery != null || uri.rawFragment != null) {
        return RELEASES_PAGE_URL
    }
    val prefix = "/openwarpkit/warpscout-android/releases/tag/"
    return value.takeIf { uri.path.startsWith(prefix) && ANDROID_RELEASE_TAG_REGEX.matches(uri.path.removePrefix(prefix)) }
        ?: RELEASES_PAGE_URL
}

fun compareVersions(left: String, right: String): Int {
    val a = left.split('.').map { it.toIntOrNull() ?: 0 }
    val b = right.split('.').map { it.toIntOrNull() ?: 0 }
    return (0 until maxOf(a.size, b.size))
        .map { (a.getOrElse(it) { 0 }).compareTo(b.getOrElse(it) { 0 }) }
        .firstOrNull { it != 0 } ?: 0
}

private val SHA256_DIGEST_REGEX = Regex("^sha256:([0-9a-fA-F]{64})$")
private val SHA256_HEX_REGEX = Regex("^[0-9a-f]{64}$")
private val UPDATE_ASSET_NAME_REGEX = Regex("^[0-9A-Za-z._-]+\\.apk$", RegexOption.IGNORE_CASE)
private val ANDROID_RELEASE_TAG_REGEX = Regex("^android-v[0-9]+\\.[0-9]+\\.[0-9]+$")
private const val RELEASES_PAGE_URL = "https://github.com/openwarpkit/warpscout-android/releases"
