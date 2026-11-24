package com.omnicontrol.feature.navigator.popup

import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.model.Observation
import com.omnicontrol.core.model.Role
import com.omnicontrol.core.model.UiElement

data class DismissCandidate(
    val element: UiElement,
    val dismissText: String
)

object PopupHandler {
    private const val TAG = "PopupHandler"

    private val DISMISS_KEYWORDS = listOf(
        "not now",
        "skip",
        "skip ad",
        "no thanks",
        "maybe later",
        "close",
        "dismiss",
        "got it"
    )

    fun checkPopup(observation: Observation): DismissCandidate? {
        // Never auto-dismiss or touch OS permission dialogs
        if (observation.packageName.contains("permissioncontroller")) {
            OmniLog.i(TAG, "OS permission controller screen detected, ignoring auto-dismiss")
            return null
        }

        // Check if there are dialog indicators or promo modals
        for (element in observation.elements) {
            val text = element.primaryLabel.trim().lowercase()
            for (kw in DISMISS_KEYWORDS) {
                if (text == kw && (element.clickable || element.role == Role.BUTTON || element.role == Role.TEXT)) {
                    OmniLog.i(TAG, "Detected dismissable popup button: '${element.primaryLabel}' on #${element.id}")
                    return DismissCandidate(element, element.primaryLabel)
                }
            }
        }
        return null
    }
}
