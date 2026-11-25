package com.omnicontrol.feature.overlay

import com.omnicontrol.core.model.ChatItem
import com.omnicontrol.core.model.UiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Shared, observable assistant state (read by the overlay windows and the main app). */
object AssistantHub {
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    var serviceRunning: Boolean
        get() = _state.value.serviceRunning
        internal set(value) = _state.update { it.copy(serviceRunning = value) }

    internal fun update(block: (UiState) -> UiState) = _state.update(block)

    internal fun post(item: ChatItem) = _state.update { s ->
        s.copy(messages = (s.messages + item).takeLast(80))
    }

    internal fun clearMessages() = _state.update { it.copy(messages = emptyList()) }
}
