package io.github.openwarpkit.warpscout.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class AboutScreenTest {
    @Test
    fun `shortens upstream commit to seven characters`() {
        assertEquals(
            "v0.14.0 (2fe3507)",
            upstreamBaseDisplay("v0.14.0", "2fe3507ffd3915f68efc65537d2adae0b5d1eff8")
        )
    }

    @Test
    fun `keeps upstream commits shorter than seven characters`() {
        assertEquals("v0.14.0 (abc123)", upstreamBaseDisplay("v0.14.0", "abc123"))
    }

    @Test
    fun `omits parentheses when upstream commit is empty`() {
        assertEquals("v0.14.0", upstreamBaseDisplay("v0.14.0", ""))
    }
}
