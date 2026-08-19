package io.github.openwarpkit.warpscout.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class ReportScreenTest {
    @Test
    fun rendersCountryFlagFromIsoCode() {
        assertEquals("\uD83C\uDDF7\uD83C\uDDFA", countryFlag("ru"))
        assertEquals("", countryFlag("RUS"))
    }
}
