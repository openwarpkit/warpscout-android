package io.github.openwarpkit.warpscout.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ReportScreen(viewModel: AppViewModel, historyId: Long, onBack: () -> Unit) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val exportError by viewModel.exportError.collectAsStateWithLifecycle()
    val item = history.firstOrNull { it.id == historyId }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(exportError) {
        val message = exportError ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.clearExportError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.report_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.dismiss)
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
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onOpenTxt = { viewModel.openReport(item) },
                onShareTxt = { viewModel.shareReport(item) }
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportContent(
    item: HistoryEntity,
    modifier: Modifier,
    onOpenTxt: () -> Unit,
    onShareTxt: () -> Unit
) {
    val results = remember(item.resultJson) { parseReport(item.resultJson.orEmpty()) }
    val working = results.count { it.working && it.durable }
    Column(modifier) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = item.protocol.uppercase(),
                style = MaterialTheme.typography.titleLarge,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = stringResource(R.string.report_summary, working, results.size),
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(item.startedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = onOpenTxt) { Text(stringResource(R.string.open_txt)) }
                OutlinedButton(onClick = onShareTxt) { Text(stringResource(R.string.share_txt)) }
            }
        }
        HorizontalDivider()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            LazyColumn(
                modifier = Modifier
                    .width(1180.dp)
                    .fillMaxHeight()
            ) {
                item { ReportHeader() }
                items(results, key = { it.endpoint }) { endpoint ->
                    ReportTableRow(endpoint)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ReportHeader() {
    Row(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(vertical = 10.dp)
    ) {
        HeaderCell(R.string.report_status, 110.dp)
        HeaderCell(R.string.report_endpoint, 230.dp)
        HeaderCell(R.string.endpoint_ping, 130.dp)
        HeaderCell(R.string.tunnel_ping, 130.dp)
        HeaderCell(R.string.loss, 90.dp)
        HeaderCell(R.string.seen_as, 120.dp)
        HeaderCell(R.string.node, 90.dp)
        HeaderCell(R.string.node_location, 180.dp)
        HeaderCell(R.string.speed, 100.dp)
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
            .background(background)
            .padding(vertical = 10.dp)
    ) {
        DataCell(
            text = when {
                !result.working -> stringResource(R.string.status_failed)
                result.durable -> stringResource(R.string.status_working)
                else -> stringResource(R.string.status_torn_down)
            },
            width = 110.dp,
            color = when {
                !result.working -> MaterialTheme.colorScheme.error
                result.durable -> MaterialTheme.colorScheme.primary
                else -> MaterialTheme.colorScheme.tertiary
            }
        )
        DataCell(result.endpoint, 230.dp, monospace = true)
        DataCell(formatLatency(result.endpointPingMs), 130.dp, monospace = true)
        DataCell(formatLatency(result.tunnelPingMs), 130.dp, monospace = true)
        DataCell(formatPercent(result.lossPercent, result.tunnelPingMs > 0), 90.dp, monospace = true)
        DataCell(withFlag(result.region, result.region), 120.dp)
        DataCell(result.node.ifBlank { "-" }, 90.dp, monospace = true)
        DataCell(withFlag(result.country, result.nodeLocation), 180.dp)
        DataCell(formatSpeed(result.speedMbps), 100.dp, monospace = true)
    }
}

@Composable
private fun HeaderCell(label: Int, width: Dp) {
    Text(
        text = stringResource(label),
        modifier = Modifier
            .width(width)
            .padding(horizontal = 10.dp),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold
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
            .padding(horizontal = 10.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default
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
