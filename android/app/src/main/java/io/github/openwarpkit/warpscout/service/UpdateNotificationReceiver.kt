package io.github.openwarpkit.warpscout.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import io.github.openwarpkit.warpscout.core.OperationRepository
import io.github.openwarpkit.warpscout.data.UpdateDownloadController
import io.github.openwarpkit.warpscout.data.UpdateDownloadState
import io.github.openwarpkit.warpscout.data.UpdateStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class UpdateNotificationReceiver : BroadcastReceiver() {
    @Inject lateinit var updateStateStore: UpdateStateStore
    @Inject lateinit var downloadController: UpdateDownloadController
    @Inject lateinit var operations: OperationRepository
    @Inject lateinit var notifier: UpdateNotifier

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                when (intent.action) {
                    ACTION_DISMISS -> {
                        updateStateStore.dismissAutomatically(System.currentTimeMillis())
                        notifier.cancelAll()
                    }
                    ACTION_DOWNLOAD -> {
                        val update = updateStateStore.snapshot().availableUpdate ?: return@launch
                        if (operations.state.value.running) {
                            notifier.showOperationBusy(update)
                        } else {
                            downloadController.restore()
                            if (downloadController.state.value !is UpdateDownloadState.Idle) return@launch
                            notifier.cancelAvailable()
                            downloadController.start(update)
                            if (downloadController.state.value is UpdateDownloadState.Failed) {
                                notifier.showFailed(update)
                            }
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DOWNLOAD = "io.github.openwarpkit.warpscout.action.DOWNLOAD_UPDATE"
        const val ACTION_DISMISS = "io.github.openwarpkit.warpscout.action.DISMISS_UPDATE"
    }
}
