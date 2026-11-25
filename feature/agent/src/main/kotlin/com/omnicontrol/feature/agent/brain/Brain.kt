package com.omnicontrol.feature.agent.brain

import com.omnicontrol.core.model.AgentAction
import com.omnicontrol.core.model.ScreenState

/** Everything the brain needs to choose the next step. */
data class BrainRequest(
    val goal: String,
    val sessionMemory: List<String>,
    val notes: List<String>,
    val steps: List<String>,
    val screen: ScreenState,
    /** JPEG (base64) of the screen with element numbers drawn on it, or null. */
    val screenshotBase64: String?,
    /** Screen pixels per screenshot pixel (for tap_xy). */
    val screenshotScale: Float,
    val hint: String?
)

enum class DecisionKind { ACTION, DONE, ANSWER, ASK }

data class BrainDecision(
    val kind: DecisionKind,
    val thought: String = "",
    val action: AgentAction? = null,
    /** Text for the user (done summary, answer, or question). */
    val message: String? = null,
    /** A fact the brain wants to remember for later steps. */
    val note: String? = null
)

interface Brain {
    val name: String
    /** Whether screenshots are sent with each step. */
    val seesImages: Boolean
    suspend fun decide(request: BrainRequest): Result<BrainDecision>
    /** Plain text completion (used for "Test connection"). */
    suspend fun ping(): Result<String>
    fun close() {}
}

class BrainException(message: String) : Exception(message)
