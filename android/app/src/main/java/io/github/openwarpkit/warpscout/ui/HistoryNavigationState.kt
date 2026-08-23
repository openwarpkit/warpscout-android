package io.github.openwarpkit.warpscout.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal const val SCAN_ROUTE = "scan"
internal const val HISTORY_ROUTE = "history"

data class HistoryFocusRequest(
    val requestId: Long,
    val historyId: Long
)

internal data class HistoryNavigationRequest(
    val focus: HistoryFocusRequest,
    val destination: String = HISTORY_ROUTE,
    val clearSavedHistory: Boolean = true,
    val savePoppedState: Boolean = false,
    val restoreState: Boolean = false
)

internal data class PrimaryNavigationOptions(
    val savePoppedState: Boolean,
    val restoreState: Boolean
)

internal sealed interface HistoryFocusResolution {
    data object Waiting : HistoryFocusResolution
    data class Found(val request: HistoryFocusRequest, val index: Int) : HistoryFocusResolution
    data class Missing(val request: HistoryFocusRequest) : HistoryFocusResolution
}

internal class HistoryNavigationState {
    private var nextRequestId = 0L

    var pendingFocus by mutableStateOf<HistoryFocusRequest?>(null)
        private set

    fun openCompletedScan(historyId: Long): HistoryNavigationRequest {
        val focus = HistoryFocusRequest(++nextRequestId, historyId)
        pendingFocus = focus
        return HistoryNavigationRequest(focus)
    }

    fun consumeFocus(request: HistoryFocusRequest): Boolean {
        if (pendingFocus != request) return false
        pendingFocus = null
        return true
    }
}

internal fun resolveHistoryFocus(
    request: HistoryFocusRequest,
    historyLoaded: Boolean,
    historyIds: List<Long>
): HistoryFocusResolution {
    if (!historyLoaded) return HistoryFocusResolution.Waiting
    val index = historyIds.indexOf(request.historyId)
    return if (index >= 0) {
        HistoryFocusResolution.Found(request, index)
    } else {
        HistoryFocusResolution.Missing(request)
    }
}

internal fun primaryNavigationOptions(route: String): PrimaryNavigationOptions {
    val preserveDestinationState = route != SCAN_ROUTE
    return PrimaryNavigationOptions(
        savePoppedState = preserveDestinationState,
        restoreState = preserveDestinationState
    )
}
