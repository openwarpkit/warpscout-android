package io.github.openwarpkit.warpscout.ui.screen

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
}
