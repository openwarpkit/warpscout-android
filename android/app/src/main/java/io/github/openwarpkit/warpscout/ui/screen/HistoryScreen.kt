package io.github.openwarpkit.warpscout.ui.screen

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
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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
    onOpenReport: (Long) -> Unit
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
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
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenReport(item.id) }
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
