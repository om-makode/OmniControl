package com.omnicontrol.core.model

import kotlinx.serialization.Serializable

@Serializable
data class RectBounds(
    val left: Int = 0,
    val top: Int = 0,
    val right: Int = 0,
    val bottom: Int = 0
) {
    val centerX: Int get() = (left + right) / 2
    val centerY: Int get() = (top + bottom) / 2
    val width: Int get() = right - left
    val height: Int get() = bottom - top
    val area: Long get() = width.toLong() * height.toLong()

    fun contains(other: RectBounds): Boolean =
        other.left >= left && other.top >= top && other.right <= right && other.bottom <= bottom
}

@Serializable
enum class Role { BUTTON, TEXT_FIELD, TAB, LIST_ITEM, IMAGE, CHECKBOX, SWITCH, LINK, TEXT, CONTAINER, OTHER }

/** One meaningful thing on screen. `id` is only valid for the [ScreenState] it came from. */
@Serializable
data class UiElement(
    val id: Int,
    val role: Role,
    val text: String? = null,
    val description: String? = null,
    val hint: String? = null,
    val viewId: String? = null,
    val className: String? = null,
    val bounds: RectBounds = RectBounds(),
    val clickable: Boolean = false,
    val longClickable: Boolean = false,
    val scrollable: Boolean = false,
    val editable: Boolean = false,
    val checked: Boolean? = null,
    val selected: Boolean = false,
    val enabled: Boolean = true,
    val focused: Boolean = false,
    val isPassword: Boolean = false,
    /** true when the element sits inside a scrollable list (feed items, search results, chats...). */
    val inList: Boolean = false
) {
    val primaryLabel: String
        get() = when {
            !text.isNullOrBlank() -> text
            !description.isNullOrBlank() -> description
            !hint.isNullOrBlank() -> hint
            !viewId.isNullOrBlank() -> viewId.substringAfterLast("/").replace('_', ' ')
            else -> ""
        }

    /** Everything we know about the element as one lowercase string, for matching. */
    val searchText: String
        get() = listOfNotNull(text, description, hint, viewId?.substringAfterLast("/")?.replace('_', ' '))
            .joinToString(" ").lowercase()

    val actionable: Boolean get() = clickable || longClickable || editable || scrollable || checked != null
}

@Serializable
data class ScreenState(
    val packageName: String,
    val appLabel: String = "",
    val elements: List<UiElement> = emptyList(),
    val fingerprint: String = "",
    val keyboardVisible: Boolean = false,
    val screenWidth: Int = 0,
    val screenHeight: Int = 0,
    val isSecure: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun element(id: Int?): UiElement? = if (id == null) null else elements.firstOrNull { it.id == id }

    /**
     * Compact text version of the screen for the AI. Every element is one line:
     * `[12] button "Search" @(540,120)` plus flags.
     */
    fun toPromptText(maxElements: Int = 80): String {
        val sb = StringBuilder()
        sb.appendLine("APP: ${appLabel.ifBlank { packageName }} ($packageName)")
        sb.appendLine("SCREEN: ${screenWidth}x$screenHeight  KEYBOARD: ${if (keyboardVisible) "open" else "closed"}")
        val shown = elements.take(maxElements)
        for (el in shown) {
            val label = el.primaryLabel.replace('\n', ' ').replace("\"", "'").take(90)
            val flags = buildList {
                if (el.editable) add("editable")
                if (el.scrollable) add("scrollable")
                if (el.checked == true) add("on")
                if (el.checked == false) add("off")
                if (el.selected) add("selected")
                if (el.focused) add("focused")
                if (!el.enabled) add("disabled")
                if (el.isPassword) add("password")
                if (el.inList) add("list-item")
            }
            val flagText = if (flags.isEmpty()) "" else " {" + flags.joinToString(",") + "}"
            sb.appendLine("[${el.id}] ${el.role.name.lowercase()} \"$label\" @(${el.bounds.centerX},${el.bounds.centerY})$flagText")
        }
        if (elements.size > maxElements) sb.appendLine("... ${elements.size - maxElements} more (scroll to see)")
        return sb.toString().trimEnd()
    }
}

/**
 * The single action format used everywhere (quick commands, AI brain, typed commands).
 * Flat on purpose: small models handle flat JSON far better than nested JSON.
 */
@Serializable
data class AgentAction(
    val type: String,
    val target: Int? = null,
    val text: String? = null,
    val app: String? = null,
    val url: String? = null,
    val direction: String? = null,
    val x: Int? = null,
    val y: Int? = null,
    val ms: Long? = null,
    val submit: Boolean? = null
) {
    fun describe(screen: ScreenState?): String {
        val label = screen?.element(target)?.primaryLabel?.take(40)?.let { "“$it”" }
        return when (type) {
            "open_app" -> "Opening ${app ?: text}"
            "open_url" -> "Opening link"
            "click", "tap" -> "Tapping ${label ?: if (x != null) "the screen" else "#$target"}"
            "long_press" -> "Long-pressing ${label ?: "#$target"}"
            "type" -> "Typing “${text?.take(40)}”"
            "clear" -> "Clearing ${label ?: "the field"}"
            "scroll" -> "Scrolling ${direction ?: "down"}"
            "swipe" -> "Swiping ${direction ?: ""}".trim()
            "back" -> "Going back"
            "home" -> "Going home"
            "recents" -> "Opening recent apps"
            "notifications" -> "Opening notifications"
            "quick_settings" -> "Opening quick settings"
            "wait" -> "Waiting for the screen"
            "enter" -> "Pressing enter"
            else -> type
        }
    }
}

@Serializable
data class ActionResult(
    val success: Boolean,
    val reason: String = "",
    val durationMs: Long = 0,
    val screenChanged: Boolean = false
)
