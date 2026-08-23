package io.github.openwarpkit.warpscout.service

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.openwarpkit.warpscout.data.UpdateDownloadController
import io.github.openwarpkit.warpscout.data.UpdateDownloadState
import io.github.openwarpkit.warpscout.data.UpdateStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UpdateDownloadCompleteReceiver : BroadcastReceiver() {
    @Inject lateinit var updateStateStore: UpdateStateStore
    @Inject lateinit var downloadController: UpdateDownloadController
    @Inject lateinit var notifier: UpdateNotifier

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val completedId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0)
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val stored = updateStateStore.snapshot()
                if (completedId <= 0 || completedId != stored.downloadId) return@launch
                when (val state = downloadController.refresh()) {
                    is UpdateDownloadState.Ready -> notifier.showReady(state.update)
                    is UpdateDownloadState.Failed -> notifier.showFailed(state.update)
                    else -> Unit
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
