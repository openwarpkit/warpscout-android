package io.github.openwarpkit.warpscout.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dagger.hilt.android.AndroidEntryPoint
import io.github.openwarpkit.warpscout.MainActivity
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.core.CoreBridge
import io.github.openwarpkit.warpscout.core.OperationRepository
import io.github.openwarpkit.warpscout.core.OperationState
import io.github.openwarpkit.warpscout.data.AccountStore
import io.github.openwarpkit.warpscout.data.HistoryDao
import io.github.openwarpkit.warpscout.data.HistoryEntity
import io.github.openwarpkit.warpscout.data.ToolResultStore
import io.github.openwarpkit.warpscout.data.ToolSearchResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject

@AndroidEntryPoint
class OperationService : Service() {
    @Inject lateinit var bridge: CoreBridge
    @Inject lateinit var operations: OperationRepository
    @Inject lateinit var accountStore: AccountStore
    @Inject lateinit var historyDao: HistoryDao
    @Inject lateinit var toolResultStore: ToolResultStore

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var activeJob: Job? = null
    private var activeHistoryId: Long? = null
    private var activePayloadJson: String = "{}"
    private var pendingRegisteredAccount: String? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val stoppedByUser = AtomicBoolean(false)
    private val stoppedByNetwork = AtomicBoolean(false)
    private val finishing = AtomicBoolean(false)

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch { historyDao.interruptUnfinished(System.currentTimeMillis()) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopActiveOperation()
            ACTION_START -> startOperation(intent)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        bridge.cancel()
        unregisterNetworkCallback()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startOperation(intent: Intent) {
        if (activeJob?.isActive == true || operations.state.value.running) return
        val operation = intent.getStringExtra(EXTRA_OPERATION) ?: return
        val payload = intent.getStringExtra(EXTRA_PAYLOAD) ?: "{}"
        val preset = intent.getStringExtra(EXTRA_PRESET).orEmpty()
        val protocol = intent.getStringExtra(EXTRA_PROTOCOL).orEmpty()
        val localPort = intent.getIntExtra(EXTRA_LOCAL_PORT, 0).takeIf { it in 1..65535 }
        stoppedByUser.set(false)
        stoppedByNetwork.set(false)
        finishing.set(false)
        pendingRegisteredAccount = null
        activePayloadJson = payload

        val initial = OperationState(
            running = true,
            operation = operation,
            phase = getString(R.string.phase_preparing),
            startedAt = System.currentTimeMillis(),
            localPort = localPort
        )
        operations.setState(initial)
        startForeground(NOTIFICATION_ID, notification(initial))
        registerNetworkCallback()

        activeJob = serviceScope.launch {
            if (operation == "scan") {
                activeHistoryId = historyDao.insert(
                    HistoryEntity(
                        operation = operation,
                        protocol = protocol,
                        preset = preset,
                        status = "Running",
                        startedAt = initial.startedAt,
                        finishedAt = null,
                        progressCompleted = 0,
                        progressTotal = 0,
                        workingCount = 0,
                        tornDownCount = 0,
                        bestEndpoint = null,
                        optionsJson = payload,
                        resultJson = null
                    )
                )
                operations.update { it.copy(historyId = activeHistoryId) }
            }

            val error = runCatching {
                val accountJson = if (operation == "register") "" else accountStore.read().orEmpty()
                if (operation != "register" && accountJson.isBlank()) {
                    throw IllegalStateException(getString(R.string.account_required))
                }
                val request = JSONObject()
                    .put("schemaVersion", 1)
                    .put("operation", operation)
                    .put("accountJson", accountJson)
                    .put("payload", JSONObject(payload))
                    .toString()
                bridge.start(request, ::handleEvent)
            }.exceptionOrNull()

            finishOperation(error)
        }
    }

    private fun handleEvent(eventJson: String) {
        val event = runCatching { JSONObject(eventJson) }.getOrNull() ?: return
        when (event.optString("type")) {
            "error" -> {
                val error = event.optJSONObject("error") ?: return
                operations.update {
                    it.copy(
                        errorCode = error.optString("code"),
                        errorMessage = error.optString("message"),
                        latestResultJson = error.opt("payload")?.toString() ?: it.latestResultJson
                    )
                }
            }
            "completed" -> {
                val payload = event.opt("payload")
                val payloadJson = payload?.toString()
                val currentOperation = operations.state.value.operation
                if (currentOperation == "register") {
                    val accountJson = (payload as? JSONObject)?.optString("rawJson").orEmpty()
                    if (accountJson.isNotBlank()) {
                        pendingRegisteredAccount = accountJson
                    }
                }
                if (currentOperation == "scan" && payload is JSONObject) {
                    val results = payload.optJSONArray("results")
                    var working = 0
                    var tornDown = 0
                    var bestEndpoint: String? = null
                    if (results != null) {
                        for (index in 0 until results.length()) {
                            val endpoint = results.optJSONObject(index) ?: continue
                            if (!endpoint.optBoolean("working")) continue
                            if (endpoint.optBoolean("durable")) {
                                working++
                                if (bestEndpoint == null) bestEndpoint = endpoint.optString("endpoint").ifBlank { null }
                            } else {
                                tornDown++
                            }
                        }
                    }
                    operations.update {
                        it.copy(
                            latestResultJson = payloadJson,
                            working = working,
                            tornDown = tornDown,
                            bestEndpoint = bestEndpoint
                        )
                    }
                } else {
                    operations.update { it.copy(latestResultJson = payloadJson) }
                }
            }
            else -> updateProgress(event)
        }
        updateNotification()
    }

    private fun updateProgress(event: JSONObject) {
        operations.update { current ->
            val endpoint = event.optJSONObject("endpoint")
            val working = endpoint?.optBoolean("working") == true
            val durable = endpoint?.optBoolean("durable") != false
            val region = endpoint?.optString("region").orEmpty()
            val node = endpoint?.optString("node").orEmpty()
            current.copy(
                phase = localizedPhase(event.optString("phase"))
                    .ifBlank { event.optString("message").ifBlank { current.phase } },
                completed = event.optInt("completed", current.completed),
                total = event.optInt("total", current.total),
                working = current.working + if (working && durable) 1 else 0,
                tornDown = current.tornDown + if (working && !durable) 1 else 0,
                regions = if (region.isBlank()) current.regions else current.regions + region,
                nodes = if (node.isBlank()) current.nodes else current.nodes + node
            )
        }
    }

    private fun localizedPhase(value: String): String = when {
        value == "registration" -> getString(R.string.phase_registration)
        value.startsWith("Phase 1") -> getString(R.string.phase_port_discovery)
        value.startsWith("Phase 2") -> getString(R.string.phase_tunnel_verification)
        value.startsWith("Speedtest") -> getString(R.string.phase_speed_test)
        value == "junk" -> getString(R.string.phase_find_junk)
        value == "sni" -> getString(R.string.phase_find_sni)
        value == "handshake" -> getString(R.string.phase_handshake)
        value == "listening" -> getString(R.string.phase_listening)
        value == "Through" -> getString(R.string.phase_outer_tunnel)
        value == "Port" -> getString(R.string.phase_port_selected)
        else -> value
    }

    private suspend fun finishOperation(error: Throwable?) {
        if (!finishing.compareAndSet(false, true)) return
        val persistenceError = if (error == null && operations.state.value.operation == "register") {
            runCatching {
                val accountJson = pendingRegisteredAccount ?: error(getString(R.string.operation_failed))
                accountStore.write(accountJson)
            }.exceptionOrNull()
        } else {
            null
        }
        val operationError = error ?: persistenceError
        val previous = operations.state.value
        val interrupted = stoppedByUser.get() || stoppedByNetwork.get()
        val status = when {
            interrupted -> "Interrupted"
            operationError != null || previous.errorMessage != null -> "Failed"
            else -> "Completed"
        }
        val finalState = previous.copy(
            running = false,
            errorCode = when {
                stoppedByNetwork.get() -> "network_lost"
                stoppedByUser.get() -> null
                operationError != null && previous.errorCode == null -> "operation_failed"
                else -> previous.errorCode
            },
            errorMessage = when {
                stoppedByNetwork.get() -> getString(R.string.network_lost)
                stoppedByUser.get() -> null
                operationError != null && previous.errorMessage == null -> operationError.message ?: getString(R.string.operation_failed)
                else -> previous.errorMessage
            }
        )
        operations.setState(finalState)
        val finishedAt = System.currentTimeMillis()
        activeHistoryId?.let { id ->
            historyDao.find(id)?.let { item ->
                historyDao.update(
                    item.copy(
                        status = status,
                        finishedAt = finishedAt,
                        progressCompleted = finalState.completed,
                        progressTotal = finalState.total,
                        workingCount = finalState.working,
                        tornDownCount = finalState.tornDown,
                        bestEndpoint = finalState.bestEndpoint,
                        resultJson = finalState.latestResultJson
                    )
                )
            }
        }
        if (
            finalState.operation in setOf("find-junk", "find-sni") &&
            !finalState.latestResultJson.isNullOrBlank()
        ) {
            toolResultStore.save(
                ToolSearchResult(
                    operation = finalState.operation,
                    requestJson = activePayloadJson,
                    resultJson = finalState.latestResultJson,
                    status = status,
                    finishedAt = finishedAt,
                    errorCode = finalState.errorCode,
                    errorMessage = finalState.errorMessage
                )
            )
        }
        activeHistoryId = null
        activePayloadJson = "{}"
        pendingRegisteredAccount = null
        activeJob = null
        unregisterNetworkCallback()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun stopActiveOperation() {
        stoppedByUser.set(true)
        bridge.stop()
    }

    private fun registerNetworkCallback() {
        val connectivity = getSystemService(ConnectivityManager::class.java)
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onLost(network: Network) {
                if (connectivity.activeNetwork == null && operations.state.value.running) {
                    stoppedByNetwork.set(true)
                    bridge.cancel()
                }
            }
        }
        networkCallback = callback
        connectivity.registerDefaultNetworkCallback(callback)
    }

    private fun unregisterNetworkCallback() {
        val callback = networkCallback ?: return
        runCatching { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(callback) }
        networkCallback = null
    }

    private fun updateNotification() {
        val state = operations.state.value
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
    }

    private fun notification(state: OperationState) = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_status)
        .setContentTitle(getString(R.string.active_operation))
        .setContentText(state.phase.ifBlank { getString(R.string.notification_preparing) })
        .setOnlyAlertOnce(true)
        .setOngoing(state.running)
        .setProgress(state.total, state.completed, state.total == 0)
        .setContentIntent(
            PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .addAction(
            0,
            getString(R.string.stop_operation),
            PendingIntent.getService(
                this,
                1,
                Intent(this, OperationService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        )
        .build()

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_operations),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = getString(R.string.notification_channel_description)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val ACTION_START = "io.github.openwarpkit.warpscout.action.START"
        const val ACTION_STOP = "io.github.openwarpkit.warpscout.action.STOP"
        const val EXTRA_OPERATION = "operation"
        const val EXTRA_PAYLOAD = "payload"
        const val EXTRA_PRESET = "preset"
        const val EXTRA_PROTOCOL = "protocol"
        const val EXTRA_LOCAL_PORT = "local_port"
        private const val CHANNEL_ID = "operations"
        private const val NOTIFICATION_ID = 1001
    }
}
