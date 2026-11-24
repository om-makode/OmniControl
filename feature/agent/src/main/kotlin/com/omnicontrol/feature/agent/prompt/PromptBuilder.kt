package com.omnicontrol.feature.agent.prompt

import com.omnicontrol.core.model.Observation

object PromptBuilder {

    private const val SYSTEM_PROMPT = """You are OmniControl, an offline AI operator controlling an Android device.
You observe the screen and output ONE valid JSON action to fulfill the user's goal.
Response MUST be in this format:
{"thought": "reasoning", "action": {"type": "action_type", ...}, "say": "brief user update"}

Allowed actions:
- {"type": "open_app", "app": "name"}
- {"type": "open_url", "url": "https://..."}
- {"type": "click", "target": <id>}
- {"type": "long_press", "target": <id>}
- {"type": "type_text", "target": <id>, "text": "text", "submit": true/false}
- {"type": "clear_text", "target": <id>}
- {"type": "scroll", "direction": "down"|"up"}
- {"type": "back"}
- {"type": "home"}
- {"type": "recents"}
- {"type": "select", "target": <id>}
- {"type": "wait", "ms": 1000}
- {"type": "read_screen", "question": "..."}
- {"type": "ask_user", "question": "..."}
- {"type": "done", "summary": "..."}

Rules:
1. Always choose an element ID from the CURRENT screen observation. Never invent element IDs.
2. If the goal is fulfilled, emit {"type": "done", "summary": "..."}.
3. If unsure or needing password/login, emit {"type": "ask_user", "question": "..."}."""

    fun buildPrompt(
        goal: String,
        observation: Observation,
        historyTurns: List<Pair<String, String>> = emptyList(),
        currentSubGoal: String? = null
    ): String {
        val sb = StringBuilder()
        sb.appendLine("<|system|>")
        sb.appendLine(SYSTEM_PROMPT)
        sb.appendLine("<|end|>")

        if (historyTurns.isNotEmpty()) {
            sb.appendLine("<|history|>")
            for ((role, text) in historyTurns.takeLast(6)) {
                sb.appendLine("$role: $text")
            }
            sb.appendLine("<|end|>")
        }

        sb.appendLine("<|observation|>")
        sb.appendLine(observation.toLlmPromptText(maxElements = 60))
        sb.appendLine("<|end|>")

        sb.appendLine("<|user|>")
        sb.appendLine("Goal: $goal")
        if (!currentSubGoal.isNullOrBlank()) {
            sb.appendLine("Current Sub-goal: $currentSubGoal")
        }
        sb.appendLine("Output your JSON action:")
        sb.append("<|assistant|>\n")

        return sb.toString()
    }
}
