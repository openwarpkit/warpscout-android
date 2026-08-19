package io.github.openwarpkit.warpscout.ui.screen

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.ui.AppViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: AppViewModel, onAbout: () -> Unit) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val updateResult by viewModel.updateResult.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val exportError by viewModel.exportError.collectAsStateWithLifecycle()
    var relayUrl by remember(settings.relayUrl) { mutableStateOf(settings.relayUrl) }
    var showClearDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(exportError) {
        val message = exportError ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.clearExportError()
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.clear_data_confirm_title)) },
            text = { Text(stringResource(R.string.clear_data_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearApplicationData()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.clear_data))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            SettingSwitch(
                title = stringResource(R.string.relay),
                description = stringResource(R.string.relay_description),
                checked = settings.relayEnabled,
                onCheckedChange = viewModel::setRelayEnabled
            )
            OutlinedTextField(
                value = relayUrl,
                onValueChange = {
                    relayUrl = it
                    viewModel.setRelayUrl(it)
                },
                enabled = settings.relayEnabled,
                label = { Text(stringResource(R.string.relay_url)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            SettingSwitch(
                title = stringResource(R.string.dynamic_color),
                description = stringResource(R.string.dynamic_color_description),
                checked = settings.dynamicColor,
                onCheckedChange = viewModel::setDynamicColor
            )
            HorizontalDivider()
            LanguageSelector()
            HorizontalDivider()
            Button(onClick = viewModel::checkUpdates) { Text(stringResource(R.string.check_updates)) }
            updateResult?.fold(
                onSuccess = { result ->
                    Text(
                        if (!result.releaseFound) {
                            stringResource(R.string.no_android_releases)
                        } else if (result.updateAvailable) {
                            stringResource(R.string.update_available, result.latestVersion)
                        } else {
                            stringResource(R.string.update_current)
                        },
                        modifier = if (result.updateAvailable) Modifier.clickable {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(result.releaseUrl)))
                        } else Modifier,
                        color = if (result.updateAvailable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                },
                onFailure = { Text(it.message ?: stringResource(R.string.operation_failed), color = MaterialTheme.colorScheme.error) }
            )
            HorizontalDivider()
            OutlinedButton(
                onClick = onAbout,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.Info, contentDescription = null)
                Text(
                    text = stringResource(R.string.about),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            OutlinedButton(
                onClick = { showClearDialog = true },
                enabled = !operation.running,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Icon(Icons.Outlined.DeleteForever, contentDescription = null)
                Text(
                    text = stringResource(R.string.clear_app_data),
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            Text(
                text = stringResource(R.string.clear_app_data_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageSelector() {
    val selected = AppCompatDelegate.getApplicationLocales().toLanguageTags()
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.language), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LanguageChip("", R.string.language_system, selected)
            LanguageChip("ru", R.string.language_russian, selected)
            LanguageChip("en", R.string.language_english, selected)
        }
    }
}

@Composable
private fun LanguageChip(tag: String, label: Int, selected: String) {
    FilterChip(
        selected = if (tag.isEmpty()) selected.isEmpty() else selected.startsWith(tag),
        onClick = { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) },
        label = { Text(stringResource(label)) }
    )
}

@Composable
private fun SettingSwitch(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}
