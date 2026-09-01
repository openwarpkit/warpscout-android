package io.github.openwarpkit.warpscout.ui

import io.github.openwarpkit.warpscout.core.OperationState
import org.junit.Assert.assertEquals
import org.junit.Test

class OperationDockTest {
    @Test
    fun `running SOCKS operation uses the SOCKS dock`() {
        val state = OperationState(operation = "socks", running = true, localPort = 1080)

        assertEquals(OperationDockKind.Socks, operationDockKind(state))
    }

    @Test
    fun `finished SOCKS operation does not leave a dock`() {
        val state = OperationState(operation = "socks", running = false, localPort = 1080)

        assertEquals(OperationDockKind.None, operationDockKind(state))
    }

    @Test
    fun `SOCKS operation without a valid local port falls back to its screen panel`() {
        val state = OperationState(operation = "socks", running = true)

        assertEquals(OperationDockKind.None, operationDockKind(state))
    }

    @Test
    fun `scan keeps its dock after completion`() {
        val state = OperationState(operation = "scan", running = false)

        assertEquals(OperationDockKind.Progress, operationDockKind(state))
    }

    @Test
    fun `discovery operations use the progress dock`() {
        assertEquals(
            OperationDockKind.Progress,
            operationDockKind(OperationState(operation = "find-junk", running = true))
        )
        assertEquals(
            OperationDockKind.Progress,
            operationDockKind(OperationState(operation = "find-sni", running = false))
        )
    }
}
