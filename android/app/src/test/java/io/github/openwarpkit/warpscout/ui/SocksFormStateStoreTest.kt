package io.github.openwarpkit.warpscout.ui

import androidx.lifecycle.SavedStateHandle
import org.junit.Assert.assertEquals
import org.junit.Test

class SocksFormStateStoreTest {
    @Test
    fun `SOCKS form defaults to AWG and port 1080`() {
        val state = SocksFormStateStore(SavedStateHandle())

        assertEquals("awg", state.protocol.value)
        assertEquals("", state.endpoint.value)
        assertEquals("1080", state.port.value)
    }

    @Test
    fun `SOCKS form values survive state holder recreation`() {
        val savedStateHandle = SavedStateHandle()
        SocksFormStateStore(savedStateHandle).apply {
            setProtocol("masque-h2")
            setEndpoint("192.0.2.8:8443")
            setPort("9090")
        }

        val restored = SocksFormStateStore(savedStateHandle)

        assertEquals("masque-h2", restored.protocol.value)
        assertEquals("192.0.2.8:8443", restored.endpoint.value)
        assertEquals("9090", restored.port.value)
    }
}
