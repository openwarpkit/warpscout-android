package io.github.openwarpkit.warpscout.core

enum class ScanPreset(val id: String) {
    Standard("standard"),
    Durable("durable"),
    Full("full")
}

data class ExpertScanOptions(
    val protocol: String = "wg",
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
    val throughEndpoint: String
)

fun resolveScanOptions(
    preset: ScanPreset,
    expertEnabled: Boolean,
    expert: ExpertScanOptions
): ResolvedScanOptions {
    val selected = if (expertEnabled) expert else ExpertScanOptions()
    val pings = when (preset) {
        ScanPreset.Standard -> selected.tunnelPingCount
        ScanPreset.Durable -> 10
        ScanPreset.Full -> if (expertEnabled) selected.tunnelPingCount.takeIf { it > 0 } ?: 10 else 10
    }
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
        throughEndpoint = selected.throughEndpoint
    )
}
