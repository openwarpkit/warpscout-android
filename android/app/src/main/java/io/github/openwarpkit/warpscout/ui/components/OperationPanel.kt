package io.github.openwarpkit.warpscout.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.core.OperationState

@Composable
fun OperationPanel(
    state: OperationState,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.running && state.errorMessage.isNullOrBlank()) return
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        HorizontalDivider()
        Text(
            text = if (state.running) {
                stringResource(R.string.active_operation)
            } else {
                stringResource(R.string.operation_failed)
            },
            style = MaterialTheme.typography.titleMedium
        )
        if (state.running && state.phase.isNotBlank()) {
            Text(state.phase, style = MaterialTheme.typography.bodyMedium)
        }
        if (state.running) {
            if (state.total > 0) {
                LinearProgressIndicator(progress = { state.progress }, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Metric(stringResource(R.string.working_endpoints), state.working.toString())
                Metric(stringResource(R.string.torn_down_endpoints), state.tornDown.toString())
                Metric(stringResource(R.string.progress), if (state.total > 0) "${state.completed}/${state.total}" else "-")
            }
            Button(onClick = onStop) { Text(stringResource(R.string.stop_operation)) }
        }
        state.errorMessage?.takeIf(String::isNotBlank)?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dismiss))
            }
        }
        if (state.regions.isNotEmpty()) {
            ValueList(stringResource(R.string.regions), state.regions)
        }
        if (state.nodes.isNotEmpty()) {
            ValueList(stringResource(R.string.nodes), state.nodes)
        }
        state.bestEndpoint?.let {
            ValueList(stringResource(R.string.best_endpoint), setOf(it))
        }
    }
}

@Composable
private fun ValueList(label: String, values: Set<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(values.sorted().joinToString(", "), style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
private fun Metric(label: String, value: String) {
    Column(Modifier.padding(end = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, fontFamily = FontFamily.Monospace)
    }
}
