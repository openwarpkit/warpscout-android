package io.github.openwarpkit.warpscout.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.data.HistoryEntity
import io.github.openwarpkit.warpscout.ui.AppViewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: AppViewModel,
    highlightedId: Long? = null,
    onViewReport: (Long) -> Unit,
    onViewConfig: (Long, String) -> Unit
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    var expandedId by rememberSaveable { mutableStateOf<Long?>(null) }
    var endpointDetailsId by rememberSaveable { mutableStateOf<Long?>(null) }
    val listState = rememberLazyListState()

    LaunchedEffect(highlightedId, history) {
        val index = history.indexOfFirst { it.id == highlightedId }
        if (index >= 0) listState.animateScrollToItem(index)
    }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.history_title)) }) }) { padding ->
        if (history.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) {
                items(history, key = HistoryEntity::id) { item ->
                    Surface(
                        color = if (item.id == highlightedId) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        } else {
                            MaterialTheme.colorScheme.background
                        }
                    ) {
                        HistoryRow(
                            item = item,
                            expanded = expandedId == item.id,
                            endpointExpanded = endpointDetailsId == item.id,
                            actionsEnabled = !operation.running,
                            onToggle = {
                                expandedId = if (expandedId == item.id) null else item.id
                                if (expandedId != item.id) endpointDetailsId = null
                            },
                            onViewReport = { onViewReport(item.id) },
                            onEndpoint = {
                                endpointDetailsId = if (endpointDetailsId == item.id) null else item.id
                            },
                            onConfig = { onViewConfig(item.id, it) }
                        )
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    item: HistoryEntity,
    expanded: Boolean,
    endpointExpanded: Boolean,
    actionsEnabled: Boolean,
    onToggle: () -> Unit,
    onViewReport: () -> Unit,
    onEndpoint: () -> Unit,
    onConfig: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .animateContentSize()
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                listOf(item.preset, item.protocol.uppercase()).filter(String::isNotBlank).joinToString(" / "),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f)
            )
            Text(
                historyStatus(item.status),
                style = MaterialTheme.typography.labelLarge,
                color = when (item.status) {
                    "Completed" -> MaterialTheme.colorScheme.primary
                    "Interrupted" -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.error
                }
            )
        }
        Text(
            DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.startedAt)),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HistoryMetric(stringResource(R.string.working_endpoints), item.workingCount)
            HistoryMetric(stringResource(R.string.torn_down_endpoints), item.tornDownCount)
            if (item.progressTotal > 0) {
                Text(
                    "${item.progressCompleted}/${item.progressTotal}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
        if (expanded) {
            ExportActions(item, actionsEnabled, onViewReport, onEndpoint, onConfig)
            if (endpointExpanded) BestEndpointDetails(item)
        }
    }
}

@Composable
private fun ExportActions(
    item: HistoryEntity,
    enabled: Boolean,
    onViewReport: () -> Unit,
    onEndpoint: () -> Unit,
    onConfig: (String) -> Unit
) {
    val hasResult = item.resultJson != null && item.status == "Completed"
    HorizontalDivider(Modifier.padding(top = 4.dp))
    Text(stringResource(R.string.export), style = MaterialTheme.typography.titleSmall)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TextButton(onClick = onViewReport, enabled = hasResult) {
            Text(stringResource(R.string.view_report))
        }
        TextButton(onClick = onEndpoint, enabled = item.bestEndpoint != null && hasResult) {
            Text(stringResource(R.string.best_endpoint))
        }
        when (item.protocol.lowercase()) {
            "wg" -> ConfigButton(R.string.wireguard_config, "wireguard", enabled && hasResult, onConfig)
            "awg" -> ConfigButton(R.string.amneziawg_config, "amneziawg", enabled && hasResult, onConfig)
            "masque", "masque-h2" -> ConfigButton(R.string.usque_config, "usque", enabled && hasResult, onConfig)
        }
        ConfigButton(R.string.mihomo_config, "mihomo", enabled && hasResult, onConfig)
    }
}

@Composable
private fun BestEndpointDetails(item: HistoryEntity) {
    val context = LocalContext.current
    val endpoint = remember(item.resultJson, item.bestEndpoint) {
        parseReport(item.resultJson.orEmpty()).firstOrNull { it.endpoint == item.bestEndpoint }
    } ?: return
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
                    modifier = Modifier.weight(1f, fill = false),
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
private fun historyStatus(status: String): String = when (status) {
    "Completed" -> stringResource(R.string.status_completed)
    "Interrupted" -> stringResource(R.string.status_interrupted)
    else -> stringResource(R.string.status_failed)
}

@Composable
private fun HistoryMetric(label: String, value: Int) {
    Text(
        "$label $value",
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace
    )
}
