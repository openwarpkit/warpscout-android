package io.github.openwarpkit.warpscout.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.SaveAlt
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.openwarpkit.warpscout.R
import io.github.openwarpkit.warpscout.core.StoredScanOptions
import io.github.openwarpkit.warpscout.core.parseStoredScanOptions
import io.github.openwarpkit.warpscout.data.HistoryEntity
import io.github.openwarpkit.warpscout.data.ReportImageColumn
import io.github.openwarpkit.warpscout.data.ReportImageDocument
import io.github.openwarpkit.warpscout.data.ReportImageRow
import io.github.openwarpkit.warpscout.data.ReportImageRowStyle
import io.github.openwarpkit.warpscout.ui.AppViewModel
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

internal data class ReportEndpoint(
    val endpoint: String,
    val region: String,
    val node: String,
    val country: String,
    val nodeLocation: String,
    val endpointPingMs: Double,
    val tunnelPingMs: Double,
    val lossPercent: Double,
    val speedMbps: Double,
    val working: Boolean,
    val durable: Boolean
)

internal enum class ReportColumn(val label: Int, val width: Dp) {
    Status(R.string.report_status, 68.dp),
    Endpoint(R.string.report_endpoint, 154.dp),
    EndpointPing(R.string.endpoint_ping, 78.dp),
    TunnelPing(R.string.tunnel_ping, 78.dp),
    Loss(R.string.loss, 54.dp),
    Region(R.string.seen_as, 78.dp),
    Node(R.string.node, 52.dp),
    NodeLocation(R.string.node_location, 128.dp),
    Speed(R.string.speed, 84.dp)
}

internal enum class ReportSortDirection {
    Ascending,
    Descending
}

internal data class ReportSort(
    val column: ReportColumn,
    val direction: ReportSortDirection
)

internal enum class ScanSettingId(val label: Int) {
    ScanType(R.string.scan_type),
    Protocol(R.string.protocol),
    OuterEndpoint(R.string.through_endpoint),
    InnerProtocol(R.string.inner_protocol),
    IpFamily(R.string.ip_family),
    PortScanMode(R.string.port_scan_mode),
    Port(R.string.port),
    Target(R.string.custom_target),
    Timeout(R.string.timeout),
    Jobs(R.string.jobs),
    Sampling(R.string.sampling),
    TunnelPings(R.string.tunnel_pings),
    PingTarget(R.string.ping_target),
    BestBy(R.string.best_endpoint_by),
    SpeedTest(R.string.speed_test),
    AWGParameters(R.string.awg_parameters),
    AWGI1(R.string.awg_i1),
    MASQUESNI(R.string.masque_sni),
    MASQUEAttempts(R.string.masque_attempts),
    IncludeNodes(R.string.include_nodes),
    IncludeCountries(R.string.include_countries),
    ExcludeNodes(R.string.exclude_nodes),
    ExcludeCountries(R.string.exclude_countries),
    MTU(R.string.mtu),
    DNS(R.string.dns),
    ConfigurationFormat(R.string.configuration_format)
}

internal data class ScanSettingValue(val id: ScanSettingId, val value: String)

internal data class DisplayScanSetting(val label: String, val value: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportScreen(
    viewModel: AppViewModel,
    historyId: Long,
    onBack: () -> Unit,
    onUseForScan: () -> Unit,
    onViewConfig: (String) -> Unit
) {
    val history by viewModel.history.collectAsStateWithLifecycle()
    val operation by viewModel.operation.collectAsStateWithLifecycle()
    val exportError by viewModel.exportError.collectAsStateWithLifecycle()
    val item = history.firstOrNull { it.id == historyId }
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val copyScope = rememberCoroutineScope()
    val settingsClipboardLabel = stringResource(R.string.scan_settings)
    val settingsCopiedMessage = stringResource(R.string.scan_settings_copied)
    val date = item?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it.startedAt))
    }

    LaunchedEffect(exportError) {
        val message = exportError ?: return@LaunchedEffect
        snackbar.showSnackbar(message)
        viewModel.clearExportError()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (date == null) {
                            stringResource(R.string.report_title)
                        } else {
                            stringResource(R.string.report_title_with_date, date)
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = stringResource(R.string.navigate_up)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        if (item == null || item.resultJson == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp)
            ) {
                Text(stringResource(R.string.no_results))
            }
        } else {
            ReportContent(
                item = item,
                actionsEnabled = !operation.running,
                scanDockVisible = operation.operation == "scan",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onOpen = { viewModel.openReport(item) },
                onShare = { viewModel.shareReport(item) },
                onSaveImage = viewModel::saveReportImage,
                onShareImage = viewModel::shareReportImage,
                onApplySettings = {
                    viewModel.applyHistoryScan(item)
                    onUseForScan()
                },
                onCopySettings = { content ->
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText(settingsClipboardLabel, content))
                    copyScope.launch {
                        snackbar.currentSnackbarData?.dismiss()
                        snackbar.showSnackbar(settingsCopiedMessage)
                    }
                },
                onViewConfig = onViewConfig
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReportContent(
    item: HistoryEntity,
    actionsEnabled: Boolean,
    scanDockVisible: Boolean,
    modifier: Modifier,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onSaveImage: (ReportImageDocument, Uri) -> Unit,
    onShareImage: (ReportImageDocument) -> Unit,
    onApplySettings: () -> Unit,
    onCopySettings: (String) -> Unit,
    onViewConfig: (String) -> Unit
) {
    val results = remember(item.resultJson) { parseReport(item.resultJson.orEmpty()) }
    val working = results.count { it.working && it.durable }
    val tableScroll = rememberScrollState()
    var tableExpanded by rememberSaveable(item.id) { mutableStateOf(false) }
    var hideEmptyColumns by rememberSaveable(item.id) { mutableStateOf(true) }
    var sortColumn by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    var sortDirection by rememberSaveable(item.id) { mutableStateOf<String?>(null) }
    val sort = remember(sortColumn, sortDirection) {
        val column = sortColumn?.let { runCatching { ReportColumn.valueOf(it) }.getOrNull() }
        val direction = sortDirection?.let { runCatching { ReportSortDirection.valueOf(it) }.getOrNull() }
        if (column != null && direction != null) ReportSort(column, direction) else null
    }
    val visibleColumns = remember(results, hideEmptyColumns) {
        visibleReportColumns(results, hideEmptyColumns)
    }
    val displayedResults = remember(results, sort) { sortReportResults(results, sort) }
    val settings = remember(item.preset, item.protocol, item.optionsJson) {
        scanSettings(item.preset, item.protocol, item.optionsJson)
    }
    val displayedSettings = settings.map { setting ->
        DisplayScanSetting(
            label = stringResource(setting.id.label),
            value = localizedScanSettingValue(setting)
        )
    }
    val settingsTitle = stringResource(R.string.scan_settings)
    val settingsCopyText = scanSettingsCopyText(settingsTitle, displayedSettings)
    val imageTitle = stringResource(R.string.report_image_title)
    val imageProtocol = stringResource(R.string.protocol)
    val imageWorking = stringResource(R.string.report_image_working)
    val imageDate = stringResource(R.string.report_image_date)
    val workingLabel = stringResource(R.string.status_working)
    val tornDownLabel = stringResource(R.string.status_torn_down)
    val failedLabel = stringResource(R.string.status_failed)
    val columnLabels = visibleColumns.map { stringResource(it.label) }
    val formattedDate = remember(item.startedAt) {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(item.startedAt))
    }
    val imageDocument = remember(
        item.id,
        item.protocol,
        formattedDate,
        displayedResults,
        visibleColumns,
        columnLabels,
        imageTitle,
        imageProtocol,
        imageWorking,
        imageDate,
        workingLabel,
        tornDownLabel,
        failedLabel
    ) {
        ReportImageDocument(
            historyId = item.id,
            title = imageTitle,
            metadata = listOf(
                imageProtocol to item.protocol.uppercase(),
                imageWorking to "$working / ${results.size}",
                imageDate to formattedDate
            ),
            columns = visibleColumns.mapIndexed { index, column ->
                ReportImageColumn(
                    label = columnLabels[index],
                    width = (column.width.value * 2f).roundToInt()
                )
            },
            rows = displayedResults.map { result ->
                ReportImageRow(
                    values = visibleColumns.map { column ->
                        reportImageValue(result, column, workingLabel, tornDownLabel, failedLabel)
                    },
                    style = when {
                        !result.working -> ReportImageRowStyle.Failed
                        result.durable -> ReportImageRowStyle.Working
                        else -> ReportImageRowStyle.TornDown
                    }
                )
            }
        )
    }
    var pendingImageDocument by remember { mutableStateOf<ReportImageDocument?>(null) }
    val createImageDocument = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val document = pendingImageDocument
        pendingImageDocument = null
        if (uri != null && document != null) onSaveImage(document, uri)
    }

    LazyColumn(
        modifier = modifier,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            bottom = if (scanDockVisible) 112.dp else 24.dp
        )
    ) {
        item("report-summary") {
            ReportSection(
                title = stringResource(R.string.report_section),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.report_summary, working, results.size),
                    style = MaterialTheme.typography.bodyLarge
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = onOpen, enabled = actionsEnabled) {
                        Text(stringResource(R.string.open_txt))
                    }
                    OutlinedButton(onClick = onShare, enabled = actionsEnabled) {
                        Text(stringResource(R.string.share_txt))
                    }
                }
                OutlinedButton(onClick = { tableExpanded = !tableExpanded }) {
                    Icon(
                        if (tableExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null
                    )
                    Text(
                        stringResource(
                            if (tableExpanded) R.string.hide_report_table else R.string.show_report_table
                        ),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            HorizontalDivider()
        }
        if (tableExpanded) {
            item("report-table-options") {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = hideEmptyColumns,
                        onClick = { hideEmptyColumns = !hideEmptyColumns },
                        label = { Text(stringResource(R.string.hide_empty_columns)) }
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            enabled = actionsEnabled,
                            onClick = {
                                pendingImageDocument = imageDocument
                                createImageDocument.launch("warpscout-report-${item.id}.png")
                            }
                        ) {
                            Icon(Icons.Outlined.SaveAlt, contentDescription = null)
                            Text(
                                stringResource(R.string.save_report_image),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        OutlinedButton(
                            enabled = actionsEnabled,
                            onClick = { onShareImage(imageDocument) }
                        ) {
                            Icon(Icons.Outlined.Share, contentDescription = null)
                            Text(
                                stringResource(R.string.share_report_image),
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
            item("report-header") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(tableScroll)
                ) {
                    ReportHeader(
                        columns = visibleColumns,
                        sort = sort,
                        onSort = { column ->
                            val next = nextReportSort(sort, column)
                            sortColumn = next.column.name
                            sortDirection = next.direction.name
                        }
                    )
                }
            }
            items(displayedResults, key = { "report-${it.endpoint}" }) { endpoint ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(tableScroll)
                ) {
                    ReportTableRow(endpoint, visibleColumns)
                }
                HorizontalDivider()
            }
        }
        item("best-endpoint") {
            ReportSection(
                title = stringResource(R.string.best_endpoint),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                BestEndpointDetails(item)
            }
            HorizontalDivider()
        }
        item("scan-settings") {
            ReportSection(
                title = settingsTitle,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Column(Modifier.fillMaxWidth()) {
                    displayedSettings.forEachIndexed { index, setting ->
                        ScanSettingRow(setting)
                        if (index < displayedSettings.lastIndex) HorizontalDivider()
                    }
                }
                Button(
                    onClick = onApplySettings,
                    enabled = actionsEnabled,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.use_scan_settings))
                }
                OutlinedButton(
                    onClick = { onCopySettings(settingsCopyText) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null)
                    Text(
                        stringResource(R.string.copy_scan_settings),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            HorizontalDivider()
        }
        item("configuration") {
            ReportSection(
                title = stringResource(R.string.configuration_section),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    when (item.protocol.lowercase()) {
                        "wg" -> ConfigButton(
                            R.string.wireguard_config,
                            "wireguard",
                            actionsEnabled,
                            onViewConfig
                        )
                        "awg" -> ConfigButton(
                            R.string.amneziawg_config,
                            "amneziawg",
                            actionsEnabled,
                            onViewConfig
                        )
                        "masque", "masque-h2" -> ConfigButton(
                            R.string.usque_config,
                            "usque",
                            actionsEnabled,
                            onViewConfig
                        )
                    }
                    ConfigButton(R.string.mihomo_config, "mihomo", actionsEnabled, onViewConfig)
                }
            }
        }
    }
}

@Composable
private fun ScanSettingRow(setting: DisplayScanSetting) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            setting.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            setting.value,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun localizedScanSettingValue(setting: ScanSettingValue): String = when (setting.id) {
    ScanSettingId.ScanType -> stringResource(
        when (setting.value) {
            "durable" -> R.string.preset_durable
            "full" -> R.string.preset_full
            else -> R.string.preset_standard
        }
    )
    ScanSettingId.Protocol,
    ScanSettingId.InnerProtocol -> protocolDisplayName(setting.value)
    ScanSettingId.IpFamily -> stringResource(
        if (setting.value == "ipv6") R.string.ipv6 else R.string.ipv4
    )
    ScanSettingId.PortScanMode -> stringResource(
        when (setting.value) {
            "open" -> R.string.port_scan_open
            "all" -> R.string.port_scan_all
            "per-endpoint" -> R.string.port_scan_per_endpoint
            else -> R.string.port_scan_first
        }
    )
    ScanSettingId.Port -> if (setting.value == "automatic") {
        stringResource(R.string.setting_automatic)
    } else {
        setting.value
    }
    ScanSettingId.Target -> if (setting.value == "built-in") {
        stringResource(R.string.setting_builtin_pools)
    } else {
        setting.value
    }
    ScanSettingId.Sampling -> if (setting.value == "full") {
        stringResource(R.string.setting_full_sampling)
    } else {
        stringResource(R.string.setting_sample_per_subnet, setting.value.toIntOrNull() ?: 0)
    }
    ScanSettingId.TunnelPings -> if (setting.value == "disabled") {
        stringResource(R.string.setting_disabled)
    } else {
        setting.value
    }
    ScanSettingId.BestBy -> stringResource(
        if (setting.value == "speed") R.string.best_by_speed else R.string.best_by_ping
    )
    ScanSettingId.SpeedTest -> stringResource(
        if (setting.value == "enabled") R.string.setting_enabled else R.string.setting_disabled
    )
    ScanSettingId.AWGI1,
    ScanSettingId.MASQUESNI,
    ScanSettingId.MTU,
    ScanSettingId.DNS -> if (setting.value == "default") {
        stringResource(R.string.setting_default)
    } else {
        setting.value
    }
    else -> setting.value
}

private fun protocolDisplayName(protocol: String): String = when (protocol.lowercase(Locale.ROOT)) {
    "awg" -> "AWG"
    "masque" -> "MASQUE H3"
    "masque-h2" -> "MASQUE H2"
    else -> "WG"
}

internal fun scanSettings(preset: String, reportProtocol: String, optionsJson: String): List<ScanSettingValue> {
    return scanSettings(preset, reportProtocol, parseStoredScanOptions(optionsJson))
}

internal fun scanSettings(
    preset: String,
    reportProtocol: String,
    options: StoredScanOptions
): List<ScanSettingValue> {
    val protocol = options.protocol.ifBlank { reportProtocol }.lowercase(Locale.ROOT)
    val through = options.throughEndpoint.trim()
    val sweepPorts = options.sweepPorts.trim()
    val tunnelPings = options.tunnelPingCount
    val bestBy = options.bestBy.ifBlank { "ping" }

    return buildList {
        add(ScanSettingValue(ScanSettingId.ScanType, preset.ifBlank { "standard" }))
        add(ScanSettingValue(ScanSettingId.Protocol, protocol.ifBlank { "awg" }))
        if (through.isNotEmpty()) {
            add(ScanSettingValue(ScanSettingId.OuterEndpoint, through))
            add(ScanSettingValue(ScanSettingId.InnerProtocol, options.innerProtocol))
        }
        add(ScanSettingValue(ScanSettingId.IpFamily, if (options.ipv6) "ipv6" else "ipv4"))
        add(
            ScanSettingValue(
                ScanSettingId.PortScanMode,
                if (protocol.startsWith("masque")) "per-endpoint" else sweepPorts.ifBlank { "first" }
            )
        )
        add(
            ScanSettingValue(
                ScanSettingId.Port,
                options.port.takeIf { it > 0 }?.toString() ?: "automatic"
            )
        )
        add(
            ScanSettingValue(
                ScanSettingId.Target,
                options.customTarget.trim().ifBlank { "built-in" }
            )
        )
        add(ScanSettingValue(ScanSettingId.Timeout, options.timeoutSec.toString()))
        add(ScanSettingValue(ScanSettingId.Jobs, options.jobs.toString()))
        add(
            ScanSettingValue(
                ScanSettingId.Sampling,
                if (options.full || preset == "full") {
                    "full"
                } else {
                    options.samplePerSubnet.toString()
                }
            )
        )
        add(
            ScanSettingValue(
                ScanSettingId.TunnelPings,
                tunnelPings.takeIf { it > 0 }?.toString() ?: "disabled"
            )
        )
        if (tunnelPings > 0) {
            add(
                ScanSettingValue(
                    ScanSettingId.PingTarget,
                    options.pingTarget.trim().ifBlank { "1.1.1.1" }
                )
            )
        }
        add(ScanSettingValue(ScanSettingId.BestBy, bestBy))
        add(
            ScanSettingValue(
                ScanSettingId.SpeedTest,
                if (options.speedTest || bestBy == "speed") "enabled" else "disabled"
            )
        )
        if (protocol == "awg") {
            val junkCount = options.awgJunkCount.takeIf { it > 0 } ?: 6
            val junkMin = options.awgJunkMin.takeIf { it > 0 } ?: 10
            val junkMax = options.awgJunkMax.takeIf { it > 0 } ?: 50
            add(
                ScanSettingValue(
                    ScanSettingId.AWGParameters,
                    "Jc=$junkCount  Jmin=$junkMin  Jmax=$junkMax"
                )
            )
            add(
                ScanSettingValue(
                    ScanSettingId.AWGI1,
                    options.awgI1.trim().ifBlank { "default" }
                )
            )
        }
        if (protocol.startsWith("masque")) {
            add(
                ScanSettingValue(
                    ScanSettingId.MASQUESNI,
                    options.masqueSni.trim().ifBlank { "default" }
                )
            )
            add(
                ScanSettingValue(
                    ScanSettingId.MASQUEAttempts,
                    options.masqueAttempts.toString()
                )
            )
        }
        addSettingList(options.includeNodes, ScanSettingId.IncludeNodes)
        addSettingList(options.includeCountries, ScanSettingId.IncludeCountries)
        addSettingList(options.excludeNodes, ScanSettingId.ExcludeNodes)
        addSettingList(options.excludeCountries, ScanSettingId.ExcludeCountries)
        add(
            ScanSettingValue(
                ScanSettingId.MTU,
                options.mtu.takeIf { it > 0 }?.toString() ?: "default"
            )
        )
        add(ScanSettingValue(ScanSettingId.DNS, options.dns.joinToString(", ").ifBlank { "default" }))
        options.configurationFormat.trim().takeIf(String::isNotEmpty)?.let {
            add(ScanSettingValue(ScanSettingId.ConfigurationFormat, it))
        }
    }
}

private fun MutableList<ScanSettingValue>.addSettingList(values: List<String>, id: ScanSettingId) {
    values.takeIf(List<String>::isNotEmpty)?.let {
        add(ScanSettingValue(id, it.joinToString(", ")))
    }
}

internal fun scanSettingsCopyText(title: String, settings: List<DisplayScanSetting>): String =
    buildString {
        appendLine(title)
        settings.forEach { appendLine("${it.label}: ${it.value}") }
    }.trimEnd()

@Composable
private fun ReportSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        content()
    }
}

@Composable
private fun BestEndpointDetails(item: HistoryEntity) {
    val context = LocalContext.current
    val endpoints = remember(item.resultJson, item.optionsJson) {
        val options = runCatching { JSONObject(item.optionsJson) }.getOrDefault(JSONObject())
        bestEndpointsByNode(
            results = parseReport(item.resultJson.orEmpty()),
            bestBy = options.optString("bestBy", "ping"),
            sweepPorts = options.optString("sweepPorts")
        )
    }
    if (endpoints.isEmpty()) {
        Text(stringResource(R.string.no_results), color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    var selectedIndex by rememberSaveable(item.id) { mutableIntStateOf(0) }
    selectedIndex = selectedIndex.coerceIn(0, endpoints.lastIndex)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                enabled = selectedIndex > 0,
                onClick = { selectedIndex-- }
            ) {
                Icon(
                    Icons.Outlined.ChevronLeft,
                    contentDescription = stringResource(R.string.previous_best_endpoint)
                )
            }
            Text(
                text = "${endpoints[selectedIndex].node.ifBlank { "-" }} · ${selectedIndex + 1} / ${endpoints.size}",
                style = MaterialTheme.typography.labelLarge,
                fontFamily = FontFamily.Monospace
            )
            IconButton(
                enabled = selectedIndex < endpoints.lastIndex,
                onClick = { selectedIndex++ }
            ) {
                Icon(
                    Icons.Outlined.ChevronRight,
                    contentDescription = stringResource(R.string.next_best_endpoint)
                )
            }
        }
        AnimatedContent(
            targetState = selectedIndex,
            transitionSpec = {
                val direction = if (targetState > initialState) 1 else -1
                (slideInHorizontally { direction * it } + fadeIn()) togetherWith
                    (slideOutHorizontally { -direction * it } + fadeOut()) using
                    SizeTransform(clip = false)
            },
            label = "best-endpoint-page",
            modifier = Modifier.pointerInput(endpoints.size) {
                var dragDistance = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragDistance = 0f },
                    onHorizontalDrag = { change, amount ->
                        change.consume()
                        dragDistance += amount
                    },
                    onDragEnd = {
                        when {
                            dragDistance < -48f && selectedIndex < endpoints.lastIndex -> selectedIndex++
                            dragDistance > 48f && selectedIndex > 0 -> selectedIndex--
                        }
                    }
                )
            }
        ) { index ->
            BestEndpointCard(endpoints[index], context)
        }
    }
}

@Composable
private fun BestEndpointCard(endpoint: ReportEndpoint, context: android.content.Context) {
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
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(onClick = {
                    context.getSystemService(ClipboardManager::class.java)
                        .setPrimaryClip(ClipData.newPlainText("WarpScout endpoint", endpoint.endpoint))
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

internal fun bestEndpointsByNode(
    results: List<ReportEndpoint>,
    bestBy: String = "ping",
    sweepPorts: String = ""
): List<ReportEndpoint> =
    results
        .asSequence()
        .filter { it.working && it.durable }
        .groupBy { if (sweepPorts.isNotEmpty()) it.endpoint else it.node.ifBlank { "-" } }
        .values
        .mapNotNull { group ->
            group.minWithOrNull { first, second -> compareBestEndpoints(first, second, bestBy) }
        }
        .sortedWith { first, second -> compareBestEndpoints(first, second, bestBy) }

private fun compareBestEndpoints(first: ReportEndpoint, second: ReportEndpoint, bestBy: String): Int {
    if (bestBy == "speed") {
        val speed = second.speedMbps.compareTo(first.speedMbps)
        if (speed != 0) return speed
    }
    val loss = first.lossPercent.compareTo(second.lossPercent)
    if (loss != 0) return loss
    val firstPing = first.tunnelPingMs.takeIf { it > 0 } ?: first.endpointPingMs
    val secondPing = second.tunnelPingMs.takeIf { it > 0 } ?: second.endpointPingMs
    val ping = firstPing.compareTo(secondPing)
    if (ping != 0) return ping
    return first.endpoint.compareTo(second.endpoint)
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
private fun ReportHeader(
    columns: List<ReportColumn>,
    sort: ReportSort?,
    onSort: (ReportColumn) -> Unit
) {
    Row(
        modifier = Modifier
            .width(columns.tableWidth())
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        columns.forEach { column ->
            HeaderCell(
                column = column,
                selectedDirection = sort?.takeIf { it.column == column }?.direction,
                onClick = { onSort(column) }
            )
        }
    }
}

@Composable
private fun ReportTableRow(result: ReportEndpoint, columns: List<ReportColumn>) {
    val background = when {
        !result.working -> MaterialTheme.colorScheme.surface
        result.durable -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.28f)
        else -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
    }
    Row(
        modifier = Modifier
            .width(columns.tableWidth())
            .background(background)
            .padding(vertical = 7.dp)
    ) {
        columns.forEach { column ->
            when (column) {
                ReportColumn.Status -> DataCell(
                    text = when {
                        !result.working -> stringResource(R.string.status_failed)
                        result.durable -> stringResource(R.string.status_working)
                        else -> stringResource(R.string.status_torn_down)
                    },
                    width = column.width,
                    color = when {
                        !result.working -> MaterialTheme.colorScheme.error
                        result.durable -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.tertiary
                    }
                )
                ReportColumn.Endpoint -> DataCell(result.endpoint, column.width, monospace = true)
                ReportColumn.EndpointPing -> DataCell(
                    formatLatency(result.endpointPingMs),
                    column.width,
                    monospace = true
                )
                ReportColumn.TunnelPing -> DataCell(
                    formatLatency(result.tunnelPingMs),
                    column.width,
                    monospace = true
                )
                ReportColumn.Loss -> DataCell(
                    formatPercent(result.lossPercent, result.tunnelPingMs > 0),
                    column.width,
                    monospace = true
                )
                ReportColumn.Region -> DataCell(withFlag(result.region, result.region), column.width)
                ReportColumn.Node -> DataCell(
                    result.node.ifBlank { "-" },
                    column.width,
                    monospace = true
                )
                ReportColumn.NodeLocation -> DataCell(
                    withFlag(result.country, result.nodeLocation),
                    column.width
                )
                ReportColumn.Speed -> DataCell(
                    formatSpeed(result.speedMbps),
                    column.width,
                    monospace = true
                )
            }
        }
    }
}

private fun List<ReportColumn>.tableWidth(): Dp = fold(0.dp) { total, column -> total + column.width }

internal fun visibleReportColumns(
    results: List<ReportEndpoint>,
    hideEmptyColumns: Boolean
): List<ReportColumn> {
    if (!hideEmptyColumns) return ReportColumn.entries
    return ReportColumn.entries.filter { column ->
        when (column) {
            ReportColumn.Status, ReportColumn.Endpoint -> true
            ReportColumn.EndpointPing -> results.any { it.endpointPingMs > 0 }
            ReportColumn.TunnelPing, ReportColumn.Loss -> results.any { it.tunnelPingMs > 0 }
            ReportColumn.Region -> results.any { it.region.isNotBlank() }
            ReportColumn.Node -> results.any { it.node.isNotBlank() }
            ReportColumn.NodeLocation -> results.any { it.country.isNotBlank() || it.nodeLocation.isNotBlank() }
            ReportColumn.Speed -> results.any { it.speedMbps > 0 }
        }
    }
}

@Composable
private fun HeaderCell(
    column: ReportColumn,
    selectedDirection: ReportSortDirection?,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(column.width)
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = stringResource(column.label),
            modifier = if (selectedDirection == null) Modifier else Modifier.padding(end = 12.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (selectedDirection != null) {
            Icon(
                imageVector = if (selectedDirection == ReportSortDirection.Ascending) {
                    Icons.Outlined.ArrowUpward
                } else {
                    Icons.Outlined.ArrowDownward
                },
                contentDescription = stringResource(
                    if (selectedDirection == ReportSortDirection.Ascending) {
                        R.string.sorted_ascending
                    } else {
                        R.string.sorted_descending
                    }
                ),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(12.dp)
            )
        }
    }
}

internal fun nextReportSort(current: ReportSort?, column: ReportColumn): ReportSort {
    if (current?.column == column) {
        return current.copy(
            direction = if (current.direction == ReportSortDirection.Ascending) {
                ReportSortDirection.Descending
            } else {
                ReportSortDirection.Ascending
            }
        )
    }
    val direction = when (column) {
        ReportColumn.EndpointPing,
        ReportColumn.TunnelPing,
        ReportColumn.Loss,
        ReportColumn.Speed -> ReportSortDirection.Descending
        else -> ReportSortDirection.Ascending
    }
    return ReportSort(column, direction)
}

internal fun sortReportResults(
    results: List<ReportEndpoint>,
    sort: ReportSort?
): List<ReportEndpoint> {
    if (sort == null) return results
    return results.sortedWith { first, second ->
        val firstMissing = !first.hasData(sort.column)
        val secondMissing = !second.hasData(sort.column)
        when {
            firstMissing && !secondMissing -> 1
            !firstMissing && secondMissing -> -1
            firstMissing && secondMissing -> 0
            else -> {
                val comparison = first.compareColumn(second, sort.column)
                if (sort.direction == ReportSortDirection.Ascending) comparison else -comparison
            }
        }
    }
}

private fun ReportEndpoint.hasData(column: ReportColumn): Boolean = when (column) {
    ReportColumn.Status, ReportColumn.Endpoint -> true
    ReportColumn.EndpointPing -> endpointPingMs > 0
    ReportColumn.TunnelPing, ReportColumn.Loss -> tunnelPingMs > 0
    ReportColumn.Region -> region.isNotBlank()
    ReportColumn.Node -> node.isNotBlank()
    ReportColumn.NodeLocation -> country.isNotBlank() || nodeLocation.isNotBlank()
    ReportColumn.Speed -> speedMbps > 0
}

private fun ReportEndpoint.compareColumn(other: ReportEndpoint, column: ReportColumn): Int = when (column) {
    ReportColumn.Status -> statusKey().compareTo(other.statusKey(), ignoreCase = true)
    ReportColumn.Endpoint -> endpoint.compareTo(other.endpoint, ignoreCase = true)
    ReportColumn.EndpointPing -> endpointPingMs.compareTo(other.endpointPingMs)
    ReportColumn.TunnelPing -> tunnelPingMs.compareTo(other.tunnelPingMs)
    ReportColumn.Loss -> lossPercent.compareTo(other.lossPercent)
    ReportColumn.Region -> region.compareTo(other.region, ignoreCase = true)
    ReportColumn.Node -> node.compareTo(other.node, ignoreCase = true)
    ReportColumn.NodeLocation -> nodeLocation.ifBlank { country }
        .compareTo(other.nodeLocation.ifBlank { other.country }, ignoreCase = true)
    ReportColumn.Speed -> speedMbps.compareTo(other.speedMbps)
}

private fun ReportEndpoint.statusKey(): String = when {
    !working -> "failed"
    durable -> "working"
    else -> "torn_down"
}

@Composable
private fun DataCell(
    text: String,
    width: Dp,
    color: Color = MaterialTheme.colorScheme.onSurface,
    monospace: Boolean = false
) {
    Text(
        text = text,
        modifier = Modifier
            .width(width)
            .padding(horizontal = 6.dp),
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

internal fun parseReport(json: String): List<ReportEndpoint> {
    val results = runCatching { JSONObject(json).optJSONArray("results") }.getOrNull() ?: return emptyList()
    return (0 until results.length()).mapNotNull { index ->
        results.optJSONObject(index)?.let {
            ReportEndpoint(
                endpoint = it.optString("endpoint"),
                region = it.optString("region"),
                node = it.optString("node"),
                country = it.optString("country"),
                nodeLocation = it.optString("nodeLocation"),
                endpointPingMs = measurementOrZero(it.optDouble("endpointPingMs")),
                tunnelPingMs = measurementOrZero(it.optDouble("tunnelPingMs")),
                lossPercent = measurementOrZero(it.optDouble("lossPercent")),
                speedMbps = measurementOrZero(it.optDouble("speedMbps")),
                working = it.optBoolean("working"),
                durable = it.optBoolean("durable")
            )
        }
    }
}

fun countryFlag(code: String): String {
    val normalized = code.trim().uppercase()
    if (normalized.length != 2 || normalized.any { it !in 'A'..'Z' }) return ""
    return normalized.map { Character.toChars(0x1F1E6 + (it - 'A')).concatToString() }.joinToString("")
}

internal fun withFlag(code: String, value: String): String {
    val label = value.ifBlank { code }.ifBlank { "-" }
    return countryFlag(code).takeIf(String::isNotBlank)?.let { "$it $label" } ?: label
}

internal fun formatLatency(value: Double): String = if (value > 0) "%.1f ms".format(value) else "-"

internal fun measurementOrZero(value: Double): Double = if (value.isFinite()) value else 0.0

internal fun formatPercent(value: Double, measured: Boolean): String =
    if (measured && value.isFinite() && value >= 0) "%.0f%%".format(value) else "-"

internal fun formatSpeed(value: Double): String = if (value > 0) "%.1f Mbps".format(value) else "-"

private fun reportImageValue(
    result: ReportEndpoint,
    column: ReportColumn,
    workingLabel: String,
    tornDownLabel: String,
    failedLabel: String
): String = when (column) {
    ReportColumn.Status -> when {
        !result.working -> failedLabel
        result.durable -> workingLabel
        else -> tornDownLabel
    }
    ReportColumn.Endpoint -> result.endpoint
    ReportColumn.EndpointPing -> formatLatency(result.endpointPingMs)
    ReportColumn.TunnelPing -> formatLatency(result.tunnelPingMs)
    ReportColumn.Loss -> formatPercent(result.lossPercent, result.tunnelPingMs > 0)
    ReportColumn.Region -> withFlag(result.region, result.region)
    ReportColumn.Node -> result.node.ifBlank { "-" }
    ReportColumn.NodeLocation -> withFlag(result.country, result.nodeLocation)
    ReportColumn.Speed -> formatSpeed(result.speedMbps)
}
