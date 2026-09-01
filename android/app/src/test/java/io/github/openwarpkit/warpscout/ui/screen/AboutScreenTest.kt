package io.github.openwarpkit.warpscout.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class AboutScreenTest {
    @Test
    fun `shortens upstream commit to seven characters`() {
        assertEquals(
            "v0.16.0 (db4ac9e)",
            upstreamBaseDisplay("v0.16.0", "db4ac9ebae8d942191b8e8351f2c3a37a375bd66")
        )
    }

    @Test
    fun `keeps upstream commits shorter than seven characters`() {
        assertEquals("v0.16.0 (abc123)", upstreamBaseDisplay("v0.16.0", "abc123"))
    }

    @Test
    fun `omits parentheses when upstream commit is empty`() {
        assertEquals("v0.16.0", upstreamBaseDisplay("v0.16.0", ""))
    }
}
