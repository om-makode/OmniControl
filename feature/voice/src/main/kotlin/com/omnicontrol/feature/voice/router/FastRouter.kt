package com.omnicontrol.feature.voice.router

import com.omnicontrol.core.model.AgentAction

sealed interface FastRouteResult {
    data class Action(val action: AgentAction, val spokenReply: String? = null) : FastRouteResult
    data object Stop : FastRouteResult
    data object Done : FastRouteResult
    data class PassToLlm(val utterance: String) : FastRouteResult
}

object FastRouter {

    private val STOP_REGEX = Regex("^(stop|cancel|wait|pause|hold on|freeze)$", RegexOption.IGNORE_CASE)
    private val DONE_REGEX = Regex("^(done|stop listening|that's all|that is all|bye|goodbye|exit)$", RegexOption.IGNORE_CASE)
    private val BACK_REGEX = Regex("^(go back|back|return|previous page)$", RegexOption.IGNORE_CASE)
    private val HOME_REGEX = Regex("^(go home|home|home screen|launcher)$", RegexOption.IGNORE_CASE)
    private val RECENTS_REGEX = Regex("^(show recents|recents|recent apps|open app switcher|app switcher)$", RegexOption.IGNORE_CASE)
    private val NOTIFS_REGEX = Regex("^(show notifications|notifications|open notifications|notification shade)$", RegexOption.IGNORE_CASE)
    private val QUICK_SETTINGS_REGEX = Regex("^(show quick settings|quick settings|open quick settings)$", RegexOption.IGNORE_CASE)
    private val OPEN_APP_REGEX = Regex("^(?:open|launch|start|switch to)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val OPEN_URL_REGEX = Regex("^(?:open url|go to website|browse to|open link)\\s+(.+)$", RegexOption.IGNORE_CASE)
    private val SCROLL_DOWN_REGEX = Regex("^(?:scroll down|swipe down|page down)$", RegexOption.IGNORE_CASE)
    private val SCROLL_UP_REGEX = Regex("^(?:scroll up|swipe up|page up)$", RegexOption.IGNORE_CASE)
    private val SCROLL_LEFT_REGEX = Regex("^(?:scroll left|swipe left)$", RegexOption.IGNORE_CASE)
    private val SCROLL_RIGHT_REGEX = Regex("^(?:scroll right|swipe right)$", RegexOption.IGNORE_CASE)

    fun route(utterance: String): FastRouteResult {
        val trimmed = utterance.trim().replace(Regex("[.,!?;:]+$"), "")
        if (trimmed.isEmpty()) return FastRouteResult.PassToLlm("")

        if (STOP_REGEX.matches(trimmed)) {
            return FastRouteResult.Stop
        }

        if (DONE_REGEX.matches(trimmed)) {
            return FastRouteResult.Done
        }

        if (BACK_REGEX.matches(trimmed)) {
            return FastRouteResult.Action(AgentAction(type = "back"), "Going back")
        }

        if (HOME_REGEX.matches(trimmed)) {
            return FastRouteResult.Action(AgentAction(type = "home"), "Going home")
        }

        if (RECENTS_REGEX.matches(trimmed)) {
            return FastRouteResult.Action(AgentAction(type = "recents"), "Opening recents")
        }

        if (NOTIFS_REGEX.matches(trimmed)) {
            return FastRouteResult.Action(AgentAction(type = "notifications"), "Opening notifications")
        }

        if (QUICK_SETTINGS_REGEX.matches(trimmed)) {
            return FastRouteResult.Action(AgentAction(type = "quick_settings"), "Opening quick settings")
        }

        val openUrlMatch = OPEN_URL_REGEX.find(trimmed)
        if (openUrlMatch != null) {
            var rawUrl = openUrlMatch.groupValues[1].trim()
            if (!rawUrl.startsWith("http://") && !rawUrl.startsWith("https://")) {
                rawUrl = "https://$rawUrl"
            }
            return FastRouteResult.Action(
                AgentAction(type = "open_url", url = rawUrl),
                "Opening link"
            )
        }

        val openAppMatch = OPEN_APP_REGEX.find(trimmed)
        if (openAppMatch != null) {
            val appName = openAppMatch.groupValues[1].trim()
            if (appName.isNotEmpty()) {
                return FastRouteResult.Action(
                    AgentAction(type = "open_app", app = appName),
                    "Opening $appName"
                )
            }
        }

        if (SCROLL_DOWN_REGEX.matches(trimmed)) {
            return FastRouteResult.Action(AgentAction(type = "scroll", direction = "down"), "Scrolling down")
        }

        if (SCROLL_UP_REGEX.matches(trimmed)) {
            return FastRouteResult.Action(AgentAction(type = "scroll", direction = "up"), "Scrolling up")
        }

        if (SCROLL_LEFT_REGEX.matches(trimmed)) {
            return FastRouteResult.Action(AgentAction(type = "scroll", direction = "left"), "Scrolling left")
        }

        if (SCROLL_RIGHT_REGEX.matches(trimmed)) {
            return FastRouteResult.Action(AgentAction(type = "scroll", direction = "right"), "Scrolling right")
        }

        return FastRouteResult.PassToLlm(trimmed)
    }
}
