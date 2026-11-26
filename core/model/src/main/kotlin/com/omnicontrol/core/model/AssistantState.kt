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
    /** A pending "Proposed action" card waiting for the user's decision. */
    val confirm: ConfirmRequest? = null,
    val messages: List<ChatItem> = emptyList(),
    val numberTags: List<NumberTag> = emptyList()
)

/**
 * What OmniControl is about to do, shown to the user before it does it.
 * Only the user (voice "yes"/"confirm" or a tap) can approve; the AI can never answer this itself.
 */
data class ConfirmRequest(
    /** e.g. "Send a WhatsApp message" */
    val title: String,
    /** e.g. "To Rahul: Hey, I will reach in 20 minutes." */
    val detail: String = "",
    /** Button text, e.g. "Confirm send" */
    val confirmLabel: String = "Confirm",
    /** Extra caution text (privacy, unknown link, repeated action...). */
    val warning: String? = null,
    /** Spoken question. */
    val spoken: String = title
)
