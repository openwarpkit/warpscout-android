package io.github.openwarpkit.warpscout.core

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import io.github.openwarpkit.warpscout.service.OperationService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OperationRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val mutableState = MutableStateFlow(OperationState())
    val state: StateFlow<OperationState> = mutableState.asStateFlow()

    fun start(request: OperationRequest): Boolean {
        if (mutableState.value.running) return false
        val intent = Intent(context, OperationService::class.java)
            .setAction(OperationService.ACTION_START)
            .putExtra(OperationService.EXTRA_OPERATION, request.operation)
            .putExtra(OperationService.EXTRA_PAYLOAD, request.payloadJson)
            .putExtra(OperationService.EXTRA_PRESET, request.preset)
            .putExtra(OperationService.EXTRA_PROTOCOL, request.protocol)
        ContextCompat.startForegroundService(context, intent)
        return true
    }

    fun stop() {
        context.startService(
            Intent(context, OperationService::class.java).setAction(OperationService.ACTION_STOP)
        )
    }

    fun clearFinished() {
        if (!mutableState.value.running) {
            mutableState.value = OperationState()
        }
    }

    internal fun setState(state: OperationState) {
        mutableState.value = state
    }

    internal fun update(transform: (OperationState) -> OperationState) {
        mutableState.value = transform(mutableState.value)
    }
}
