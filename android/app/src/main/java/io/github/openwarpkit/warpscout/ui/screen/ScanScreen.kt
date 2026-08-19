package io.github.openwarpkit.warpscout.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.core.ExpertScanOptions
import io.github.openwarpkit.warpscout.core.OperationRequest
import io.github.openwarpkit.warpscout.core.ScanPreset
import io.github.openwarpkit.warpscout.core.resolveScanOptions
import io.github.openwarpkit.warpscout.ui.AppViewModel
import io.github.openwarpkit.warpscout.ui.components.OperationPanel
import org.json.JSONArray
import org.json.JSONObject

private data class ProtocolChoice(val id: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ScanScreen(viewModel: AppViewModel) {
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    var preset by rememberSaveable { mutableStateOf(ScanPreset.Standard) }
    var expert by rememberSaveable { mutableStateOf(false) }
    var protocol by rememberSaveable { mutableStateOf("wg") }
    var ipv6 by rememberSaveable { mutableStateOf(false) }
    var port by rememberSaveable { mutableStateOf("0") }
    var timeout by rememberSaveable { mutableStateOf("2") }
    var jobs by rememberSaveable { mutableStateOf("10") }
    var target by rememberSaveable { mutableStateOf("") }
    var tunnelPings by rememberSaveable { mutableStateOf("0") }
    var junkCount by rememberSaveable { mutableStateOf("0") }
    var junkMin by rememberSaveable { mutableStateOf("0") }
    var junkMax by rememberSaveable { mutableStateOf("0") }
    var i1 by rememberSaveable { mutableStateOf("") }
    var masqueSni by rememberSaveable { mutableStateOf("") }
    var masqueAttempts by rememberSaveable { mutableStateOf("3") }
    var nodes by rememberSaveable { mutableStateOf("") }
    var countries by rememberSaveable { mutableStateOf("") }
    var mtu by rememberSaveable { mutableStateOf("0") }
    var dns by rememberSaveable { mutableStateOf("") }
    var speedTest by rememberSaveable { mutableStateOf(false) }
    var nested by rememberSaveable { mutableStateOf(false) }
    var through by rememberSaveable { mutableStateOf("") }
    var innerProtocol by rememberSaveable { mutableStateOf("wg") }

    Scaffold(topBar = { TopAppBar(title = { Text(stringResource(R.string.scan_title)) }) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                stringResource(R.string.scan_description),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PresetChip(ScanPreset.Standard, preset, R.string.preset_standard) { preset = it }
                PresetChip(ScanPreset.Durable, preset, R.string.preset_durable) { preset = it }
                PresetChip(ScanPreset.Full, preset, R.string.preset_full) { preset = it }
            }
            Text(
                stringResource(
                    when (preset) {
                        ScanPreset.Standard -> R.string.preset_standard_description
                        ScanPreset.Durable -> R.string.preset_durable_description
                        ScanPreset.Full -> R.string.preset_full_description
                    }
                ),
                style = MaterialTheme.typography.bodyMedium
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .toggleable(
                        value = expert,
                        role = Role.Switch,
                        onValueChange = { expert = it }
                    )
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.expert_mode), style = MaterialTheme.typography.titleMedium)
                Switch(checked = expert, onCheckedChange = null)
            }
            if (expert) {
                HorizontalDivider()
                Text(stringResource(R.string.protocol), style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        ProtocolChoice("wg", "WG"),
                        ProtocolChoice("awg", "AWG"),
                        ProtocolChoice("masque", "MASQUE H3"),
                        ProtocolChoice("masque-h2", "MASQUE H2")
                    ).forEach { choice ->
                        FilterChip(
                            selected = protocol == choice.id,
                            onClick = { protocol = choice.id },
                            label = { Text(choice.label) }
                        )
                    }
                }
                Text(stringResource(R.string.ip_family), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !ipv6, onClick = { ipv6 = false }, label = { Text(stringResource(R.string.ipv4)) })
                    FilterChip(selected = ipv6, onClick = { ipv6 = true }, label = { Text(stringResource(R.string.ipv6)) })
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(port, { port = it }, R.string.port, Modifier.weight(1f))
                    NumberField(timeout, { timeout = it }, R.string.timeout, Modifier.weight(1f))
                    NumberField(jobs, { jobs = it }, R.string.jobs, Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = target,
                    onValueChange = { target = it },
                    label = { Text(stringResource(R.string.custom_target)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                NumberField(tunnelPings, { tunnelPings = it }, R.string.tunnel_pings, Modifier.fillMaxWidth())
                if (protocol == "awg") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        NumberField(junkCount, { junkCount = it }, "Jc", Modifier.weight(1f))
                        NumberField(junkMin, { junkMin = it }, "Jmin", Modifier.weight(1f))
                        NumberField(junkMax, { junkMax = it }, "Jmax", Modifier.weight(1f))
                    }
                    OutlinedTextField(value = i1, onValueChange = { i1 = it }, label = { Text("I1") }, modifier = Modifier.fillMaxWidth())
                }
                if (protocol.startsWith("masque")) {
                    OutlinedTextField(
                        value = masqueSni,
                        onValueChange = { masqueSni = it },
                        label = { Text(stringResource(R.string.masque_sni)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    NumberField(masqueAttempts, { masqueAttempts = it }, R.string.masque_attempts, Modifier.fillMaxWidth())
                }
                OutlinedTextField(value = nodes, onValueChange = { nodes = it }, label = { Text(stringResource(R.string.node_filters)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = countries, onValueChange = { countries = it }, label = { Text(stringResource(R.string.country_filters)) }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    NumberField(mtu, { mtu = it }, R.string.mtu, Modifier.weight(1f))
                    OutlinedTextField(value = dns, onValueChange = { dns = it }, label = { Text(stringResource(R.string.dns)) }, modifier = Modifier.weight(2f))
                }
                ToggleRow(R.string.speed_test, speedTest) { speedTest = it }
                ToggleRow(R.string.warp_in_warp, nested) { nested = it }
                if (nested) {
                    OutlinedTextField(value = through, onValueChange = { through = it }, label = { Text(stringResource(R.string.through_endpoint)) }, modifier = Modifier.fillMaxWidth())
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = innerProtocol == "wg", onClick = { innerProtocol = "wg" }, label = { Text(stringResource(R.string.inner_wg)) })
                        FilterChip(selected = innerProtocol == "awg", onClick = { innerProtocol = "awg" }, label = { Text(stringResource(R.string.inner_awg)) })
                    }
                }
            }
            Button(
                onClick = {
                    val resolved = resolveScanOptions(
                        preset = preset,
                        expertEnabled = expert,
                        expert = ExpertScanOptions(
                            protocol = protocol,
                            innerProtocol = innerProtocol,
                            ipv6 = ipv6,
                            port = port.intOr(0),
                            timeoutSec = timeout.intOr(2),
                            jobs = jobs.intOr(10),
                            customTarget = target.trim(),
                            tunnelPingCount = tunnelPings.intOr(0),
                            awgJunkCount = junkCount.intOr(0),
                            awgJunkMin = junkMin.intOr(0),
                            awgJunkMax = junkMax.intOr(0),
                            awgI1 = i1.trim(),
                            masqueSni = masqueSni.trim(),
                            masqueAttempts = masqueAttempts.intOr(3),
                            includeNodes = nodes.stringList(),
                            includeCountries = countries.stringList(),
                            mtu = mtu.intOr(0),
                            dns = dns.stringList(),
                            speedTest = speedTest,
                            throughEndpoint = if (nested) through.trim() else ""
                        )
                    )
                    val payload = JSONObject()
                        .put("protocol", resolved.protocol)
                        .put("innerProtocol", resolved.innerProtocol)
                        .put("ipv6", resolved.ipv6)
                        .put("port", resolved.port)
                        .put("timeoutSec", resolved.timeoutSec)
                        .put("jobs", resolved.jobs)
                        .put("samplePerSubnet", resolved.samplePerSubnet)
                        .put("full", resolved.full)
                        .put("tunnelPingCount", resolved.tunnelPingCount)
                        .put("customTarget", resolved.customTarget)
                        .put("awgJunkCount", resolved.awgJunkCount)
                        .put("awgJunkMin", resolved.awgJunkMin)
                        .put("awgJunkMax", resolved.awgJunkMax)
                        .put("awgI1", resolved.awgI1)
                        .put("masqueSni", resolved.masqueSni)
                        .put("masqueAttempts", resolved.masqueAttempts)
                        .put("includeNodes", JSONArray(resolved.includeNodes))
                        .put("includeCountries", JSONArray(resolved.includeCountries))
                        .put("mtu", resolved.mtu)
                        .put("dns", JSONArray(resolved.dns))
                        .put("speedTest", resolved.speedTest)
                        .put("throughEndpoint", resolved.throughEndpoint)
                        .toString()
                    viewModel.start(OperationRequest("scan", payload, preset.id, resolved.protocol))
                },
                enabled = !operation.running,
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.start_scan)) }
            if (operation.operation == "scan") {
                OperationPanel(operation, viewModel::stop, viewModel::dismissOperation)
            }
        }
    }
}

@Composable
private fun PresetChip(value: ScanPreset, selected: ScanPreset, label: Int, onSelect: (ScanPreset) -> Unit) {
    FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(stringResource(label)) })
}

@Composable
private fun ToggleRow(label: Int, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                role = Role.Switch,
                onValueChange = onCheckedChange
            )
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: Int, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
private fun NumberField(value: String, onValueChange: (String) -> Unit, label: String, modifier: Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
        modifier = modifier
    )
}

private fun String.intOr(default: Int): Int = toIntOrNull() ?: default

private fun String.stringList(): List<String> = split(',').map(String::trim).filter(String::isNotEmpty)
