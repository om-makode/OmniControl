package com.omnicontrol.feature.agent

import com.omnicontrol.core.model.AgentAction
import com.omnicontrol.core.model.ScreenState

enum class Risk { SAFE, CONFIRM, BLOCK }

data class SafetyCheck(val risk: Risk, val question: String = "", val reason: String = "")

/**
 * Asks before anything that sends, buys, deletes or publishes, and refuses passwords and payment
 * screens. The user can turn confirmations off in Settings (blocks always stay).
 */
object SafetyGuard {

    private val CONFIRM_WORDS = Regex(
        "\\b(send|pay|payment|buy|purchase|place order|order now|checkout|check out|delete|remove|uninstall|" +
            "transfer|post|publish|share|upload|call|dial|subscribe|unsubscribe|book now|confirm|submit|" +
            "log ?out|sign ?out|erase|reset|format|block|report|unfollow|clear data|clear storage|forward)\\b",
        RegexOption.IGNORE_CASE
    )
    private val BLOCK_WORDS = Regex(
        "\\b(factory reset|erase all data|delete account|close account|enter upi pin|upi pin|cvv)\\b",
        RegexOption.IGNORE_CASE
    )
    private val MESSAGING_APPS = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "com.google.android.apps.messaging",
        "com.samsung.android.messaging", "com.instagram.android", "com.facebook.orca", "com.google.android.gm"
    )

    fun check(action: AgentAction, screen: ScreenState?, confirmEnabled: Boolean): SafetyCheck {
        val el = screen?.element(action.target)
        val label = el?.primaryLabel.orEmpty()

        if (el?.isPassword == true && action.type == "type") {
            return SafetyCheck(Risk.BLOCK, reason = "I won't type passwords. Please type it yourself.")
        }
        if (BLOCK_WORDS.containsMatchIn(label) || (action.type == "type" && BLOCK_WORDS.containsMatchIn(screenText(screen)))) {
            return SafetyCheck(Risk.BLOCK, reason = "That needs you to do it yourself (\"${label.take(40)}\").")
        }
        if (!confirmEnabled) return SafetyCheck(Risk.SAFE)

        val isTap = action.type == "click" || action.type == "tap" || action.type == "long_press"
        if (isTap && label.isNotBlank() && label.length <= 40 && CONFIRM_WORDS.containsMatchIn(label)) {
            return SafetyCheck(Risk.CONFIRM, question = "Should I tap \"${label.take(40)}\"?")
        }
        if (action.type == "type" && action.submit == true && screen?.packageName in MESSAGING_APPS) {
            return SafetyCheck(Risk.CONFIRM, question = "Send \"${action.text?.take(60)}\"?")
        }
        return SafetyCheck(Risk.SAFE)
    }

    private fun screenText(screen: ScreenState?): String =
        screen?.elements?.take(40)?.joinToString(" ") { it.primaryLabel }.orEmpty()
}
