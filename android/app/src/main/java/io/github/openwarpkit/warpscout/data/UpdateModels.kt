package io.github.openwarpkit.warpscout.data

data class AvailableUpdate(
    val version: String,
    val releaseUrl: String,
    val apkUrl: String?,
    val apkName: String?,
    val apkSize: Long,
    val apkSha256: String? = null
)

data class StoredUpdateState(
    val availableUpdate: AvailableUpdate? = null,
    val dismissedUntilMillis: Long = 0,
    val downloadId: Long = 0,
    val downloadReady: Boolean = false,
    val downloadError: String? = null
)

sealed interface UpdateDownloadState {
    data object Idle : UpdateDownloadState

    data class Downloading(
        val update: AvailableUpdate,
        val downloadedBytes: Long,
        val totalBytes: Long
    ) : UpdateDownloadState {
        val progress: Float?
            get() = totalBytes.takeIf { it > 0 }?.let {
                (downloadedBytes.toFloat() / it.toFloat()).coerceIn(0f, 1f)
            }
    }

    data class Ready(val update: AvailableUpdate) : UpdateDownloadState

    data class Failed(
        val update: AvailableUpdate,
        val reason: String
    ) : UpdateDownloadState
}

fun UpdateResult.toAvailableUpdate(): AvailableUpdate? {
    if (!updateAvailable) return null
    return AvailableUpdate(
        version = latestVersion,
        releaseUrl = trustedReleasePageUrl(releaseUrl),
        apkUrl = apkAsset?.downloadUrl,
        apkName = apkAsset?.name,
        apkSize = apkAsset?.size ?: 0,
        apkSha256 = parseSha256Digest(apkAsset?.digest)
    )
}

fun shouldAutomaticallyCheck(nowMillis: Long, dismissedUntilMillis: Long): Boolean =
    nowMillis >= dismissedUntilMillis
