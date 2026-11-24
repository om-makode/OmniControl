package com.omnicontrol.feature.overlay.state

import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.model.AssistantState
import com.omnicontrol.core.model.ChatItem
import com.omnicontrol.core.model.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

sealed interface AssistantEvent {
    data object BubbleTap : AssistantEvent
    data object ExpandPanel : AssistantEvent
    data object MinimizePanel : AssistantEvent
    data object StartSession : AssistantEvent
    data object StopSession : AssistantEvent
    data object SetListening : AssistantEvent
    data object SetProcessing : AssistantEvent
    data object SetObserving : AssistantEvent
    data class SetExecuting(val step: String, val index: Int, val total: Int? = null) : AssistantEvent
    data object SetWaiting : AssistantEvent
    data class SetConfirming(val question: String) : AssistantEvent
    data class SetPaused(val reason: String) : AssistantEvent
    data class SetError(val message: String) : AssistantEvent
    data object SetIdle : AssistantEvent
    data class AppendMessage(val message: ChatItem) : AssistantEvent
    data class UpdateTranscript(val partial: String) : AssistantEvent
    data class UpdateAudioLevel(val level: Float) : AssistantEvent
    data class SetCurrentApp(val app: String?) : AssistantEvent
    data class SetHomeBrain(val inUse: Boolean) : AssistantEvent
    data class SetTargetHighlight(val bounds: com.omnicontrol.core.model.RectBounds?) : AssistantEvent
    data class SetDebugElements(val elements: List<com.omnicontrol.core.model.ScreenElement>) : AssistantEvent
    data class SetAssistantState(val assistantState: AssistantState) : AssistantEvent
}

class AssistantStateStore {

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun dispatch(event: AssistantEvent) {
        _state.update { current ->
            reduce(current, event)
        }
    }

    fun reduce(current: UiState, event: AssistantEvent): UiState {
        OmniLog.d(TAG, "Reduce event: ${event.javaClass.simpleName} from state: ${current.assistant.javaClass.simpleName}")
        return when (event) {
            is AssistantEvent.BubbleTap -> {
                if (current.panelExpanded) {
                    current.copy(panelExpanded = false)
                } else {
                    current.copy(panelExpanded = true)
                }
            }
            is AssistantEvent.ExpandPanel -> current.copy(panelExpanded = true)
            is AssistantEvent.MinimizePanel -> current.copy(panelExpanded = false)
            is AssistantEvent.StartSession -> {
                current.copy(
                    sessionActive = true,
                    assistant = AssistantState.Listening,
                    partialTranscript = ""
                )
            }
            is AssistantEvent.StopSession -> {
                current.copy(
                    sessionActive = false,
                    assistant = AssistantState.Idle,
                    partialTranscript = "",
                    audioLevel = 0f,
                    messages = emptyList(),
                    targetBounds = null,
                    panelExpanded = false
                )
            }
            is AssistantEvent.SetListening -> current.copy(assistant = AssistantState.Listening)
            is AssistantEvent.SetProcessing -> current.copy(assistant = AssistantState.Processing)
            is AssistantEvent.SetObserving -> current.copy(assistant = AssistantState.Observing)
            is AssistantEvent.SetExecuting -> current.copy(
                assistant = AssistantState.Executing(event.step, event.index, event.total)
            )
            is AssistantEvent.SetWaiting -> current.copy(assistant = AssistantState.Waiting)
            is AssistantEvent.SetConfirming -> current.copy(
                assistant = AssistantState.Confirming(event.question),
                panelExpanded = true
            )
            is AssistantEvent.SetPaused -> current.copy(assistant = AssistantState.Paused(event.reason))
            is AssistantEvent.SetError -> current.copy(assistant = AssistantState.Error(event.message))
            is AssistantEvent.SetIdle -> current.copy(
                assistant = AssistantState.Idle,
                sessionActive = false,
                partialTranscript = ""
            )
            is AssistantEvent.AppendMessage -> current.copy(
                messages = current.messages + event.message
            )
            is AssistantEvent.UpdateTranscript -> current.copy(
                partialTranscript = event.partial
            )
            is AssistantEvent.UpdateAudioLevel -> current.copy(
                audioLevel = event.level.coerceIn(0f, 1f)
            )
            is AssistantEvent.SetCurrentApp -> current.copy(
                currentApp = event.app
            )
            is AssistantEvent.SetHomeBrain -> current.copy(
                homeBrainInUse = event.inUse
            )
            is AssistantEvent.SetTargetHighlight -> current.copy(
                targetBounds = event.bounds
            )
            is AssistantEvent.SetDebugElements -> current.copy(
                debugElements = event.elements
            )
            is AssistantEvent.SetAssistantState -> current.copy(
                assistant = event.assistantState
            )
        }
    }

    companion object {
        private const val TAG = "OmniOverlay"
    }
}
