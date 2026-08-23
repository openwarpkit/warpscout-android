package io.github.openwarpkit.warpscout.ui.screen

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.data.HistoryEntity
import io.github.openwarpkit.warpscout.ui.AppViewModel
import io.github.openwarpkit.warpscout.ui.HistoryFocusRequest
import io.github.openwarpkit.warpscout.ui.HistoryFocusResolution
import io.github.openwarpkit.warpscout.ui.resolveHistoryFocus
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.delay

private const val HistoryHighlightDurationMs = 1_800L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: AppViewModel,
    focusRequest: HistoryFocusRequest? = null,
    onFocusConsumed: (HistoryFocusRequest) -> Unit = {},
    onOpenReport: (Long) -> Unit
) {
    val historySnapshot by viewModel.historySnapshot.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val history = historySnapshot.items
    val listState = rememberLazyListState()
    var highlightedRequest by remember { mutableStateOf<HistoryFocusRequest?>(null) }

    LaunchedEffect(focusRequest, historySnapshot) {
        val request = focusRequest ?: return@LaunchedEffect
        when (
            val resolution = resolveHistoryFocus(
                request,
                historySnapshot.loaded,
                history.map(HistoryEntity::id)
            )
        ) {
            HistoryFocusResolution.Waiting -> Unit
            is HistoryFocusResolution.Found -> {
                highlightedRequest = resolution.request
                onFocusConsumed(resolution.request)
            }
            is HistoryFocusResolution.Missing -> {
                highlightedRequest = null
                onFocusConsumed(resolution.request)
            }
        }
    }

    LaunchedEffect(highlightedRequest, history) {
        val request = highlightedRequest ?: return@LaunchedEffect
        val index = history.indexOfFirst { it.id == request.historyId }
        if (index < 0) {
            highlightedRequest = null
            return@LaunchedEffect
        }
        listState.animateScrollToItem(index)
        delay(HistoryHighlightDurationMs)
        if (highlightedRequest == request) highlightedRequest = null
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
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp,
                    top = 8.dp,
                    end = 20.dp,
                    bottom = if (operation.operation == "scan") 104.dp else 8.dp
                )
            ) {
                items(history, key = HistoryEntity::id) { item ->
                    val highlighted = item.id == highlightedRequest?.historyId
                    val containerColor by animateColorAsState(
                        targetValue = if (highlighted) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.background
                        },
                        animationSpec = tween(durationMillis = 220),
                        label = "historyRowHighlight"
                    )
                    Surface(
                        color = containerColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .semantics { selected = highlighted }
                            .clickable {
                                highlightedRequest = null
                                onOpenReport(item.id)
                            }
                    ) {
                        HistoryRow(item)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(item: HistoryEntity) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
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
