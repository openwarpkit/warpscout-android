package io.github.openwarpkit.warpscout.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class ToolsScreenTest {
    @Test
    fun `SOCKS offers every protocol supported by the core`() {
        assertEquals(
            listOf("wg", "awg", "masque", "masque-h2"),
            socksProtocolOptions.map(SocksProtocolOption::id)
        )
    }

    @Test
    fun `SOCKS request passes the selected protocol to the tunnel`() {
        val parameters = socksOperationParameters("192.0.2.1:2408", 1080, "awg")

        assertEquals("awg", parameters.protocol)
        assertEquals("awg", parameters.tunnelProtocol)
        assertEquals("192.0.2.1:2408", parameters.endpoint)
        assertEquals(1080, parameters.port)
        assertEquals(2, parameters.timeoutSec)
    }
}
