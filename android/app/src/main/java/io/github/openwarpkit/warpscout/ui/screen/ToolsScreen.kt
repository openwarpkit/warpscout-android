package io.github.openwarpkit.warpscout.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.core.OperationRequest
import io.github.openwarpkit.warpscout.ui.AppViewModel
import io.github.openwarpkit.warpscout.ui.components.OperationPanel
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(viewModel: AppViewModel) {
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    var socksPort by rememberSaveable { mutableStateOf("1080") }
    var socksEndpoint by rememberSaveable { mutableStateOf("") }
    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.tools_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            ToolSection(
                title = stringResource(R.string.find_junk),
                description = stringResource(R.string.find_junk_description),
                enabled = !operation.running,
                onStart = {
                    val payload = JSONObject()
                        .put("protocol", "awg")
                        .put("timeoutSec", 2)
                        .put("jobs", 10)
                        .put("samplePerSubnet", 3)
                        .put("tunnelPingCount", 10)
                        .toString()
                    viewModel.start(OperationRequest("find-junk", payload, protocol = "awg"))
                }
            )
            HorizontalDivider()
            ToolSection(
                title = stringResource(R.string.find_sni),
                description = stringResource(R.string.find_sni_description),
                enabled = !operation.running,
                onStart = {
                    val payload = JSONObject()
                        .put("protocol", "masque")
                        .put("timeoutSec", 2)
                        .put("jobs", 14)
                        .put("samplePerSubnet", 1)
                        .put("tunnelPingCount", 10)
                        .put("masqueAttempts", 3)
                        .toString()
                    viewModel.start(OperationRequest("find-sni", payload, protocol = "masque"))
                }
            )
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.socks), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.socks_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = socksEndpoint,
                    onValueChange = { socksEndpoint = it },
                    label = { Text(stringResource(R.string.best_endpoint)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = socksPort,
                    onValueChange = { socksPort = it },
                    label = { Text(stringResource(R.string.port)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Button(
                    enabled = !operation.running && socksEndpoint.isNotBlank() && socksPort.toIntOrNull() in 1..65535,
                    onClick = {
                        val payload = JSONObject()
                            .put("endpoint", socksEndpoint.trim())
                            .put("port", socksPort.toInt())
                            .put("protocol", "wg")
                            .put("scan", JSONObject().put("protocol", "wg").put("timeoutSec", 2))
                            .toString()
                        viewModel.start(OperationRequest("socks", payload, protocol = "wg"))
                    }
                ) { Text(stringResource(R.string.start)) }
            }
            if (operation.operation in setOf("find-junk", "find-sni", "socks")) {
                OperationPanel(operation, viewModel::stop, viewModel::dismissOperation)
            }
        }
    }
}

@Composable
private fun ToolSection(
    title: String,
    description: String,
    enabled: Boolean,
    onStart: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth()) {
            Button(onClick = onStart, enabled = enabled) { Text(stringResource(R.string.start)) }
        }
    }
}
