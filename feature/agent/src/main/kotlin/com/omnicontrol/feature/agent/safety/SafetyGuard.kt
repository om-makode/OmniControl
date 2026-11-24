package com.omnicontrol.feature.agent.safety

import com.omnicontrol.core.model.AgentAction
import com.omnicontrol.core.model.Observation
import com.omnicontrol.core.model.UiElement

enum class RiskLevel {
    SAFE,
    CONFIRM,
    HANDOFF,
    BLOCKED
}

data class SafetyAssessment(
    val level: RiskLevel,
    val reason: String,
    val confirmQuestion: String? = null
)

object SafetyGuard {

    private val CONFIRM_KEYWORDS = listOf(
        "send", "delete", "remove", "post", "publish", "subscribe",
        "call", "uninstall", "install", "purchase", "pay", "buy",
        "transfer", "confirm", "order", "place order", "checkout"
    )

    private val HANDOFF_PACKAGES = listOf(
        "com.google.android.apps.nbu.paisa.user", // Google Pay
        "net.one97.paytm", // Paytm
        "com.phonepe.app", // PhonePe
        "in.org.npci.upiapp", // BHIM
        "com.axis.mobile", "com.icicibank.mobile", "com.sbi.lotusintouch", "com.hdfcbank.android"
    )

    private val BLOCKED_KEYWORDS = listOf(
        "factory reset", "erase all data", "wipe data", "uninstall omnicontrol",
        "disable accessibility", "disable administrator"
    )

    fun evaluate(action: AgentAction, observation: Observation): SafetyAssessment {
        val targetElement = action.target?.let { id ->
            observation.elements.find { it.id == id }
        }

        // 1. Check for Blocked targets / actions
        if (observation.packageName.contains("com.omnicontrol")) {
            return SafetyAssessment(RiskLevel.BLOCKED, "Actions inside OmniControl system windows are blocked")
        }

        val targetLabel = targetElement?.primaryLabel?.lowercase() ?: ""
        if (BLOCKED_KEYWORDS.any { targetLabel.contains(it) }) {
            return SafetyAssessment(RiskLevel.BLOCKED, "Dangerous system action is blocked: $targetLabel")
        }

        // 2. Check for Handoff (Passwords, Banking, CAPTCHA, Secure Screens)
        if (observation.isSecureWindow) {
            return SafetyAssessment(RiskLevel.HANDOFF, "This screen is protected. Please complete this step yourself.")
        }

        if (targetElement?.isPassword == true) {
            return SafetyAssessment(RiskLevel.HANDOFF, "Password or PIN entry requires manual handoff.")
        }

        if (HANDOFF_PACKAGES.any { observation.packageName.contains(it, ignoreCase = true) }) {
            return SafetyAssessment(RiskLevel.HANDOFF, "Financial or banking application requires manual operation.")
        }

        if (targetLabel.contains("captcha") || targetLabel.contains("verify you are human") || targetLabel.contains("biometric")) {
            return SafetyAssessment(RiskLevel.HANDOFF, "Authentication challenge requires manual handoff.")
        }

        // 3. Check for Confirmation
        if (action.type.equals("click", ignoreCase = true) || action.type.equals("select", ignoreCase = true)) {
            val matchingKeyword = CONFIRM_KEYWORDS.firstOrNull { targetLabel.contains(it) }
            if (matchingKeyword != null) {
                return SafetyAssessment(
                    level = RiskLevel.CONFIRM,
                    reason = "Action targets sensitive button: '$targetLabel'",
                    confirmQuestion = "About to tap \"$targetLabel\". Do you want to proceed?"
                )
            }
        }

        // 4. Default: Safe
        return SafetyAssessment(RiskLevel.SAFE, "Action is safe to execute directly")
    }
}
