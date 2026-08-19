package io.github.openwarpkit.warpscout.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.openwarpkit.warpscout.core.CoreBridge
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
import io.github.openwarpkit.warpscout.data.UpdateChecker
import io.github.openwarpkit.warpscout.data.UpdateResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONObject
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    private val accountStore: AccountStore,
    private val settingsStore: SettingsStore,
    private val operations: OperationRepository,
    private val historyDao: HistoryDao,
    private val exportManager: ExportManager,
    private val toolResultStore: ToolResultStore,
    private val updateChecker: UpdateChecker,
    val coreBridge: CoreBridge
) : ViewModel() {
    private val mutableHasAccount = MutableStateFlow<Boolean?>(null)
    val hasAccount: StateFlow<Boolean?> = mutableHasAccount.asStateFlow()

    private val mutableAccountError = MutableStateFlow<String?>(null)
    val accountError: StateFlow<String?> = mutableAccountError.asStateFlow()

    private val mutableUpdateResult = MutableStateFlow<Result<UpdateResult>?>(null)
    val updateResult: StateFlow<Result<UpdateResult>?> = mutableUpdateResult.asStateFlow()

    private val mutableExportError = MutableStateFlow<String?>(null)
    val exportError: StateFlow<String?> = mutableExportError.asStateFlow()

    private val mutableConfigDocument = MutableStateFlow<Result<ConfigDocument>?>(null)
    val configDocument: StateFlow<Result<ConfigDocument>?> = mutableConfigDocument.asStateFlow()

    val operation = operations.state
    val history = historyDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val toolResults = toolResultStore.results.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        io.github.openwarpkit.warpscout.data.ToolSearchResults()
    )
    val settings = settingsStore.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    init {
        refreshAccount()
        viewModelScope.launch { historyDao.interruptUnfinished(System.currentTimeMillis()) }
        viewModelScope.launch {
            operation.collect { state ->
                if (!state.running && state.operation == "register") refreshAccount()
            }
        }
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
            mutableUpdateResult.value = runCatching { updateChecker.check() }
        }
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
            }.onSuccess {
                operations.clearFinished()
                mutableConfigDocument.value = null
                mutableUpdateResult.value = null
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
}
