package io.github.openwarpkit.warpscout.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.core.OperationState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanStartButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(stringResource(R.string.start_scan)) } },
        state = rememberTooltipState(),
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = { if (enabled) onClick() },
            modifier = Modifier.size(56.dp),
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }
        ) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = stringResource(R.string.start_scan),
                tint = if (enabled) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanOperationDock(
    state: OperationState,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val finished = !state.running
    val failed = finished && state.latestResultJson == null
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        shadowElevation = 3.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(3f)
                    .heightIn(min = 56.dp)
                    .clickable(
                        enabled = finished && state.historyId != null,
                        onClick = onOpenHistory
                    ),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = when {
                            state.running -> state.phase.ifBlank { stringResource(R.string.phase_preparing) }
                            failed -> state.errorMessage ?: stringResource(R.string.status_failed)
                            else -> stringResource(R.string.status_completed)
                        },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.scan_dock_progress,
                            state.completed,
                            state.total,
                            state.working
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LinearProgressIndicator(
                        progress = { if (finished) 1f else state.progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd
            ) {
                val tooltip = when {
                    state.running -> stringResource(R.string.stop_operation)
                    else -> stringResource(R.string.dismiss)
                }
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(tooltip) } },
                    state = rememberTooltipState()
                ) {
                    FloatingActionButton(
                        onClick = if (state.running) onStop else onDismiss,
                        modifier = Modifier.size(56.dp)
                    ) {
                        when {
                            state.running -> Box(contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(34.dp),
                                    strokeWidth = 3.dp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Icon(
                                    Icons.Filled.Stop,
                                    contentDescription = stringResource(R.string.stop_operation),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            failed -> Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.dismiss)
                            )
                            else -> Icon(
                                Icons.Filled.Check,
                                contentDescription = stringResource(R.string.dismiss)
                            )
                        }
                    }
                }
            }
        }
    }
}
