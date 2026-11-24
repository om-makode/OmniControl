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
