package io.github.openwarpkit.warpscout.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryNavigationStateTest {
    @Test
    fun `completed scan navigation closes active report detail`() {
        val request = HistoryNavigationState().openCompletedScan(42L)

        assertEquals(HISTORY_ROUTE, request.destination)
        assertFalse(request.savePoppedState)
    }

    @Test
    fun `completed scan navigation cannot restore a past report`() {
        val request = HistoryNavigationState().openCompletedScan(42L)

        assertTrue(request.clearSavedHistory)
        assertFalse(request.restoreState)
    }

    @Test
    fun `completed scan navigation passes selected history id`() {
        val state = HistoryNavigationState()

        val request = state.openCompletedScan(73L)

        assertEquals(73L, request.focus.historyId)
        assertSame(request.focus, state.pendingFocus)
    }

    @Test
    fun `focus request is consumed only once`() {
        val state = HistoryNavigationState()
        val request = state.openCompletedScan(12L).focus

        assertTrue(state.consumeFocus(request))
        assertNull(state.pendingFocus)
        assertFalse(state.consumeFocus(request))
    }

    @Test
    fun `stale consumption cannot clear a newer focus request`() {
        val state = HistoryNavigationState()
        val first = state.openCompletedScan(12L).focus
        val second = state.openCompletedScan(34L).focus

        assertFalse(state.consumeFocus(first))
        assertEquals(second, state.pendingFocus)
    }

    @Test
    fun `repeated completed scan selection creates a fresh request`() {
        val state = HistoryNavigationState()
        val first = state.openCompletedScan(12L).focus
        val second = state.openCompletedScan(12L).focus

        assertNotEquals(first.requestId, second.requestId)
        assertEquals(first.historyId, second.historyId)
    }

    @Test
    fun `focus resolves selected record by stable id`() {
        val request = HistoryFocusRequest(requestId = 1L, historyId = 48L)

        val resolution = resolveHistoryFocus(request, historyLoaded = true, historyIds = listOf(91L, 48L, 7L))

        assertEquals(HistoryFocusResolution.Found(request, index = 1), resolution)
    }

    @Test
    fun `focus waits until history finishes loading`() {
        val request = HistoryFocusRequest(requestId = 1L, historyId = 48L)

        val resolution = resolveHistoryFocus(request, historyLoaded = false, historyIds = emptyList())

        assertSame(HistoryFocusResolution.Waiting, resolution)
    }

    @Test
    fun `missing history record does not fall back to another scan`() {
        val request = HistoryFocusRequest(requestId = 1L, historyId = 48L)

        val resolution = resolveHistoryFocus(request, historyLoaded = true, historyIds = listOf(91L, 7L))

        assertEquals(HistoryFocusResolution.Missing(request), resolution)
    }

    @Test
    fun `missing history record consumes pending focus`() {
        val state = HistoryNavigationState()
        val request = state.openCompletedScan(48L).focus
        val resolution = resolveHistoryFocus(request, historyLoaded = true, historyIds = emptyList())

        assertEquals(HistoryFocusResolution.Missing(request), resolution)
        assertTrue(state.consumeFocus(request))
        assertNull(state.pendingFocus)
    }
}
