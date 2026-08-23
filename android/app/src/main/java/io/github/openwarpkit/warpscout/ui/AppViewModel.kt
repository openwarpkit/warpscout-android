package io.github.openwarpkit.warpscout.ui

import android.net.Uri
import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.openwarpkit.warpscout.core.CoreBridge
import io.github.openwarpkit.warpscout.BuildConfig
import io.github.openwarpkit.warpscout.core.OperationRepository
import io.github.openwarpkit.warpscout.core.OperationRequest
import io.github.openwarpkit.warpscout.data.AccountStore
import io.github.openwarpkit.warpscout.data.AppSettings
import io.github.openwarpkit.warpscout.data.ConfigDocument
import io.github.openwarpkit.warpscout.data.ExportManager
import io.github.openwarpkit.warpscout.data.HistoryDao
import io.github.openwarpkit.warpscout.data.HistoryEntity
import io.github.openwarpkit.warpscout.data.ReportImageDocument
import io.github.openwarpkit.warpscout.data.SettingsStore
import io.github.openwarpkit.warpscout.data.ToolResultStore
import io.github.openwarpkit.warpscout.data.ToolScanProfile
import io.github.openwarpkit.warpscout.data.ToolSearchResult
import io.github.openwarpkit.warpscout.data.TextDocument
import io.github.openwarpkit.warpscout.data.toScanProfile
import io.github.openwarpkit.warpscout.data.UpdateChecker
import io.github.openwarpkit.warpscout.data.AvailableUpdate
import io.github.openwarpkit.warpscout.data.StoredUpdateState
import io.github.openwarpkit.warpscout.data.UpdateDownloadController
import io.github.openwarpkit.warpscout.data.UpdateDownloadState
import io.github.openwarpkit.warpscout.data.UpdateInstallLaunch
import io.github.openwarpkit.warpscout.data.UpdateInstaller
import io.github.openwarpkit.warpscout.data.UpdateResult
import io.github.openwarpkit.warpscout.data.UpdateStateStore
import io.github.openwarpkit.warpscout.data.compareVersions
import io.github.openwarpkit.warpscout.data.shouldAutomaticallyCheck
import io.github.openwarpkit.warpscout.data.toAvailableUpdate
import io.github.openwarpkit.warpscout.service.UpdateNotifier
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

data class HistorySnapshot(
    val loaded: Boolean = false,
    val items: List<HistoryEntity> = emptyList()
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val accountStore: AccountStore,
    private val settingsStore: SettingsStore,
    private val operations: OperationRepository,
    private val historyDao: HistoryDao,
    private val exportManager: ExportManager,
    private val toolResultStore: ToolResultStore,
    private val updateChecker: UpdateChecker,
    private val updateStateStore: UpdateStateStore,
    private val updateDownloadController: UpdateDownloadController,
    private val updateInstaller: UpdateInstaller,
    private val updateNotifier: UpdateNotifier,
    savedStateHandle: SavedStateHandle,
    val coreBridge: CoreBridge
) : ViewModel() {
    private val socksFormStateStore = SocksFormStateStore(savedStateHandle)

    private val mutableHasAccount = MutableStateFlow<Boolean?>(null)
    val hasAccount: StateFlow<Boolean?> = mutableHasAccount.asStateFlow()

    private val mutableAccountError = MutableStateFlow<String?>(null)
    val accountError: StateFlow<String?> = mutableAccountError.asStateFlow()

    private val mutableUpdateResult = MutableStateFlow<Result<UpdateResult>?>(null)
    val updateResult: StateFlow<Result<UpdateResult>?> = mutableUpdateResult.asStateFlow()

    private val mutableUpdateChecking = MutableStateFlow(false)
    val updateChecking: StateFlow<Boolean> = mutableUpdateChecking.asStateFlow()

    private val mutableUpdatePrompt = MutableStateFlow<AvailableUpdate?>(null)
    val updatePrompt: StateFlow<AvailableUpdate?> = mutableUpdatePrompt.asStateFlow()

    val storedUpdate = updateStateStore.state.stateIn(
        viewModelScope,
        SharingStarted.Eagerly,
        StoredUpdateState()
    )
    val updateDownloadState = updateDownloadController.state
    private var updateMonitorJob: Job? = null

    private val mutableExportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = mutableExportError.asStateFlow()

    private val mutableConfigDocument = MutableStateFlow<Result<ConfigDocument>?>(null)
    val configDocument: StateFlow<Result<ConfigDocument>?> = mutableConfigDocument.asStateFlow()

    private val mutableToolScanProfile = MutableStateFlow<ToolScanProfile?>(null)
    val toolScanProfile: StateFlow<ToolScanProfile?> = mutableToolScanProfile.asStateFlow()

    val operation = operations.state
    val historySnapshot = historyDao.observeAll()
        .map { HistorySnapshot(loaded = true, items = it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistorySnapshot())
    val history = historySnapshot
        .map { it.items }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<HistoryEntity>())
    val toolResults = toolResultStore.results.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        io.github.openwarpkit.warpscout.data.ToolSearchResults()
    )
    val settings = settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val socksProtocol = socksFormStateStore.protocol
    val socksEndpoint = socksFormStateStore.endpoint
    val socksPort = socksFormStateStore.port

    init {
        refreshAccount()
        viewModelScope.launch { historyDao.interruptUnfinished(System.currentTimeMillis()) }
        viewModelScope.launch {
            operation.collect { state ->
                if (!state.running && state.operation == "register") refreshAccount()
            }
        }
        viewModelScope.launch { initializeUpdates() }
    }

    fun importAccount(value: String) {
        viewModelScope.launch {
            runCatching { accountStore.write(value) }
                .onSuccess {
                    mutableAccountError.value = null
                    mutableHasAccount.value = true
                }
                .onFailure { mutableAccountError.value = "invalid_account" }
        }
    }

    fun registerAccount() {
        val currentSettings = settings.value
        val payload = JSONObject()
            .put("proxy", "")
            .put("relay", if (currentSettings.relayEnabled) currentSettings.relayUrl else "none")
            .put("fresh", true)
            .put("timeoutSec", 2)
            .put("ipv6", false)
            .toString()
        operations.start(OperationRequest("register", payload))
    }

    fun start(request: OperationRequest): Boolean = operations.start(request)

    fun stop() = operations.stop()

    fun dismissOperation() = operations.clearFinished()

    fun setSocksProtocol(value: String) = socksFormStateStore.setProtocol(value)

    fun setSocksEndpoint(value: String) = socksFormStateStore.setEndpoint(value)

    fun setSocksPort(value: String) = socksFormStateStore.setPort(value)

    fun setRelayEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setRelayEnabled(enabled) }
    }

    fun setRelayUrl(value: String) {
        viewModelScope.launch { settingsStore.setRelayUrl(value) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setDynamicColor(enabled) }
    }

    fun checkUpdates() {
        mutableUpdateResult.value = null
        viewModelScope.launch {
            mutableUpdateChecking.value = true
            val result = runCatching { updateChecker.check() }
            mutableUpdateResult.value = result
            result.getOrNull()?.let { checked ->
                val available = checked.toAvailableUpdate()
                if (available != null) {
                    updateStateStore.saveAvailableUpdate(available)
                }
            }
            mutableUpdateChecking.value = false
        }
    }

    fun dismissUpdatePrompt() {
        mutableUpdatePrompt.value = null
        updateNotifier.cancelAll()
        viewModelScope.launch { updateStateStore.dismissAutomatically(System.currentTimeMillis()) }
    }

    fun startUpdate() {
        if (operation.value.running || updateDownloadState.value !is UpdateDownloadState.Idle) return
        viewModelScope.launch {
            val update = updateStateStore.snapshot().availableUpdate ?: return@launch
            mutableUpdatePrompt.value = null
            updateNotifier.cancelAvailable()
            if (updateDownloadController.start(update)) startUpdateMonitor()
        }
    }

    fun cancelUpdate() {
        updateMonitorJob?.cancel()
        viewModelScope.launch { updateDownloadController.cancel() }
    }

    fun retryUpdate() {
        viewModelScope.launch {
            val update = updateStateStore.snapshot().availableUpdate ?: return@launch
            if (updateDownloadController.start(update)) startUpdateMonitor()
        }
    }

    fun installUpdate(context: Context): UpdateInstallLaunch {
        val ready = updateDownloadState.value as? UpdateDownloadState.Ready
            ?: return UpdateInstallLaunch.Failed
        return updateInstaller.launch(context, ready.update)
    }

    fun shareReport(item: HistoryEntity) {
        if (operation.value.running) return
        mutableExportError.value = null
        viewModelScope.launch {
            runCatching { exportManager.shareReport(item) }
                .onFailure { mutableExportError.value = it.message }
        }
    }

    fun openReport(item: HistoryEntity) {
        if (operation.value.running) return
        mutableExportError.value = null
        viewModelScope.launch {
            runCatching { exportManager.openReport(item) }
                .onFailure { mutableExportError.value = it.message }
        }
    }

    fun shareReportImage(document: ReportImageDocument) {
        if (operation.value.running) return
        mutableExportError.value = null
        viewModelScope.launch {
            runCatching { exportManager.shareReportImage(document) }
                .onFailure { mutableExportError.value = it.message }
        }
    }

    fun saveReportImage(document: ReportImageDocument, uri: Uri) {
        if (operation.value.running) return
        mutableExportError.value = null
        viewModelScope.launch {
            runCatching { exportManager.saveReportImage(document, uri) }
                .onFailure { mutableExportError.value = it.message }
        }
    }

    fun shareText(document: TextDocument) {
        mutableExportError.value = null
        viewModelScope.launch {
            runCatching { exportManager.shareText(document) }
                .onFailure { mutableExportError.value = it.message }
        }
    }

    fun saveText(document: TextDocument, uri: Uri) {
        mutableExportError.value = null
        viewModelScope.launch {
            runCatching { exportManager.saveText(document, uri) }
                .onFailure { mutableExportError.value = it.message }
        }
    }

    fun applyToolResult(result: ToolSearchResult): Boolean {
        val profile = result.toScanProfile() ?: return false
        mutableToolScanProfile.value = profile
        return true
    }

    fun consumeToolScanProfile(profile: ToolScanProfile) {
        if (mutableToolScanProfile.value == profile) mutableToolScanProfile.value = null
    }

    fun loadConfig(item: HistoryEntity, format: String) {
        if (operation.value.running) return
        mutableExportError.value = null
        mutableConfigDocument.value = null
        viewModelScope.launch {
            mutableConfigDocument.value = runCatching { exportManager.renderConfig(item, format) }
        }
    }

    fun shareConfig(document: ConfigDocument) {
        mutableExportError.value = null
        viewModelScope.launch {
            runCatching { exportManager.shareConfig(document) }
                .onFailure { mutableExportError.value = it.message }
        }
    }

    fun saveConfig(document: ConfigDocument, uri: Uri) {
        mutableExportError.value = null
        viewModelScope.launch {
            runCatching { exportManager.saveConfig(document, uri) }
                .onFailure { mutableExportError.value = it.message }
        }
    }

    fun clearApplicationData() {
        if (operation.value.running) return
        mutableExportError.value = null
        viewModelScope.launch {
            runCatching {
                accountStore.clear()
                historyDao.clearAll()
                settingsStore.clear()
                toolResultStore.clear()
                exportManager.clearCache()
                updateDownloadController.clearAll()
                updateNotifier.cancelAll()
            }.onSuccess {
                operations.clearFinished()
                mutableConfigDocument.value = null
                mutableToolScanProfile.value = null
                mutableUpdateResult.value = null
                mutableUpdatePrompt.value = null
                mutableAccountError.value = null
                mutableHasAccount.value = false
            }.onFailure {
                mutableExportError.value = it.message
            }
        }
    }

    fun clearExportError() {
        mutableExportError.value = null
    }

    private fun refreshAccount() {
        viewModelScope.launch { mutableHasAccount.value = accountStore.hasAccount() }
    }

    private suspend fun initializeUpdates() {
        val installedVersion = BuildConfig.VERSION_NAME.substringBefore('-')
        val stored = updateStateStore.snapshot()
        if (stored.availableUpdate != null && compareVersions(stored.availableUpdate.version, installedVersion) <= 0) {
            clearStoredUpdate()
        } else {
            updateDownloadController.restore()
            if (updateDownloadState.value is UpdateDownloadState.Downloading) startUpdateMonitor()
        }
        val current = updateStateStore.snapshot()
        if (!shouldAutomaticallyCheck(System.currentTimeMillis(), current.dismissedUntilMillis)) return
        if (updateDownloadState.value !is UpdateDownloadState.Idle) return
        val checked = runCatching { updateChecker.check() }.getOrNull()
        val available = checked?.toAvailableUpdate()
        when {
            available != null -> {
                updateStateStore.saveAvailableUpdate(available)
                mutableUpdatePrompt.value = available
                updateNotifier.showAvailable(available)
            }
            current.availableUpdate != null -> {
                mutableUpdatePrompt.value = current.availableUpdate
                updateNotifier.showAvailable(current.availableUpdate)
            }
        }
    }

    private fun startUpdateMonitor() {
        updateMonitorJob?.cancel()
        updateMonitorJob = viewModelScope.launch {
            updateDownloadController.monitorUntilTerminal()
            when (val state = updateDownloadState.value) {
                is UpdateDownloadState.Ready -> updateNotifier.showReady(state.update)
                is UpdateDownloadState.Failed -> updateNotifier.showFailed(state.update)
                else -> Unit
            }
        }
    }

    private suspend fun clearStoredUpdate() {
        updateMonitorJob?.cancel()
        updateDownloadController.clearAll()
        updateNotifier.cancelAll()
        mutableUpdatePrompt.value = null
    }
}
