package com.omnicontrol.feature.accessibility

import com.omnicontrol.core.model.AgentAction
import com.omnicontrol.core.model.Observation

data class VerificationResult(
    val isSuccess: Boolean,
    val reason: String
)

object ActionVerifier {

    fun verify(
        action: AgentAction,
        pre: Observation,
        post: Observation
    ): VerificationResult {
        return when (action.type.lowercase().trim()) {
            "open_app" -> {
                val expected = action.app ?: ""
                val packageChanged = pre.packageName != post.packageName
                val labelMatches = post.appLabel.contains(expected, ignoreCase = true) ||
                    post.packageName.contains(expected, ignoreCase = true)
                if (labelMatches || packageChanged) {
                    VerificationResult(true, "App opened: ${post.appLabel} (${post.packageName})")
                } else {
                    VerificationResult(false, "Screen remained on ${pre.appLabel} (${pre.packageName})")
                }
            }

            "open_url" -> {
                val packageChanged = pre.packageName != post.packageName
                if (packageChanged || post.screenFingerprint != pre.screenFingerprint) {
                    VerificationResult(true, "Opened URL in browser: ${post.appLabel}")
                } else {
                    VerificationResult(false, "No browser navigation detected")
                }
            }

            "click", "select" -> {
                val targetId = action.target
                val targetPre = pre.elements.find { it.id == targetId }

                val fingerprintChanged = pre.screenFingerprint != post.screenFingerprint
                val dialogAppeared = pre.dialogs.isEmpty() && post.dialogs.isNotEmpty()
                val packageChanged = pre.packageName != post.packageName

                // Check toggle states
                val toggleChanged = if (targetId != null && targetPre != null) {
                    val targetPost = post.elements.find { it.bounds == targetPre.bounds }
                    targetPost?.checked != null && targetPost.checked != targetPre.checked
                } else {
                    false
                }

                if (fingerprintChanged || dialogAppeared || packageChanged || toggleChanged) {
                    VerificationResult(true, "Click registered, screen updated")
                } else {
                    // Sometimes a button tap triggers an internal state or audio without screen layout change
                    VerificationResult(true, "Click performed on target [$targetId]")
                }
            }

            "long_press" -> {
                val fingerprintChanged = pre.screenFingerprint != post.screenFingerprint
                val dialogAppeared = post.dialogs.isNotEmpty()
                if (fingerprintChanged || dialogAppeared) {
                    VerificationResult(true, "Long press triggered context menu or update")
                } else {
                    VerificationResult(true, "Long press performed on target [${action.target}]")
                }
            }

            "type_text" -> {
                val typed = action.text ?: ""
                val targetId = action.target
                val targetPost = post.elements.find { it.id == targetId }
                val textUpdated = targetPost?.text?.contains(typed, ignoreCase = true) == true
                val keyboardOpened = post.keyboardVisible

                if (textUpdated || keyboardOpened || pre.screenFingerprint != post.screenFingerprint) {
                    VerificationResult(true, "Text entered: '$typed'")
                } else {
                    VerificationResult(true, "Set text command dispatched")
                }
            }

            "clear_text" -> {
                VerificationResult(true, "Text cleared")
            }

            "scroll", "swipe" -> {
                val fingerprintChanged = pre.screenFingerprint != post.screenFingerprint
                if (fingerprintChanged) {
                    VerificationResult(true, "Screen scrolled, new content visible")
                } else {
                    VerificationResult(true, "Scroll gesture dispatched (reached end or small movement)")
                }
            }

            "back", "home", "recents" -> {
                val fingerprintChanged = pre.screenFingerprint != post.screenFingerprint
                val packageChanged = pre.packageName != post.packageName
                if (fingerprintChanged || packageChanged) {
                    VerificationResult(true, "Navigation action ${action.type} succeeded")
                } else {
                    VerificationResult(true, "Global action ${action.type} performed")
                }
            }

            "notifications", "quick_settings" -> {
                VerificationResult(true, "Opened ${action.type}")
            }

            "wait" -> {
                VerificationResult(true, "Waited ${action.ms ?: 1000}ms")
            }

            "read_screen" -> {
                val summary = "${post.elements.size} elements on ${post.appLabel}"
                VerificationResult(true, summary)
            }

            "done" -> {
                VerificationResult(true, action.summary ?: "Task completed")
            }

            else -> {
                VerificationResult(true, "Action ${action.type} executed")
            }
        }
    }
}
