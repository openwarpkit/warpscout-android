package io.github.openwarpkit.warpscout.ui

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.flow.StateFlow

internal class SocksFormStateStore(private val savedStateHandle: SavedStateHandle) {
    val protocol: StateFlow<String> = savedStateHandle.getStateFlow(PROTOCOL_KEY, DEFAULT_PROTOCOL)
    val endpoint: StateFlow<String> = savedStateHandle.getStateFlow(ENDPOINT_KEY, "")
    val port: StateFlow<String> = savedStateHandle.getStateFlow(PORT_KEY, DEFAULT_PORT)

    fun setProtocol(value: String) {
        savedStateHandle[PROTOCOL_KEY] = value
    }

    fun setEndpoint(value: String) {
        savedStateHandle[ENDPOINT_KEY] = value
    }

    fun setPort(value: String) {
        savedStateHandle[PORT_KEY] = value
    }

    companion object {
        const val DEFAULT_PROTOCOL = "awg"
        const val DEFAULT_PORT = "1080"
        private const val PROTOCOL_KEY = "socks_protocol"
        private const val ENDPOINT_KEY = "socks_endpoint"
        private const val PORT_KEY = "socks_port"
    }
}
