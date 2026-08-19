package io.github.openwarpkit.warpscout.ui.screen

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(
    viewModel: AppViewModel,
    historyId: Long,
    format: String,
    onBack: () -> Unit
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val result by viewModel.configDocument.collectAsStateWithLifecycle()
    val exportError by viewModel.exportError.collectAsStateWithLifecycle()
    val item = history.firstOrNull { it.id == historyId }
    val document = result?.getOrNull()?.takeIf { it.historyId == historyId && it.format == format }
    val snackbar = remember { SnackbarHostState() }
    val createDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain")
    ) { uri ->
        if (uri != null && document != null) viewModel.saveConfig(document, uri)
    }

    LaunchedEffect(item?.id, format) {
        if (item != null) viewModel.loadConfig(item, format)
    }

    LaunchedEffect(exportError) {
        val message = exportError ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.clearExportError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(configTitle(format)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (document != null) {
                Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.shareConfig(document) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null)
                            Text(stringResource(R.string.share), Modifier.padding(start = 8.dp))
                        }
                        Button(
                            onClick = { createDocument.launch(document.fileName) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Outlined.Download, contentDescription = null)
                            Text(stringResource(R.string.download), Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }
        }
    ) { padding ->
        when {
            item == null -> MessageBox(stringResource(R.string.config_unavailable), Modifier.padding(padding))
            result == null -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Text(
                        stringResource(R.string.loading_config),
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            result?.isFailure == true -> MessageBox(
                result?.exceptionOrNull()?.message ?: stringResource(R.string.config_unavailable),
                Modifier.padding(padding)
            )
            document != null -> SelectionContainer {
                Text(
                    text = document.content,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace
                )
            }
            else -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        }
    }
}

@Composable
private fun MessageBox(message: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun configTitle(format: String): String = stringResource(
    when (format) {
        "wireguard" -> R.string.wireguard_config
        "amneziawg" -> R.string.amneziawg_config
        "usque" -> R.string.usque_config
        "mihomo" -> R.string.mihomo_config
        else -> R.string.config_preview
    }
)
