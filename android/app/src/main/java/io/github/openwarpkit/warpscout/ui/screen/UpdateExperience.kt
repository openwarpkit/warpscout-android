package io.github.openwarpkit.warpscout.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.data.AvailableUpdate
import io.github.openwarpkit.warpscout.data.UpdateDownloadController
import io.github.openwarpkit.warpscout.data.UpdateDownloadState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateAvailableSheet(
    update: AvailableUpdate,
    updateEnabled: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.update_available_title),
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                text = stringResource(R.string.update_available_message, update.version),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!updateEnabled) {
                Text(
                    text = stringResource(R.string.update_operation_in_progress),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onUpdate,
                enabled = updateEnabled,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.update_now))
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.update_later))
            }
        }
    }
}

@Composable
fun UpdateWizardScreen(
    state: UpdateDownloadState,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onInstall: () -> Unit,
    onOpenRelease: (String) -> Unit,
    onBack: () -> Unit
) {
    if (state is UpdateDownloadState.Idle) return
    BackHandler(enabled = true, onBack = onBack)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 32.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = state,
                contentKey = ::updateStateKey,
                transitionSpec = {
                    fadeIn(tween(durationMillis = 220)) togetherWith
                        fadeOut(tween(durationMillis = 90))
                },
                label = "update-state"
            ) { animatedState ->
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    when (animatedState) {
                        is UpdateDownloadState.Downloading -> DownloadingUpdate(animatedState, onCancel)
                        is UpdateDownloadState.Ready -> ReadyUpdate(animatedState, onInstall)
                        is UpdateDownloadState.Failed -> FailedUpdate(animatedState, onRetry, onOpenRelease)
                        UpdateDownloadState.Idle -> Unit
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadingUpdate(state: UpdateDownloadState.Downloading, onCancel: () -> Unit) {
    UpdateMotion(
        icon = Icons.Outlined.Download,
        contentDescription = stringResource(R.string.downloading_update)
    )
    Spacer(Modifier.height(36.dp))
    UpdateHeading(
        title = stringResource(R.string.downloading_update),
        description = stringResource(R.string.downloading_update_message, state.update.version)
    )
    Spacer(Modifier.height(28.dp))
    val progress = state.progress
    if (progress == null) {
        LinearProgressIndicator(Modifier.fillMaxWidth())
    } else {
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(10.dp))
    Text(
        text = downloadProgressText(state.downloadedBytes, state.totalBytes),
        style = MaterialTheme.typography.labelLarge,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(28.dp))
    OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.cancel_download))
    }
}

@Composable
private fun ReadyUpdate(state: UpdateDownloadState.Ready, onInstall: () -> Unit) {
    StatusMark(Icons.Outlined.CheckCircle, MaterialTheme.colorScheme.primary)
    Spacer(Modifier.height(36.dp))
    UpdateHeading(
        title = stringResource(R.string.update_ready_title),
        description = stringResource(R.string.update_ready_message, state.update.version)
    )
    Spacer(Modifier.height(16.dp))
    Text(
        text = stringResource(R.string.install_permission_hint),
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(32.dp))
    Button(onClick = onInstall, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.install_update))
    }
}

@Composable
private fun FailedUpdate(
    state: UpdateDownloadState.Failed,
    onRetry: () -> Unit,
    onOpenRelease: (String) -> Unit
) {
    StatusMark(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error)
    Spacer(Modifier.height(36.dp))
    UpdateHeading(
        title = stringResource(R.string.update_download_failed_title),
        description = stringResource(failureMessage(state.reason))
    )
    Spacer(Modifier.height(32.dp))
    Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.retry_download))
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(
        onClick = { onOpenRelease(state.update.releaseUrl) },
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(stringResource(R.string.open_release_page))
    }
}

@Composable
private fun UpdateHeading(title: String, description: String) {
    Text(title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
    Spacer(Modifier.height(10.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun UpdateMotion(icon: ImageVector, contentDescription: String) {
    val transition = rememberInfiniteTransition(label = "update")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1_700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.tertiary
    Box(Modifier.size(132.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val inset = 8.dp.toPx()
            drawArc(
                color = primary,
                startAngle = rotation,
                sweepAngle = 108f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2, size.height - inset * 2),
                style = Stroke(width = 7.dp.toPx(), cap = StrokeCap.Round)
            )
            val inner = 21.dp.toPx()
            drawArc(
                color = secondary,
                startAngle = -rotation * 2f + 180f,
                sweepAngle = 72f,
                useCenter = false,
                topLeft = Offset(inner, inner),
                size = Size(size.width - inner * 2, size.height - inner * 2),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round)
            )
        }
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(44.dp),
            tint = primary
        )
    }
}

@Composable
private fun StatusMark(icon: ImageVector, tint: androidx.compose.ui.graphics.Color) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(104.dp), tint = tint)
}

@Composable
private fun downloadProgressText(downloaded: Long, total: Long): String {
    val downloadedMb = downloaded.coerceAtLeast(0) / 1_048_576f
    return if (total > 0) {
        stringResource(R.string.update_download_progress, downloadedMb, total / 1_048_576f)
    } else {
        stringResource(R.string.update_download_progress_unknown, downloadedMb)
    }
}

private fun failureMessage(reason: String): Int = when (reason) {
    UpdateDownloadController.FAILURE_AUTOMATIC_UNAVAILABLE -> R.string.update_failure_automatic_download_unavailable
    UpdateDownloadController.FAILURE_FILE -> R.string.update_failure_update_file_invalid
    UpdateDownloadController.FAILURE_DIGEST -> R.string.update_failure_update_digest_invalid
    UpdateDownloadController.FAILURE_PACKAGE -> R.string.update_failure_update_package_invalid
    UpdateDownloadController.FAILURE_VERSION -> R.string.update_failure_update_version_invalid
    UpdateDownloadController.FAILURE_SIGNATURE -> R.string.update_failure_update_signature_invalid
    else -> R.string.update_failure_update_download_failed
}

private fun updateStateKey(state: UpdateDownloadState): Int = when (state) {
    UpdateDownloadState.Idle -> 0
    is UpdateDownloadState.Downloading -> 1
    is UpdateDownloadState.Ready -> 2
    is UpdateDownloadState.Failed -> 3
}
