package com.omnicontrol.feature.navigator.signature

import com.omnicontrol.core.model.Role
import com.omnicontrol.core.model.UiElement
import kotlinx.serialization.Serializable
import kotlin.math.abs

@Serializable
data class ElementSignature(
    val viewId: String? = null,
    val role: Role,
    val textPattern: String? = null,
    val relX: Float,
    val relY: Float,
    val inListItem: Boolean = false
) {
    fun matches(element: UiElement, screenWidth: Int, screenHeight: Int): Float {
        var score = 0f

        // 1. viewId match (+0.50)
        if (!viewId.isNullOrBlank() && !element.viewId.isNullOrBlank()) {
            if (viewId.equals(element.viewId, ignoreCase = true)) {
                score += 0.50f
            }
        }

        // 2. Role match (+0.15)
        if (role == element.role) {
            score += 0.15f
        }

        // 3. Text pattern match (+0.25)
        val elemTextPattern = normalizeText(element.primaryLabel)
        if (!textPattern.isNullOrBlank() && !elemTextPattern.isNullOrBlank()) {
            if (textPattern == elemTextPattern) {
                score += 0.25f
            } else if (textPattern.contains(elemTextPattern) || elemTextPattern.contains(textPattern)) {
                score += 0.15f
            }
        } else if (textPattern == null && elemTextPattern == null) {
            score += 0.10f
        }

        // 4. Relative position within tolerance (+0.10)
        val elemRelX = if (screenWidth > 0) element.bounds.centerX.toFloat() / screenWidth.toFloat() else 0.5f
        val elemRelY = if (screenHeight > 0) element.bounds.centerY.toFloat() / screenHeight.toFloat() else 0.5f
        if (abs(relX - elemRelX) <= 0.12f && abs(relY - elemRelY) <= 0.12f) {
            score += 0.10f
        }

        return score.coerceIn(0f, 1f)
    }

    companion object {
        fun normalizeText(text: String?): String? {
            if (text.isNullOrBlank()) return null
            return text.trim()
                .lowercase()
                .replace(Regex("[0-9]"), "#")
                .take(40)
        }

        fun from(element: UiElement, screenWidth: Int, screenHeight: Int): ElementSignature {
            val relX = if (screenWidth > 0) element.bounds.centerX.toFloat() / screenWidth.toFloat() else 0.5f
            val relY = if (screenHeight > 0) element.bounds.centerY.toFloat() / screenHeight.toFloat() else 0.5f
            return ElementSignature(
                viewId = element.viewId,
                role = element.role,
                textPattern = normalizeText(element.primaryLabel),
                relX = relX,
                relY = relY,
                inListItem = element.role == Role.LIST_ITEM || element.parentId != null
            )
        }
    }
}
