package com.omnicontrol.feature.overlay

import com.omnicontrol.core.model.AssistantState
import com.omnicontrol.core.model.ChatItem
import com.omnicontrol.core.model.ChatRole
import com.omnicontrol.core.model.UiState
import com.omnicontrol.feature.overlay.state.AssistantEvent
import com.omnicontrol.feature.overlay.state.AssistantStateStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantStateStoreTest {

    private val store = AssistantStateStore()

    @Test
    fun testBubbleTapTogglesPanel() {
        val initial = UiState(panelExpanded = false)
        val expanded = store.reduce(initial, AssistantEvent.BubbleTap)
        assertTrue(expanded.panelExpanded)

        val minimized = store.reduce(expanded, AssistantEvent.BubbleTap)
        assertFalse(minimized.panelExpanded)
    }

    @Test
    fun testSessionStartAndStop() {
        val initial = UiState()
        val started = store.reduce(initial, AssistantEvent.StartSession)
        assertTrue(started.sessionActive)
        assertEquals(AssistantState.Listening, started.assistant)

        val message = ChatItem(id = "1", role = ChatRole.USER, content = "Open YouTube")
        val withMessage = store.reduce(started, AssistantEvent.AppendMessage(message))
        assertEquals(1, withMessage.messages.size)

        val stopped = store.reduce(withMessage, AssistantEvent.StopSession)
        assertFalse(stopped.sessionActive)
        assertEquals(AssistantState.Idle, stopped.assistant)
        assertEquals("", stopped.partialTranscript)
        assertTrue(stopped.messages.isEmpty())
    }

    @Test
    fun testStateProgression() {
        var state = UiState()
        state = store.reduce(state, AssistantEvent.SetProcessing)
        assertEquals(AssistantState.Processing, state.assistant)

        state = store.reduce(state, AssistantEvent.SetObserving)
        assertEquals(AssistantState.Observing, state.assistant)

        state = store.reduce(state, AssistantEvent.SetExecuting("Tapping search", 1, 3))
        assertTrue(state.assistant is AssistantState.Executing)
        val executing = state.assistant as AssistantState.Executing
        assertEquals("Tapping search", executing.step)
        assertEquals(1, executing.index)
        assertEquals(3, executing.total)

        state = store.reduce(state, AssistantEvent.SetWaiting)
        assertEquals(AssistantState.Waiting, state.assistant)
    }

    @Test
    fun testMessagesAppend() {
        val initial = UiState()
        val message = ChatItem(id = "1", role = ChatRole.USER, content = "Open YouTube")
        val updated = store.reduce(initial, AssistantEvent.AppendMessage(message))

        assertEquals(1, updated.messages.size)
        assertEquals("Open YouTube", updated.messages.first().content)
    }

    @Test
    fun testTargetHighlightAndDebugElements() {
        val initial = UiState()
        val bounds = com.omnicontrol.core.model.RectBounds(10, 20, 100, 150)
        val highlighted = store.reduce(initial, AssistantEvent.SetTargetHighlight(bounds))
        assertEquals(bounds, highlighted.targetBounds)

        val elements = listOf(com.omnicontrol.core.model.ScreenElement(id = 1, text = "Button"))
        val debugged = store.reduce(highlighted, AssistantEvent.SetDebugElements(elements))
        assertEquals(elements, debugged.debugElements)
    }
}
