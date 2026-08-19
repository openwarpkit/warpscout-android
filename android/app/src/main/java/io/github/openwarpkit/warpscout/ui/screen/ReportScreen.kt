package io.github.openwarpkit.warpscout.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.data.HistoryEntity
import io.github.openwarpkit.warpscout.ui.AppViewModel
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

internal data class ReportEndpoint(
    val endpoint: String,
    val region: String,
    val node: String,
    val country: String,
    val nodeLocation: String,
    val endpointPingMs: Double,
    val tunnelPingMs: Double,
    val lossPercent: Double,
    val speedMbps: Double,
    val working: Boolean,
    val durable: Boolean
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: AppViewModel,
    historyId: Long,
    onBack: () -> Unit,
    onViewConfig: (String) -> Unit
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val exportError by viewModel.exportError.collectAsStateWithLifecycle()
    val item = history.firstOrNull { it.id == historyId }
    val snackbar = remember { SnackbarHostState() }
    val date = item?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.startedAt))
    }

    LaunchedEffect(exportError) {
        val message = exportError ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.clearExportError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (date == null) {
                            stringResource(R.string.report_title)
                        } else {
                            stringResource(R.string.report_title_with_date, date)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (item == null || item.resultJson == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                Text(stringResource(R.string.no_results))
            }
        } else {
            ReportContent(
                item = item,
                actionsEnabled = !operation.running,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onOpen = { viewModel.openReport(item) },
                onShare = { viewModel.shareReport(item) },
                onViewConfig = onViewConfig
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportContent(
    item: HistoryEntity,
    actionsEnabled: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onViewConfig: (String) -> Unit
) {
    val results = remember(item.resultJson) { parseReport(item.resultJson.orEmpty()) }
    val working = results.count { it.working && it.durable }
    val tableScroll = rememberScrollState()
    var tableExpanded by rememberSaveable(item.id) { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        item("report-summary") {
            ReportSection(
                title = stringResource(R.string.report_section),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.report_summary, working, results.size),
                    style = MaterialTheme.typography.bodyLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onOpen, enabled = actionsEnabled) {
                        Text(stringResource(R.string.open_txt))
                    }
                    OutlinedButton(onClick = onShare, enabled = actionsEnabled) {
                        Text(stringResource(R.string.share_txt))
                    }
                }
                OutlinedButton(onClick = { tableExpanded = !tableExpanded }) {
                    Icon(
                        if (tableExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null
                    )
                    Text(
                        stringResource(
                            if (tableExpanded) R.string.hide_report_table else R.string.show_report_table
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            HorizontalDivider()
        }
        if (tableExpanded) {
            item("report-header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(tableScroll)
                ) {
                    ReportHeader()
                }
            }
            items(results, key = { "report-${it.endpoint}" }) { endpoint ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(tableScroll)
                ) {
                    ReportTableRow(endpoint)
                }
                HorizontalDivider()
            }
        }
        item("best-endpoint") {
            ReportSection(
                title = stringResource(R.string.best_endpoint),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                BestEndpointDetails(item)
            }
            HorizontalDivider()
        }
        item("configuration") {
            ReportSection(
                title = stringResource(R.string.configuration_section),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (item.protocol.lowercase()) {
                        "wg" -> ConfigButton(
                            R.string.wireguard_config,
                            "wireguard",
                            actionsEnabled,
                            onViewConfig
                        )
                        "awg" -> ConfigButton(
                            R.string.amneziawg_config,
                            "amneziawg",
                            actionsEnabled,
                            onViewConfig
                        )
                        "masque", "masque-h2" -> ConfigButton(
                            R.string.usque_config,
                            "usque",
                            actionsEnabled,
                            onViewConfig
                        )
                    }
                    ConfigButton(R.string.mihomo_config, "mihomo", actionsEnabled, onViewConfig)
                }
            }
        }
    }
}

@Composable
private fun ReportSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun BestEndpointDetails(item: HistoryEntity) {
    val context = LocalContext.current
    val endpoint = remember(item.resultJson, item.bestEndpoint) {
        parseReport(item.resultJson.orEmpty()).firstOrNull { it.endpoint == item.bestEndpoint }
    }
    if (endpoint == null) {
        Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    endpoint.endpoint,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("WARPSCOUT endpoint", endpoint.endpoint))
                }) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = stringResource(R.string.copy)
                    )
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                EndpointMetric(stringResource(R.string.endpoint_ping), formatLatency(endpoint.endpointPingMs))
                EndpointMetric(stringResource(R.string.tunnel_ping), formatLatency(endpoint.tunnelPingMs))
                EndpointMetric(
                    stringResource(R.string.loss),
                    formatPercent(endpoint.lossPercent, endpoint.tunnelPingMs > 0)
                )
                EndpointMetric(stringResource(R.string.seen_as), withFlag(endpoint.region, endpoint.region))
                EndpointMetric(stringResource(R.string.node), endpoint.node.ifBlank { "-" })
                EndpointMetric(
                    stringResource(R.string.node_location),
                    withFlag(endpoint.country, endpoint.nodeLocation)
                )
                EndpointMetric(stringResource(R.string.speed), formatSpeed(endpoint.speedMbps))
            }
        }
    }
}

@Composable
private fun EndpointMetric(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun ConfigButton(label: Int, format: String, enabled: Boolean, onConfig: (String) -> Unit) {
    OutlinedButton(onClick = { onConfig(format) }, enabled = enabled) {
        Text(stringResource(label))
    }
}

@Composable
private fun ReportHeader() {
    Row(
        modifier = Modifier
            .width(774.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 7.dp)
    ) {
        HeaderCell(R.string.report_status, 68.dp)
        HeaderCell(R.string.report_endpoint, 154.dp)
        HeaderCell(R.string.endpoint_ping, 78.dp)
        HeaderCell(R.string.tunnel_ping, 78.dp)
        HeaderCell(R.string.loss, 54.dp)
        HeaderCell(R.string.seen_as, 78.dp)
        HeaderCell(R.string.node, 52.dp)
        HeaderCell(R.string.node_location, 128.dp)
        HeaderCell(R.string.speed, 84.dp)
    }
}

@Composable
private fun ReportTableRow(result: ReportEndpoint) {
    val background = when {
        !result.working -> MaterialTheme.colorScheme.surface
        result.durable -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
    }
    Row(
        modifier = Modifier
            .width(774.dp)
            .background(background)
            .padding(vertical = 7.dp)
    ) {
        DataCell(
            text = when {
                !result.working -> stringResource(R.string.status_failed)
                result.durable -> stringResource(R.string.status_working)
                else -> stringResource(R.string.status_torn_down)
            },
            width = 68.dp,
            color = when {
                !result.working -> MaterialTheme.colorScheme.error
                result.durable -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.tertiary
            }
        )
        DataCell(result.endpoint, 154.dp, monospace = true)
        DataCell(formatLatency(result.endpointPingMs), 78.dp, monospace = true)
        DataCell(formatLatency(result.tunnelPingMs), 78.dp, monospace = true)
        DataCell(formatPercent(result.lossPercent, result.tunnelPingMs > 0), 54.dp, monospace = true)
        DataCell(withFlag(result.region, result.region), 78.dp)
        DataCell(result.node.ifBlank { "-" }, 52.dp, monospace = true)
        DataCell(withFlag(result.country, result.nodeLocation), 128.dp)
        DataCell(formatSpeed(result.speedMbps), 84.dp, monospace = true)
    }
}

@Composable
private fun HeaderCell(label: Int, width: Dp) {
    Text(
        text = stringResource(label),
        modifier = Modifier
            .width(width)
            .padding(horizontal = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun DataCell(
    text: String,
    width: Dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    monospace: Boolean = false
) {
    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

internal fun parseReport(json: String): List<ReportEndpoint> {
    val results = runCatching { JSONObject(json).optJSONArray("results") }.getOrNull() ?: return emptyList()
    return (0 until results.length()).mapNotNull { index ->
        results.optJSONObject(index)?.let {
            ReportEndpoint(
                endpoint = it.optString("endpoint"),
                region = it.optString("region"),
                node = it.optString("node"),
                country = it.optString("country"),
                nodeLocation = it.optString("nodeLocation"),
                endpointPingMs = it.optDouble("endpointPingMs"),
                tunnelPingMs = it.optDouble("tunnelPingMs"),
                lossPercent = it.optDouble("lossPercent"),
                speedMbps = it.optDouble("speedMbps"),
                working = it.optBoolean("working"),
                durable = it.optBoolean("durable")
            )
        }
    }
}

fun countryFlag(code: String): String {
    val normalized = code.trim().uppercase()
    if (normalized.length != 2 || normalized.any { it !in 'A'..'Z' }) return ""
    return normalized.map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
}

internal fun withFlag(code: String, value: String): String {
    val label = value.ifBlank { code }.ifBlank { "-" }
    return countryFlag(code).takeIf(String::isNotBlank)?.let { "$it $label" } ?: label
}

internal fun formatLatency(value: Double): String = if (value > 0) "%.1f ms".format(value) else "-"

internal fun formatPercent(value: Double, measured: Boolean): String = if (measured) "%.0f%%".format(value) else "-"

internal fun formatSpeed(value: Double): String = if (value > 0) "%.1f Mbps".format(value) else "-"
