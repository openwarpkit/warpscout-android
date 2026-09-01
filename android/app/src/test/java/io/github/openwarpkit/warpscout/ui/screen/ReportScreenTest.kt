package io.github.openwarpkit.warpscout.ui.screen

import io.github.openwarpkit.warpscout.core.StoredScanOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportScreenTest {
    @Test
    fun rendersCountryFlagFromIsoCode() {
        assertEquals("\uD83C\uDDF7\uD83C\uDDFA", countryFlag("ru"))
        assertEquals("", countryFlag("RUS"))
    }

    @Test
    fun usesZeroForMissingOptionalMeasurements() {
        assertEquals(0.0, measurementOrZero(Double.NaN), 0.0)
        assertEquals(0.0, measurementOrZero(Double.POSITIVE_INFINITY), 0.0)
        assertEquals(12.5, measurementOrZero(12.5), 0.0)
    }

    @Test
    fun hidesInvalidLossMeasurement() {
        assertEquals("-", formatPercent(Double.NaN, measured = true))
        assertEquals("-", formatPercent(0.0, measured = false))
        assertEquals("0%", formatPercent(0.0, measured = true))
    }

    @Test
    fun hidesColumnsWithoutAnyMeasurements() {
        val result = ReportEndpoint(
            endpoint = "example:2408",
            region = "FI",
            node = "HEL",
            country = "FI",
            nodeLocation = "Helsinki, FI",
            endpointPingMs = 12.0,
            tunnelPingMs = 0.0,
            lossPercent = 0.0,
            speedMbps = 0.0,
            working = true,
            durable = true
        )

        assertEquals(
            listOf(
                ReportColumn.Status,
                ReportColumn.Endpoint,
                ReportColumn.EndpointPing,
                ReportColumn.Region,
                ReportColumn.Node,
                ReportColumn.NodeLocation
            ),
            visibleReportColumns(listOf(result), hideEmptyColumns = true)
        )
        assertEquals(ReportColumn.entries, visibleReportColumns(listOf(result), hideEmptyColumns = false))
    }

    @Test
    fun selectsExpectedInitialSortDirection() {
        assertEquals(
            ReportSort(ReportColumn.Endpoint, ReportSortDirection.Ascending),
            nextReportSort(null, ReportColumn.Endpoint)
        )
        assertEquals(
            ReportSort(ReportColumn.EndpointPing, ReportSortDirection.Descending),
            nextReportSort(null, ReportColumn.EndpointPing)
        )
        assertEquals(
            ReportSort(ReportColumn.EndpointPing, ReportSortDirection.Ascending),
            nextReportSort(
                ReportSort(ReportColumn.EndpointPing, ReportSortDirection.Descending),
                ReportColumn.EndpointPing
            )
        )
    }

    @Test
    fun keepsMissingMeasurementsAfterSortedValues() {
        fun endpoint(address: String, ping: Double) = ReportEndpoint(
            endpoint = address,
            region = "",
            node = "",
            country = "",
            nodeLocation = "",
            endpointPingMs = ping,
            tunnelPingMs = 0.0,
            lossPercent = 0.0,
            speedMbps = 0.0,
            working = true,
            durable = true
        )
        val results = listOf(endpoint("missing", 0.0), endpoint("slow", 50.0), endpoint("fast", 10.0))

        assertEquals(
            listOf("slow", "fast", "missing"),
            sortReportResults(
                results,
                ReportSort(ReportColumn.EndpointPing, ReportSortDirection.Descending)
            ).map(ReportEndpoint::endpoint)
        )
        assertEquals(
            listOf("fast", "slow", "missing"),
            sortReportResults(
                results,
                ReportSort(ReportColumn.EndpointPing, ReportSortDirection.Ascending)
            ).map(ReportEndpoint::endpoint)
        )
    }

    @Test
    fun selectsBestWorkingEndpointForEveryNode() {
        fun endpoint(
            address: String,
            node: String,
            endpointPing: Double,
            tunnelPing: Double,
            loss: Double,
            working: Boolean = true
        ) = ReportEndpoint(
            endpoint = address,
            region = "",
            node = node,
            country = "",
            nodeLocation = "",
            endpointPingMs = endpointPing,
            tunnelPingMs = tunnelPing,
            lossPercent = loss,
            speedMbps = 0.0,
            working = working,
            durable = working
        )
        val results = listOf(
            endpoint("hel-slow", "HEL", 9.0, 40.0, 0.0),
            endpoint("hel-fast", "HEL", 12.0, 20.0, 0.0),
            endpoint("arn-loss", "ARN", 5.0, 10.0, 2.0),
            endpoint("dme", "DME", 15.0, 0.0, 0.0),
            endpoint("failed", "AMS", 1.0, 1.0, 0.0, working = false)
        )

        assertEquals(
            listOf("dme", "hel-fast", "arn-loss"),
            bestEndpointsByNode(results).map(ReportEndpoint::endpoint)
        )
    }

    @Test
    fun selectsBestEndpointsBySpeedAndKeepsSweptPorts() {
        fun endpoint(address: String, node: String, ping: Double, speed: Double) = ReportEndpoint(
            endpoint = address,
            region = "",
            node = node,
            country = "",
            nodeLocation = "",
            endpointPingMs = ping,
            tunnelPingMs = 0.0,
            lossPercent = 0.0,
            speedMbps = speed,
            working = true,
            durable = true
        )
        val results = listOf(
            endpoint("hel:2408", "HEL", 8.0, 20.0),
            endpoint("hel:500", "HEL", 30.0, 90.0),
            endpoint("arn:2408", "ARN", 10.0, 50.0)
        )

        assertEquals(
            listOf("hel:500", "arn:2408"),
            bestEndpointsByNode(results, bestBy = "speed").map(ReportEndpoint::endpoint)
        )
        assertEquals(
            listOf("hel:500", "arn:2408", "hel:2408"),
            bestEndpointsByNode(results, bestBy = "speed", sweepPorts = "open")
                .map(ReportEndpoint::endpoint)
        )
    }

    @Test
    fun buildsCompleteEffectiveScanSettings() {
        val settings = scanSettings(
            preset = "durable",
            reportProtocol = "awg",
            options = StoredScanOptions(
                protocol = "awg",
                innerProtocol = "wg",
                ipv6 = true,
                timeoutSec = 8,
                jobs = 24,
                tunnelPingCount = 10,
                customTarget = "192.0.2.0/24",
                bestBy = "speed",
                sweepPorts = "all",
                pingTarget = "example.com",
                includeNodes = listOf("FRA", "AMS"),
                includeCountries = listOf("DE"),
                mtu = 1280,
                dns = listOf("1.1.1.1", "1.0.0.1"),
                throughEndpoint = "188.114.96.1:2408"
            )
        ).associate { it.id to it.value }

        assertEquals("durable", settings[ScanSettingId.ScanType])
        assertEquals("all", settings[ScanSettingId.PortScanMode])
        assertEquals("192.0.2.0/24", settings[ScanSettingId.Target])
        assertEquals("10", settings[ScanSettingId.TunnelPings])
        assertEquals("example.com", settings[ScanSettingId.PingTarget])
        assertEquals("speed", settings[ScanSettingId.BestBy])
        assertEquals("enabled", settings[ScanSettingId.SpeedTest])
        assertEquals("Jc=6  Jmin=10  Jmax=50", settings[ScanSettingId.AWGParameters])
        assertEquals("FRA, AMS", settings[ScanSettingId.IncludeNodes])
        assertEquals("188.114.96.1:2408", settings[ScanSettingId.OuterEndpoint])
        assertEquals("wg", settings[ScanSettingId.InnerProtocol])
    }

    @Test
    fun restoresDefaultsForOldReportsAndFormatsClipboardText() {
        val settings = scanSettings("", "awg", StoredScanOptions()).associate { it.id to it.value }

        assertEquals("standard", settings[ScanSettingId.ScanType])
        assertEquals("first", settings[ScanSettingId.PortScanMode])
        assertEquals("built-in", settings[ScanSettingId.Target])
        assertEquals("disabled", settings[ScanSettingId.TunnelPings])
        assertEquals("default", settings[ScanSettingId.DNS])

        assertEquals(
            listOf(
                "Scan settings",
                "Scan type: Standard",
                "Port scan mode: First reachable"
            ).joinToString("\n"),
            scanSettingsCopyText(
                "Scan settings",
                listOf(
                    DisplayScanSetting("Scan type", "Standard"),
                    DisplayScanSetting("Port scan mode", "First reachable")
                )
            )
        )
    }

    @Test
    fun describesMasquePortsAsPerEndpoint() {
        val settings = scanSettings(
            preset = "standard",
            reportProtocol = "masque",
            options = StoredScanOptions(protocol = "masque")
        ).associate { it.id to it.value }

        assertEquals("per-endpoint", settings[ScanSettingId.PortScanMode])
    }
}
