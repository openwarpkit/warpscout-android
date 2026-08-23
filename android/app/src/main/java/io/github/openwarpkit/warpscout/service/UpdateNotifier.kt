package io.github.openwarpkit.warpscout.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.openwarpkit.warpscout.MainActivity
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.data.AvailableUpdate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateNotifier @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager = context.getSystemService(NotificationManager::class.java)

    fun showAvailable(update: AvailableUpdate) {
        ensureChannel()
        val downloadIntent = Intent(context, UpdateNotificationReceiver::class.java)
            .setAction(UpdateNotificationReceiver.ACTION_DOWNLOAD)
        val dismissIntent = Intent(context, UpdateNotificationReceiver::class.java)
            .setAction(UpdateNotificationReceiver.ACTION_DISMISS)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle(context.getString(R.string.update_available_notification_title))
            .setContentText(context.getString(R.string.update_available, update.version))
            .setContentIntent(openAppIntent())
            .setDeleteIntent(
                PendingIntent.getBroadcast(
                    context,
                    REQUEST_DISMISS,
                    dismissIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .addAction(
                0,
                context.getString(R.string.update_now),
                PendingIntent.getBroadcast(
                    context,
                    REQUEST_DOWNLOAD,
                    downloadIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .build()
        notificationManager.notify(AVAILABLE_NOTIFICATION_ID, notification)
    }

    fun showReady(update: AvailableUpdate) {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle(context.getString(R.string.update_ready_title))
            .setContentText(context.getString(R.string.update_ready_notification, update.version))
            .setContentIntent(openAppIntent())
            .addAction(0, context.getString(R.string.install_update), openAppIntent())
            .setAutoCancel(true)
            .build()
        notificationManager.notify(READY_NOTIFICATION_ID, notification)
    }

    fun showFailed(update: AvailableUpdate) {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle(context.getString(R.string.update_download_failed_title))
            .setContentText(context.getString(R.string.update_download_failed_notification, update.version))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        notificationManager.notify(READY_NOTIFICATION_ID, notification)
    }

    fun showOperationBusy(update: AvailableUpdate) {
        ensureChannel()
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_status)
            .setContentTitle(context.getString(R.string.update_wait_operation_title))
            .setContentText(context.getString(R.string.update_wait_operation_message, update.version))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .build()
        notificationManager.notify(AVAILABLE_NOTIFICATION_ID, notification)
    }

    fun cancelAvailable() {
        notificationManager.cancel(AVAILABLE_NOTIFICATION_ID)
    }

    fun cancelAll() {
        notificationManager.cancel(AVAILABLE_NOTIFICATION_ID)
        notificationManager.cancel(READY_NOTIFICATION_ID)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        REQUEST_OPEN_APP,
        Intent(context, MainActivity::class.java)
            .setAction(ACTION_OPEN_UPDATE)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun ensureChannel() {
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_updates),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.notification_channel_updates_description)
            }
        )
    }

    companion object {
        const val ACTION_OPEN_UPDATE = "io.github.openwarpkit.warpscout.action.OPEN_UPDATE"
        private const val CHANNEL_ID = "updates"
        private const val AVAILABLE_NOTIFICATION_ID = 2001
        private const val READY_NOTIFICATION_ID = 2002
        private const val REQUEST_OPEN_APP = 20
        private const val REQUEST_DOWNLOAD = 21
        private const val REQUEST_DISMISS = 22
    }
}
