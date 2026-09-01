package io.github.openwarpkit.warpscout.core

import org.json.JSONObject

enum class ScanPreset(val id: String) {
    Standard("standard"),
    Durable("durable"),
    Full("full")
}

const val DEFAULT_SCAN_PROTOCOL = "awg"

data class ExpertScanOptions(
    val protocol: String = DEFAULT_SCAN_PROTOCOL,
    val innerProtocol: String = "wg",
    val ipv6: Boolean = false,
    val port: Int = 0,
    val timeoutSec: Int = 2,
    val jobs: Int = 10,
    val customTarget: String = "",
    val tunnelPingCount: Int = 0,
    val awgJunkCount: Int = 0,
    val awgJunkMin: Int = 0,
    val awgJunkMax: Int = 0,
    val awgI1: String = "",
    val masqueSni: String = "",
    val masqueAttempts: Int = 3,
    val includeNodes: List<String> = emptyList(),
    val includeCountries: List<String> = emptyList(),
    val mtu: Int = 0,
    val dns: List<String> = emptyList(),
    val speedTest: Boolean = false,
    val bestBy: String = "ping",
    val sweepPorts: String = "",
    val pingTarget: String = "",
    val throughEndpoint: String = ""
)

data class ResolvedScanOptions(
    val protocol: String,
    val innerProtocol: String,
    val ipv6: Boolean,
    val port: Int,
    val timeoutSec: Int,
    val jobs: Int,
    val samplePerSubnet: Int,
    val full: Boolean,
    val tunnelPingCount: Int,
    val customTarget: String,
    val awgJunkCount: Int,
    val awgJunkMin: Int,
    val awgJunkMax: Int,
    val awgI1: String,
    val masqueSni: String,
    val masqueAttempts: Int,
    val includeNodes: List<String>,
    val includeCountries: List<String>,
    val mtu: Int,
    val dns: List<String>,
    val speedTest: Boolean,
    val bestBy: String,
    val sweepPorts: String,
    val pingTarget: String,
    val throughEndpoint: String
)

data class StoredScanOptions(
    val protocol: String = "",
    val innerProtocol: String = "wg",
    val ipv6: Boolean = false,
    val port: Int = 0,
    val timeoutSec: Int = 2,
    val jobs: Int = 10,
    val samplePerSubnet: Int = 5,
    val full: Boolean = false,
    val tunnelPingCount: Int = 0,
    val customTarget: String = "",
    val bestBy: String = "ping",
    val speedTest: Boolean = false,
    val sweepPorts: String = "",
    val pingTarget: String = "",
    val awgJunkCount: Int = 0,
    val awgJunkMin: Int = 0,
    val awgJunkMax: Int = 0,
    val awgI1: String = "",
    val masqueSni: String = "",
    val masqueAttempts: Int = 3,
    val includeNodes: List<String> = emptyList(),
    val includeCountries: List<String> = emptyList(),
    val excludeNodes: List<String> = emptyList(),
    val excludeCountries: List<String> = emptyList(),
    val mtu: Int = 0,
    val dns: List<String> = emptyList(),
    val throughEndpoint: String = "",
    val configurationFormat: String = ""
)

data class HistoryScanProfile(
    val sourceHistoryId: Long,
    val preset: ScanPreset,
    val expert: ExpertScanOptions
)

fun resolveScanOptions(
    preset: ScanPreset,
    expertEnabled: Boolean,
    expert: ExpertScanOptions
): ResolvedScanOptions {
    val selected = if (expertEnabled) expert else ExpertScanOptions()
    val presetPings = when (preset) {
        ScanPreset.Standard -> selected.tunnelPingCount
        ScanPreset.Durable -> 10
        ScanPreset.Full -> if (expertEnabled) selected.tunnelPingCount.takeIf { it > 0 } ?: 10 else 10
    }
    val pings = if (selected.pingTarget.isNotBlank() && presetPings <= 0) 10 else presetPings
    return ResolvedScanOptions(
        protocol = selected.protocol,
        innerProtocol = selected.innerProtocol,
        ipv6 = selected.ipv6,
        port = selected.port,
        timeoutSec = selected.timeoutSec,
        jobs = selected.jobs,
        samplePerSubnet = if (preset == ScanPreset.Full) 0 else 5,
        full = preset == ScanPreset.Full,
        tunnelPingCount = pings,
        customTarget = selected.customTarget,
        awgJunkCount = selected.awgJunkCount,
        awgJunkMin = selected.awgJunkMin,
        awgJunkMax = selected.awgJunkMax,
        awgI1 = selected.awgI1,
        masqueSni = selected.masqueSni,
        masqueAttempts = selected.masqueAttempts,
        includeNodes = selected.includeNodes,
        includeCountries = selected.includeCountries,
        mtu = selected.mtu,
        dns = selected.dns,
        speedTest = selected.speedTest,
        bestBy = selected.bestBy,
        sweepPorts = selected.sweepPorts,
        pingTarget = selected.pingTarget,
        throughEndpoint = selected.throughEndpoint
    )
}

fun parseStoredScanOptions(optionsJson: String): StoredScanOptions {
    val options = runCatching { JSONObject(optionsJson) }.getOrDefault(JSONObject())
    return StoredScanOptions(
        protocol = options.optString("protocol"),
        innerProtocol = options.optString("innerProtocol", "wg"),
        ipv6 = options.optBoolean("ipv6"),
        port = options.optInt("port"),
        timeoutSec = options.optInt("timeoutSec", 2),
        jobs = options.optInt("jobs", 10),
        samplePerSubnet = options.optInt("samplePerSubnet", 5),
        full = options.optBoolean("full"),
        tunnelPingCount = options.optInt("tunnelPingCount"),
        customTarget = options.optString("customTarget"),
        bestBy = options.optString("bestBy", "ping"),
        speedTest = options.optBoolean("speedTest"),
        sweepPorts = options.optString("sweepPorts"),
        pingTarget = options.optString("pingTarget"),
        awgJunkCount = options.optInt("awgJunkCount"),
        awgJunkMin = options.optInt("awgJunkMin"),
        awgJunkMax = options.optInt("awgJunkMax"),
        awgI1 = options.optString("awgI1"),
        masqueSni = options.optString("masqueSni"),
        masqueAttempts = options.optInt("masqueAttempts", 3),
        includeNodes = options.stringList("includeNodes"),
        includeCountries = options.stringList("includeCountries"),
        excludeNodes = options.stringList("excludeNodes"),
        excludeCountries = options.stringList("excludeCountries"),
        mtu = options.optInt("mtu"),
        dns = options.stringList("dns"),
        throughEndpoint = options.optString("throughEndpoint"),
        configurationFormat = options.optString("configurationFormat")
    )
}

fun historyScanProfile(
    historyId: Long,
    presetId: String,
    reportProtocol: String,
    optionsJson: String
): HistoryScanProfile = historyScanProfile(
    historyId = historyId,
    presetId = presetId,
    reportProtocol = reportProtocol,
    options = parseStoredScanOptions(optionsJson)
)

fun historyScanProfile(
    historyId: Long,
    presetId: String,
    reportProtocol: String,
    options: StoredScanOptions
): HistoryScanProfile {
    val protocol = options.protocol.ifBlank { reportProtocol }.ifBlank { DEFAULT_SCAN_PROTOCOL }
    return HistoryScanProfile(
        sourceHistoryId = historyId,
        preset = ScanPreset.entries.firstOrNull { it.id == presetId } ?: ScanPreset.Standard,
        expert = ExpertScanOptions(
            protocol = protocol,
            innerProtocol = options.innerProtocol.ifBlank { "wg" },
            ipv6 = options.ipv6,
            port = options.port,
            timeoutSec = options.timeoutSec,
            jobs = options.jobs,
            customTarget = options.customTarget,
            tunnelPingCount = options.tunnelPingCount,
            awgJunkCount = options.awgJunkCount,
            awgJunkMin = options.awgJunkMin,
            awgJunkMax = options.awgJunkMax,
            awgI1 = options.awgI1,
            masqueSni = options.masqueSni,
            masqueAttempts = options.masqueAttempts,
            includeNodes = options.includeNodes,
            includeCountries = options.includeCountries,
            mtu = options.mtu,
            dns = options.dns,
            speedTest = options.speedTest,
            bestBy = options.bestBy.ifBlank { "ping" },
            sweepPorts = options.sweepPorts,
            pingTarget = options.pingTarget,
            throughEndpoint = options.throughEndpoint
        )
    )
}

private fun JSONObject.stringList(key: String): List<String> {
    val values = optJSONArray(key) ?: return emptyList()
    return (0 until values.length()).mapNotNull { index ->
        values.optString(index).trim().takeIf(String::isNotEmpty)
    }
}
