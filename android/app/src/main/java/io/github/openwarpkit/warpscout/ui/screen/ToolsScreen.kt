package io.github.openwarpkit.warpscout.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.core.OperationRequest
import io.github.openwarpkit.warpscout.data.TextDocument
import io.github.openwarpkit.warpscout.data.ToolSearchResult
import io.github.openwarpkit.warpscout.ui.AppViewModel
import io.github.openwarpkit.warpscout.ui.components.OperationPanel
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

private data class DiscoveryAttempt(
    val parameters: String,
    val working: Int,
    val total: Int,
    val completed: Boolean,
    val selected: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(viewModel: AppViewModel, onOpenScan: () -> Unit) {
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val results by viewModel.toolResults.collectAsStateWithLifecycle()
    val exportError by viewModel.exportError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var socksPort by rememberSaveable { mutableStateOf("1080") }
    var socksEndpoint by rememberSaveable { mutableStateOf("") }
    var expandedOperation by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingDocument by remember { mutableStateOf<TextDocument?>(null) }
    val saveLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val document = pendingDocument
        if (uri != null && document != null) viewModel.saveText(document, uri)
        pendingDocument = null
    }

    LaunchedEffect(exportError) {
        exportError?.let { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.tools_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ToolSection(
                title = stringResource(R.string.find_junk),
                description = stringResource(R.string.find_junk_description),
                enabled = !operation.running,
                lastResult = results.junk,
                expanded = expandedOperation == "find-junk",
                onToggleLast = {
                    expandedOperation = if (expandedOperation == "find-junk") null else "find-junk"
                },
                onStart = {
                    val payload = JSONObject()
                        .put("protocol", "awg")
                        .put("timeoutSec", 2)
                        .put("jobs", 10)
                        .put("samplePerSubnet", 3)
                        .put("tunnelPingCount", 10)
                        .toString()
                    viewModel.start(OperationRequest("find-junk", payload, protocol = "awg"))
                },
                onApply = { result ->
                    if (viewModel.applyToolResult(result)) onOpenScan()
                },
                onSave = { document ->
                    pendingDocument = document
                    saveLauncher.launch(document.fileName)
                },
                onShare = viewModel::shareText
            )
            HorizontalDivider()
            ToolSection(
                title = stringResource(R.string.find_sni),
                description = stringResource(R.string.find_sni_description),
                enabled = !operation.running,
                lastResult = results.sni,
                expanded = expandedOperation == "find-sni",
                onToggleLast = {
                    expandedOperation = if (expandedOperation == "find-sni") null else "find-sni"
                },
                onStart = {
                    val payload = JSONObject()
                        .put("protocol", "masque")
                        .put("timeoutSec", 2)
                        .put("jobs", 14)
                        .put("samplePerSubnet", 1)
                        .put("tunnelPingCount", 10)
                        .put("masqueAttempts", 3)
                        .toString()
                    viewModel.start(OperationRequest("find-sni", payload, protocol = "masque"))
                },
                onApply = { result ->
                    if (viewModel.applyToolResult(result)) onOpenScan()
                },
                onSave = { document ->
                    pendingDocument = document
                    saveLauncher.launch(document.fileName)
                },
                onShare = viewModel::shareText
            )
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.socks), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.socks_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = socksEndpoint,
                    onValueChange = { socksEndpoint = it },
                    label = { Text(stringResource(R.string.best_endpoint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = socksPort,
                    onValueChange = { socksPort = it },
                    label = { Text(stringResource(R.string.port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    enabled = !operation.running && socksEndpoint.isNotBlank() && socksPort.toIntOrNull() in 1..65535,
                    onClick = {
                        val payload = JSONObject()
                            .put("endpoint", socksEndpoint.trim())
                            .put("port", socksPort.toInt())
                            .put("protocol", "wg")
                            .put("scan", JSONObject().put("protocol", "wg").put("timeoutSec", 2))
                            .toString()
                        viewModel.start(OperationRequest("socks", payload, protocol = "wg"))
                    }
                ) { Text(stringResource(R.string.start)) }
            }
            if (operation.operation in setOf("find-junk", "find-sni", "socks")) {
                OperationPanel(operation, viewModel::stop, viewModel::dismissOperation)
            }
        }
    }
}

@Composable
private fun ToolSection(
    title: String,
    description: String,
    enabled: Boolean,
    lastResult: ToolSearchResult?,
    expanded: Boolean,
    onToggleLast: () -> Unit,
    onStart: () -> Unit,
    onApply: (ToolSearchResult) -> Unit,
    onSave: (TextDocument) -> Unit,
    onShare: (TextDocument) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onStart, enabled = enabled) { Text(stringResource(R.string.start)) }
            OutlinedButton(onClick = onToggleLast, enabled = lastResult != null) {
                Icon(Icons.Outlined.History, contentDescription = null)
                Text(stringResource(R.string.last_search), Modifier.padding(start = 8.dp))
            }
        }
        if (expanded && lastResult != null) {
            LastSearchPanel(lastResult, onApply, onSave, onShare)
        }
    }
}

@Composable
private fun LastSearchPanel(
    result: ToolSearchResult,
    onApply: (ToolSearchResult) -> Unit,
    onSave: (TextDocument) -> Unit,
    onShare: (TextDocument) -> Unit
) {
    val context = LocalContext.current
    val request = remember(result.requestJson) { runCatching { JSONObject(result.requestJson) }.getOrNull() }
    val payload = remember(result.resultJson) { runCatching { JSONObject(result.resultJson) }.getOrNull() }
    val requestParameters = remember(request, result.operation) { requestParameters(result.operation, request) }
    val selectedParameters = remember(payload, result.operation) { selectedParameters(result.operation, payload) }
    val attempts = remember(payload, result.operation) { discoveryAttempts(result.operation, payload) }
    val document = remember(result, requestParameters, selectedParameters, attempts) {
        discoveryDocument(result, requestParameters, selectedParameters, attempts)
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(result.finishedAt)),
                style = MaterialTheme.typography.labelLarge
            )
            result.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
            ParameterBlock(stringResource(R.string.search_parameters), requestParameters)
            if (selectedParameters != null) {
                ParameterBlock(stringResource(R.string.selected_parameters), selectedParameters)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TextButton(onClick = { copyText(context, selectedParameters) }) {
                        Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                        Text(stringResource(R.string.copy_parameters), Modifier.padding(start = 6.dp))
                    }
                    TextButton(onClick = { onApply(result) }) {
                        Text(stringResource(R.string.use_for_scan))
                    }
                }
            }
            Text(stringResource(R.string.tested_variants), style = MaterialTheme.typography.titleMedium)
            if (attempts.isEmpty()) {
                Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                attempts.forEachIndexed { index, attempt ->
                    DiscoveryAttemptRow(index + 1, attempt)
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(onClick = { onSave(document) }) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Text(stringResource(R.string.save_variants), Modifier.padding(start = 6.dp))
                }
                OutlinedButton(onClick = { onShare(document) }) {
                    Icon(Icons.Outlined.Share, contentDescription = null)
                    Text(stringResource(R.string.share_variants), Modifier.padding(start = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun ParameterBlock(title: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DiscoveryAttemptRow(index: Int, attempt: DiscoveryAttempt) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        if (attempt.selected) {
            Text(
                stringResource(R.string.selected_variant),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Text(
            "$index. ${attempt.parameters}",
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace
        )
        Text(
            if (attempt.completed) {
                stringResource(R.string.discovery_working_format, attempt.working, attempt.total)
            } else {
                stringResource(R.string.not_completed)
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun requestParameters(operation: String, request: JSONObject?): String {
    if (request == null) return "-"
    val keys = when (operation) {
        "find-junk" -> listOf("protocol", "timeoutSec", "jobs", "samplePerSubnet", "tunnelPingCount")
        else -> listOf("protocol", "timeoutSec", "jobs", "samplePerSubnet", "tunnelPingCount", "masqueAttempts")
    }
    return keys.mapNotNull { key ->
        request.opt(key).takeUnless { it == null || it == JSONObject.NULL }?.let { "$key=$it" }
    }.joinToString("  ").ifBlank { "-" }
}

private fun selectedParameters(operation: String, payload: JSONObject?): String? {
    payload ?: return null
    return when (operation) {
        "find-junk" -> {
            val count = payload.optInt("junkCount")
            if (count <= 0) null else buildString {
                append("Jc=$count  Jmin=${payload.optInt("junkMin")}  Jmax=${payload.optInt("junkMax")}")
                payload.optString("i1").takeIf(String::isNotBlank)?.let { append("\nI1=$it") }
            }
        }
        "find-sni" -> payload.optString("sni").takeIf(String::isNotBlank)?.let {
            "protocol=${payload.optString("protocol").ifBlank { "masque" }}  SNI=$it  attempts=${payload.optInt("attempts", 3)}"
        }
        else -> null
    }
}

private fun discoveryAttempts(operation: String, payload: JSONObject?): List<DiscoveryAttempt> {
    val tested = payload?.optJSONArray("tested") ?: return emptyList()
    return (0 until tested.length()).mapNotNull { index ->
        val item = tested.optJSONObject(index) ?: return@mapNotNull null
        val parameters = when (operation) {
            "find-junk" -> buildString {
                append("Jc=${item.optInt("junkCount")} Jmin=${item.optInt("junkMin")} Jmax=${item.optInt("junkMax")}")
                item.optString("i1").takeIf(String::isNotBlank)?.let { append(" I1=$it") }
            }
            else -> "SNI=${item.optString("sni")}"
        }
        DiscoveryAttempt(
            parameters = parameters,
            working = item.optInt("working"),
            total = item.optInt("total"),
            completed = item.optBoolean("completed"),
            selected = item.optBoolean("selected")
        )
    }
}

private fun discoveryDocument(
    result: ToolSearchResult,
    requestParameters: String,
    selectedParameters: String?,
    attempts: List<DiscoveryAttempt>
): TextDocument {
    val toolName = if (result.operation == "find-junk") "AWG Junk" else "MASQUE SNI"
    val timestamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(result.finishedAt))
    val content = buildString {
        appendLine("WarpScout for Android")
        appendLine(toolName)
        appendLine(timestamp)
        appendLine()
        appendLine("Status: ${result.status}")
        result.errorMessage?.let { appendLine("Error: $it") }
        appendLine("Search: $requestParameters")
        appendLine("Selected: ${selectedParameters ?: "-"}")
        appendLine()
        appendLine("Tested variants:")
        attempts.forEachIndexed { index, attempt ->
            val status = if (attempt.completed) "${attempt.working}/${attempt.total} working" else "not completed"
            val selected = if (attempt.selected) " selected" else ""
            appendLine("${index + 1}. ${attempt.parameters} | $status$selected")
        }
    }
    return TextDocument(
        fileName = "warpscout-${result.operation}-${result.finishedAt}.txt",
        content = content
    )
}

private fun copyText(context: Context, value: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText("WarpScout parameters", value))
}
