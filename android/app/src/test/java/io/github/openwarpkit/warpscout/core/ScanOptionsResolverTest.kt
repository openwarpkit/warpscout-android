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
            throughEndpoint = "127.0.0.1:500"
        )

        val resolved = resolveScanOptions(ScanPreset.Standard, false, expert)

        assertEquals("wg", resolved.protocol)
        assertEquals("wg", resolved.innerProtocol)
        assertFalse(resolved.ipv6)
        assertEquals(0, resolved.port)
        assertEquals(2, resolved.timeoutSec)
        assertEquals(10, resolved.jobs)
        assertEquals("", resolved.customTarget)
        assertEquals(0, resolved.tunnelPingCount)
        assertTrue(resolved.includeNodes.isEmpty())
        assertFalse(resolved.speedTest)
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
            tunnelPingCount = 7
        )

        val resolved = resolveScanOptions(ScanPreset.Standard, true, expert)

        assertEquals("awg", resolved.protocol)
        assertTrue(resolved.ipv6)
        assertEquals(2408, resolved.port)
        assertEquals(8, resolved.timeoutSec)
        assertEquals(24, resolved.jobs)
        assertEquals(7, resolved.tunnelPingCount)
    }
}
