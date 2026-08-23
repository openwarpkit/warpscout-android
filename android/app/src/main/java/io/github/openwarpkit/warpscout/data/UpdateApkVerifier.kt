package io.github.openwarpkit.warpscout.data

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateApkVerifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun verify(file: File, update: AvailableUpdate): String? = withContext(Dispatchers.IO) {
        if (!file.isFile || file.length() <= 0) return@withContext UpdateDownloadController.FAILURE_FILE
        if (update.apkSize > 0 && file.length() != update.apkSize) {
            return@withContext UpdateDownloadController.FAILURE_FILE
        }
        val archive = packageInfo(file.absolutePath) ?: return@withContext UpdateDownloadController.FAILURE_FILE
        if (archive.packageName != RELEASE_APPLICATION_ID) {
            return@withContext UpdateDownloadController.FAILURE_PACKAGE
        }
        if (archive.versionName != update.version) {
            return@withContext UpdateDownloadController.FAILURE_VERSION
        }
        if (context.packageName == RELEASE_APPLICATION_ID) {
            val installed = packageInfo(context.packageName) ?: return@withContext UpdateDownloadController.FAILURE_SIGNATURE
            if (archive.versionCodeCompat() <= installed.versionCodeCompat()) {
                return@withContext UpdateDownloadController.FAILURE_VERSION
            }
            if (signatures(archive) != signatures(installed)) {
                return@withContext UpdateDownloadController.FAILURE_SIGNATURE
            }
        }
        null
    }

    @Suppress("DEPRECATION")
    private fun packageInfo(value: String): PackageInfo? {
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }
        return if (File(value).isAbsolute) {
            context.packageManager.getPackageArchiveInfo(value, flags)
        } else {
            runCatching { context.packageManager.getPackageInfo(value, flags) }.getOrNull()
        }
    }

    @Suppress("DEPRECATION")
    private fun signatures(packageInfo: PackageInfo): Set<String> {
        val values = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptySet()
            if (signingInfo.hasMultipleSigners()) signingInfo.apkContentsSigners else signingInfo.signingCertificateHistory
        } else {
            packageInfo.signatures
        }
        return values.orEmpty()
            .map { Base64.encodeToString(it.toByteArray(), Base64.NO_WRAP) }
            .toSet()
    }

    @Suppress("DEPRECATION")
    private fun PackageInfo.versionCodeCompat(): Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        longVersionCode
    } else {
        versionCode.toLong()
    }

    companion object {
        const val RELEASE_APPLICATION_ID = "io.github.openwarpkit.warpscout"
    }
}
