package com.omnicontrol.core.model

sealed interface AssistantState {
    data object Idle : AssistantState
    data object Listening : AssistantState
    data object Processing : AssistantState
    data object Observing : AssistantState
    data class Executing(val step: String, val index: Int, val total: Int? = null) : AssistantState
    data object Waiting : AssistantState
    data class Confirming(val question: String) : AssistantState
    data class Paused(val reason: String) : AssistantState
    data class Error(val message: String) : AssistantState
    data object Stopped : AssistantState
}

data class ChatItem(
    val id: String,
    val role: ChatRole,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val actionDetail: ActionCardDetail? = null
)

enum class ChatRole { USER, ASSISTANT, ACTION, SYSTEM }

data class ActionCardDetail(
    val actionType: String,
    val targetDescription: String,
    val isSuccess: Boolean,
    val durationMs: Long
)

data class UiState(
    val assistant: AssistantState = AssistantState.Idle,
    val panelExpanded: Boolean = false,
    val sessionActive: Boolean = false,
    val partialTranscript: String = "",
    val messages: List<ChatItem> = emptyList(),
    val currentApp: String? = null,
    val audioLevel: Float = 0f,
    val homeBrainInUse: Boolean = false,
    val targetBounds: RectBounds? = null,
    val debugElements: List<ScreenElement> = emptyList()
)
