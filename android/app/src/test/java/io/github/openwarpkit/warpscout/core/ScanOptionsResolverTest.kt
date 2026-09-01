package io.github.openwarpkit.warpscout.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScanOptionsResolverTest {
    @Test
    fun disabledExpertModeUsesStandardValues() {
        val expert = ExpertScanOptions(
            protocol = "masque-h2",
            innerProtocol = "awg",
            ipv6 = true,
            port = 8443,
            timeoutSec = 30,
            jobs = 64,
            customTarget = "example.test",
            tunnelPingCount = 25,
            includeNodes = listOf("FRA"),
            speedTest = true,
            bestBy = "speed",
            sweepPorts = "all",
            pingTarget = "example.com",
            throughEndpoint = "127.0.0.1:500"
        )

        val resolved = resolveScanOptions(ScanPreset.Standard, false, expert)

        assertEquals("awg", resolved.protocol)
        assertEquals("wg", resolved.innerProtocol)
        assertFalse(resolved.ipv6)
        assertEquals(0, resolved.port)
        assertEquals(2, resolved.timeoutSec)
        assertEquals(10, resolved.jobs)
        assertEquals("", resolved.customTarget)
        assertEquals(0, resolved.tunnelPingCount)
        assertTrue(resolved.includeNodes.isEmpty())
        assertFalse(resolved.speedTest)
        assertEquals("ping", resolved.bestBy)
        assertEquals("", resolved.sweepPorts)
        assertEquals("", resolved.pingTarget)
        assertEquals("", resolved.throughEndpoint)
    }

    @Test
    fun enabledExpertModeUsesSelectedValues() {
        val expert = ExpertScanOptions(
            protocol = "awg",
            ipv6 = true,
            port = 2408,
            timeoutSec = 8,
            jobs = 24,
            tunnelPingCount = 7,
            bestBy = "speed",
            sweepPorts = "open",
            pingTarget = "one.one.one.one"
        )

        val resolved = resolveScanOptions(ScanPreset.Standard, true, expert)

        assertEquals("awg", resolved.protocol)
        assertTrue(resolved.ipv6)
        assertEquals(2408, resolved.port)
        assertEquals(8, resolved.timeoutSec)
        assertEquals(24, resolved.jobs)
        assertEquals(7, resolved.tunnelPingCount)
        assertEquals("speed", resolved.bestBy)
        assertEquals("open", resolved.sweepPorts)
        assertEquals("one.one.one.one", resolved.pingTarget)
    }

    @Test
    fun customPingTargetEnablesDurabilityBurst() {
        val resolved = resolveScanOptions(
            ScanPreset.Standard,
            expertEnabled = true,
            ExpertScanOptions(pingTarget = "example.com")
        )

        assertEquals(10, resolved.tunnelPingCount)
    }

    @Test
    fun historyProfileRestoresEveryScanFormValue() {
        val profile = historyScanProfile(
            historyId = 42L,
            presetId = "full",
            reportProtocol = "wg",
            options = StoredScanOptions(
                protocol = "awg",
                innerProtocol = "awg",
                ipv6 = true,
                port = 0,
                timeoutSec = 8,
                jobs = 24,
                tunnelPingCount = 12,
                customTarget = "192.0.2.0/24",
                bestBy = "speed",
                speedTest = false,
                sweepPorts = "all",
                pingTarget = "example.com",
                awgJunkCount = 7,
                awgJunkMin = 20,
                awgJunkMax = 80,
                awgI1 = "custom-i1",
                masqueSni = "sni.example",
                masqueAttempts = 5,
                includeNodes = listOf("FRA", "AMS"),
                includeCountries = listOf("DE", "NL"),
                mtu = 1280,
                dns = listOf("1.1.1.1", "1.0.0.1"),
                throughEndpoint = "188.114.96.1:2408"
            )
        )

        assertEquals(42L, profile.sourceHistoryId)
        assertEquals(ScanPreset.Full, profile.preset)
        assertEquals("awg", profile.expert.protocol)
        assertEquals("awg", profile.expert.innerProtocol)
        assertTrue(profile.expert.ipv6)
        assertEquals(8, profile.expert.timeoutSec)
        assertEquals(24, profile.expert.jobs)
        assertEquals("192.0.2.0/24", profile.expert.customTarget)
        assertEquals(12, profile.expert.tunnelPingCount)
        assertEquals("speed", profile.expert.bestBy)
        assertEquals("all", profile.expert.sweepPorts)
        assertEquals("example.com", profile.expert.pingTarget)
        assertEquals(7, profile.expert.awgJunkCount)
        assertEquals("custom-i1", profile.expert.awgI1)
        assertEquals(listOf("FRA", "AMS"), profile.expert.includeNodes)
        assertEquals(listOf("DE", "NL"), profile.expert.includeCountries)
        assertEquals(listOf("1.1.1.1", "1.0.0.1"), profile.expert.dns)
        assertEquals("188.114.96.1:2408", profile.expert.throughEndpoint)
    }

    @Test
    fun historyProfileFallsBackToStoredReportProtocolAndStandardPreset() {
        val profile = historyScanProfile(
            historyId = 1L,
            presetId = "",
            reportProtocol = "masque-h2",
            options = StoredScanOptions()
        )

        assertEquals(ScanPreset.Standard, profile.preset)
        assertEquals("masque-h2", profile.expert.protocol)
        assertEquals("ping", profile.expert.bestBy)
    }
}
