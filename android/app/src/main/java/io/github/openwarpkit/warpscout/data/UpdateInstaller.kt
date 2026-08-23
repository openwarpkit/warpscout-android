package io.github.openwarpkit.warpscout.data

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

enum class UpdateInstallLaunch {
    Installer,
    PermissionSettings,
    Failed
}

@Singleton
class UpdateInstaller @Inject constructor(
    @ApplicationContext private val applicationContext: Context
) {
    fun launch(context: Context, update: AvailableUpdate): UpdateInstallLaunch {
        val file = updateApkFile(applicationContext, update)
        if (!file.isFile) return UpdateInstallLaunch.Failed
        if (!context.packageManager.canRequestPackageInstalls()) {
            val settingsIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                "package:${context.packageName}".toUri()
            )
            context.startActivity(settingsIntent)
            return UpdateInstallLaunch.PermissionSettings
        }
        val uri = FileProvider.getUriForFile(
            applicationContext,
            "${applicationContext.packageName}.files",
            file
        )
        val installIntent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, UpdateDownloadController.APK_MIME_TYPE)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return runCatching {
            context.startActivity(installIntent)
            UpdateInstallLaunch.Installer
        }.getOrDefault(UpdateInstallLaunch.Failed)
    }
}
