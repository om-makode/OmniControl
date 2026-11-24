package com.omnicontrol.core.model

import kotlinx.serialization.Serializable

@Serializable
data class ScreenElement(
    val id: Int,
    val text: String = "",
    val contentDescription: String = "",
    val className: String = "",
    val viewId: String = "",
    val bounds: RectBounds = RectBounds(),
    val isClickable: Boolean = false,
    val isScrollable: Boolean = false,
    val isEditable: Boolean = false
)

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
}

@Serializable
enum class Role {
    BUTTON,
    TEXT_FIELD,
    TAB,
    LIST_ITEM,
    IMAGE,
    CHECKBOX,
    SWITCH,
    LINK,
    TEXT,
    CONTAINER,
    OTHER
}

@Serializable
enum class ElementSource {
    TREE,
    OCR,
    VISION
}

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
    val selected: Boolean? = null,
    val enabled: Boolean = true,
    val isPassword: Boolean = false,
    val source: ElementSource = ElementSource.TREE,
    val parentId: Int? = null,
    val containerId: Int? = null
) {
    fun toScreenElement(): ScreenElement {
        return ScreenElement(
            id = id,
            text = text ?: "",
            contentDescription = description ?: "",
            className = className ?: "",
            viewId = viewId ?: "",
            bounds = bounds,
            isClickable = clickable,
            isScrollable = scrollable,
            isEditable = editable
        )
    }

    val primaryLabel: String
        get() = when {
            !text.isNullOrBlank() -> text
            !description.isNullOrBlank() -> description
            !hint.isNullOrBlank() -> hint
            !viewId.isNullOrBlank() -> viewId.substringAfterLast(":id/").substringAfterLast("/")
            else -> role.name.lowercase()
        }
}

@Serializable
data class Observation(
    val packageName: String,
    val activityHint: String? = null,
    val appLabel: String = "",
    val elements: List<UiElement> = emptyList(),
    val screenFingerprint: String = "",
    val keyboardVisible: Boolean = false,
    val focusedElementId: Int? = null,
    val dialogs: List<Int> = emptyList(),
    val isSecureWindow: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toLlmPromptText(maxElements: Int = 40): String {
        val sb = StringBuilder()
        sb.appendLine("APP: $appLabel ($packageName)${if (!activityHint.isNullOrBlank()) "  SCREEN: $activityHint" else ""}")
        sb.appendLine("KEYBOARD: ${if (keyboardVisible) "visible" else "hidden"}   FOCUSED: ${focusedElementId?.let { "[$it]" } ?: "none"}")

        val shownElements = elements.take(maxElements)
        for (el in shownElements) {
            val roleStr = el.role.name.lowercase()
            val labelStr = el.primaryLabel
            val scrollSuffix = if (el.scrollable) " (scrollable)" else ""
            val checkSuffix = when (el.checked) {
                true -> " [checked]"
                false -> " [unchecked]"
                null -> ""
            }
            sb.appendLine("[${el.id}] $roleStr \"$labelStr\"$scrollSuffix$checkSuffix")
        }

        if (elements.size > maxElements) {
            sb.appendLine("... ${elements.size - maxElements} more elements below")
        }
        return sb.toString().trimEnd()
    }
}

@Serializable
data class AgentAction(
    val type: String,
    val target: Int? = null,
    val app: String? = null,
    val url: String? = null,
    val text: String? = null,
    val submit: Boolean? = null,
    val direction: String? = null,
    val amount: Float? = null,
    val fromX: Float? = null,
    val fromY: Float? = null,
    val toX: Float? = null,
    val toY: Float? = null,
    val durationMs: Long? = null,
    val ms: Long? = null,
    val question: String? = null,
    val summary: String? = null
)

@Serializable
data class ActionResult(
    val success: Boolean,
    val reason: String = "",
    val durationMs: Long = 0,
    val newObservation: Observation? = null
)
