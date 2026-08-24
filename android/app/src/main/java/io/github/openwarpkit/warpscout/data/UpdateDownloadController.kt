package io.github.openwarpkit.warpscout.data

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateDownloadController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val updateStateStore: UpdateStateStore,
    private val apkVerifier: UpdateApkVerifier
) {
    private val downloadManager = context.getSystemService(DownloadManager::class.java)
    private val mutableState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    private val refreshMutex = Mutex()
    val state: StateFlow<UpdateDownloadState> = mutableState.asStateFlow()

    suspend fun restore() {
        val stored = updateStateStore.snapshot()
        val update = stored.availableUpdate ?: run {
            mutableState.value = UpdateDownloadState.Idle
            return
        }
        when {
            stored.downloadError != null -> mutableState.value = UpdateDownloadState.Failed(update, stored.downloadError)
            stored.downloadReady -> restoreReadyDownload(update)
            stored.downloadId > 0 -> refresh()
            else -> mutableState.value = UpdateDownloadState.Idle
        }
    }

    suspend fun start(update: AvailableUpdate): Boolean = withContext(Dispatchers.IO) {
        if (!canAutomaticallyDownloadUpdate(update)) {
            fail(update, FAILURE_AUTOMATIC_UNAVAILABLE)
            return@withContext false
        }
        val url = checkNotNull(update.apkUrl)
        val name = checkNotNull(update.apkName)
        removeUpdateFiles()
        val target = updateDownloadApkFile(context, update)
        target.parentFile?.mkdirs()
        val request = DownloadManager.Request(url.toUri())
            .setTitle("WarpScout ${update.version}")
            .setDescription(name)
            .setMimeType(APK_MIME_TYPE)
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            .setDestinationUri(Uri.fromFile(target))
            .addRequestHeader("Accept", "application/octet-stream")
            .addRequestHeader("User-Agent", "warpscout-android-updater")
        val downloadId = runCatching { downloadManager.enqueue(request) }.getOrElse {
            fail(update, FAILURE_DOWNLOAD)
            return@withContext false
        }
        updateStateStore.setDownloadStarted(downloadId)
        mutableState.value = UpdateDownloadState.Downloading(update, 0, update.apkSize)
        true
    }

    suspend fun refresh(): UpdateDownloadState = refreshMutex.withLock {
        val stored = updateStateStore.snapshot()
        val update = stored.availableUpdate ?: return@withLock UpdateDownloadState.Idle.also {
            mutableState.value = it
        }
        if (stored.downloadReady) return@withLock restoreReadyDownload(update)
        if (stored.downloadId <= 0) {
            val next = stored.downloadError?.let { UpdateDownloadState.Failed(update, it) }
                ?: UpdateDownloadState.Idle
            mutableState.value = next
            return@withLock next
        }
        val query = DownloadManager.Query().setFilterById(stored.downloadId)
        val result = downloadManager.query(query)?.use { cursor ->
            if (!cursor.moveToFirst()) return@use null
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            val downloaded = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
            val total = cursor.getLong(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
            Triple(status, downloaded.coerceAtLeast(0), total)
        }
        val next = when (result?.first) {
            DownloadManager.STATUS_PENDING,
            DownloadManager.STATUS_RUNNING,
            DownloadManager.STATUS_PAUSED -> UpdateDownloadState.Downloading(
                update = update,
                downloadedBytes = result.second,
                totalBytes = result.third.takeIf { it > 0 } ?: update.apkSize
            )
            DownloadManager.STATUS_SUCCESSFUL -> completeDownload(update)
            DownloadManager.STATUS_FAILED -> fail(update, FAILURE_DOWNLOAD)
            else -> fail(update, FAILURE_DOWNLOAD)
        }
        mutableState.value = next
        next
    }

    suspend fun monitorUntilTerminal() {
        while (refresh() is UpdateDownloadState.Downloading) delay(500)
    }

    suspend fun cancel() = withContext(Dispatchers.IO) {
        val stored = updateStateStore.snapshot()
        if (stored.downloadId > 0) downloadManager.remove(stored.downloadId)
        removeUpdateFiles()
        updateStateStore.clearDownload()
        mutableState.value = UpdateDownloadState.Idle
    }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        val stored = updateStateStore.snapshot()
        if (stored.downloadId > 0) downloadManager.remove(stored.downloadId)
        removeUpdateFiles()
        updateStateStore.clearAvailableUpdate()
        mutableState.value = UpdateDownloadState.Idle
    }

    suspend fun rejectReady(update: AvailableUpdate, reason: String) = withContext(Dispatchers.IO) {
        removeUpdateFiles()
        fail(update, reason)
    }

    private suspend fun completeDownload(update: AvailableUpdate): UpdateDownloadState {
        val downloadedFile = updateDownloadApkFile(context, update)
        val file = verifiedUpdateApkFile(context, update)
        val verificationFailure = runCatching {
            file.parentFile?.mkdirs()
            val temporaryFile = File(file.parentFile, "${file.nameWithoutExtension}.part.apk")
            temporaryFile.delete()
            FileOutputStream(temporaryFile).use { output ->
                downloadedFile.inputStream().use { input -> input.copyTo(output) }
                output.fd.sync()
            }
            apkVerifier.verify(temporaryFile, update)?.let { return@runCatching it }
            file.delete()
            if (!temporaryFile.renameTo(file)) return@runCatching FAILURE_FILE
            downloadedFile.delete()
            null
        }.getOrDefault(FAILURE_FILE)
        return if (verificationFailure == null) {
            updateStateStore.setDownloadReady()
            UpdateDownloadState.Ready(update).also { mutableState.value = it }
        } else {
            removeUpdateFiles()
            fail(update, verificationFailure)
        }
    }

    private suspend fun restoreReadyDownload(update: AvailableUpdate): UpdateDownloadState {
        val verificationFailure = apkVerifier.verify(verifiedUpdateApkFile(context, update), update)
        return if (verificationFailure == null) {
            UpdateDownloadState.Ready(update).also { mutableState.value = it }
        } else {
            removeUpdateFiles()
            fail(update, verificationFailure)
        }
    }

    private suspend fun fail(update: AvailableUpdate, reason: String): UpdateDownloadState.Failed {
        updateStateStore.setDownloadFailed(reason)
        return UpdateDownloadState.Failed(update, reason).also { mutableState.value = it }
    }

    private fun removeUpdateFiles() {
        listOf(updateDownloadDirectory(context), verifiedUpdateDirectory(context)).forEach { directory ->
            directory.listFiles()?.forEach { file ->
                if (file.isFile && file.extension.equals("apk", ignoreCase = true)) {
                    file.delete()
                }
            }
        }
    }

    companion object {
        const val FAILURE_AUTOMATIC_UNAVAILABLE = "automatic_download_unavailable"
        const val FAILURE_DOWNLOAD = "update_download_failed"
        const val FAILURE_FILE = "update_file_invalid"
        const val FAILURE_DIGEST = "update_digest_invalid"
        const val FAILURE_PACKAGE = "update_package_invalid"
        const val FAILURE_VERSION = "update_version_invalid"
        const val FAILURE_SIGNATURE = "update_signature_invalid"
        const val APK_MIME_TYPE = "application/vnd.android.package-archive"
    }
}

fun updateDownloadDirectory(context: Context): File =
    File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "updates")

fun verifiedUpdateDirectory(context: Context): File = File(context.filesDir, "updates")

fun updateDownloadApkFile(context: Context, update: AvailableUpdate): File =
    File(updateDownloadDirectory(context), safeUpdateAssetName(update))

fun verifiedUpdateApkFile(context: Context, update: AvailableUpdate): File =
    File(verifiedUpdateDirectory(context), safeUpdateAssetName(update))

private fun safeUpdateAssetName(update: AvailableUpdate): String {
    val safeAssetName = update.apkName
        ?.let(::File)
        ?.name
        ?.takeIf { it.endsWith(".apk", ignoreCase = true) }
        ?: "warpscout-android_${update.version.replace(Regex("[^0-9A-Za-z._-]"), "_")}.apk"
    return safeAssetName
}
