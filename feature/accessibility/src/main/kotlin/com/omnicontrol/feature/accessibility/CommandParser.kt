package com.omnicontrol.feature.accessibility

import com.omnicontrol.core.model.AgentAction

object CommandParser {

    fun parse(input: String): AgentAction? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val parts = trimmed.split("\\s+".toRegex())
        val verb = parts[0].lowercase()

        return when (verb) {
            "click", "tap" -> {
                val id = parts.getOrNull(1)?.toIntOrNull() ?: return null
                AgentAction(type = "click", target = id)
            }
            "long_press", "longpress", "press" -> {
                val id = parts.getOrNull(1)?.toIntOrNull() ?: return null
                AgentAction(type = "long_press", target = id)
            }
            "type", "text" -> {
                val id = parts.getOrNull(1)?.toIntOrNull() ?: return null
                val text = parts.drop(2).joinToString(" ")
                AgentAction(type = "type_text", target = id, text = text, submit = true)
            }
            "clear" -> {
                val id = parts.getOrNull(1)?.toIntOrNull() ?: return null
                AgentAction(type = "clear_text", target = id)
            }
            "scroll" -> {
                val dir = parts.getOrNull(1)?.lowercase() ?: "down"
                AgentAction(type = "scroll", direction = dir)
            }
            "open" -> {
                val app = parts.drop(1).joinToString(" ")
                if (app.isBlank()) return null
                AgentAction(type = "open_app", app = app)
            }
            "url" -> {
                val url = parts.getOrNull(1) ?: return null
                AgentAction(type = "open_url", url = url)
            }
            "back" -> AgentAction(type = "back")
            "home" -> AgentAction(type = "home")
            "recents" -> AgentAction(type = "recents")
            "notifications" -> AgentAction(type = "notifications")
            "quick_settings", "quicksettings" -> AgentAction(type = "quick_settings")
            "wait" -> {
                val ms = parts.getOrNull(1)?.toLongOrNull() ?: 1000L
                AgentAction(type = "wait", ms = ms)
            }
            else -> null
        }
    }
}
