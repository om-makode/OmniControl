package com.omnicontrol.core.model

/** What the assistant is doing right now. Drives the orb colour, caption and notification. */
enum class Phase {
    IDLE,       // orb visible, not listening
    LISTENING,  // mic open, waiting for speech
    HEARING,    // user is speaking (partial text arriving)
    THINKING,   // reading the screen / asking the brain
    ACTING,     // performing a touch / typing / opening an app
    SPEAKING,   // reading a reply aloud
    CONFIRM,    // waiting for "yes" / "no"
    ERROR
}

enum class ChatRole { USER, ASSISTANT, ACTION, SYSTEM }

data class ChatItem(
    val id: Long = nextId(),
    val role: ChatRole,
    val content: String,
    val success: Boolean? = null,
    val timestamp: Long = System.currentTimeMillis()
) {
    companion object {
        private var counter = 0L
        @Synchronized private fun nextId(): Long = ++counter
    }
}

data class UiState(
    val serviceRunning: Boolean = false,
    val phase: Phase = Phase.IDLE,
    val sessionActive: Boolean = false,
    val busy: Boolean = false,
    val panelOpen: Boolean = false,
    /** Live words while the user is speaking (or the last thing they said). */
    val transcript: String = "",
    /** Short line: what the assistant is doing or just said. */
    val status: String = "",
    val currentApp: String = "",
    val stepIndex: Int = 0,
    val audioLevel: Float = 0f,
    val brainName: String = "",
    val confirmQuestion: String? = null,
    val messages: List<ChatItem> = emptyList()
)
