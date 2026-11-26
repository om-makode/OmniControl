package com.omnicontrol.feature.agent.safety

import com.omnicontrol.core.common.safety.PrivacyFilter
import com.omnicontrol.core.model.ScreenState

/**
 * Decides what screen information may be given to the AI brain.
 *
 * Every brain: password/secret fields are emptied.
 * Cloud brains (Gemini, Groq, OpenRouter...):
 *  - personal apps (messages, email, contacts, photos, files, notifications) are not sent at all
 *    unless the user switched "Let cloud AI read personal apps" on;
 *  - banking/payment/password apps are never sent;
 *  - all text is redacted (emails, phone numbers, card/ID numbers, codes);
 *  - screenshots only when enabled and the screen is not personal and has no secret fields.
 */
object BrainPrivacy {

    sealed interface Decision {
        data class Allow(val screen: ScreenState, val screenshotAllowed: Boolean) : Decision
        data class Refuse(val reason: String) : Decision
    }

    fun prepare(screen: ScreenState, cloud: Boolean, allowPersonalCloud: Boolean, visionWanted: Boolean, brainName: String): Decision {
        val cat = SafetyPolicy.category(screen.packageName)
        if (cat == AppCategory.FINANCE || cat == AppCategory.SECURE) {
            return Decision.Refuse("This is a banking, payment or security app. I don't read it or send it anywhere.")
        }
        val personal = cat == AppCategory.PERSONAL
        if (cloud && personal && !allowPersonalCloud) {
            return Decision.Refuse(
                "This screen has personal data (${screen.appLabel.ifBlank { "messages or files" }}), so I won't send it to $brainName. " +
                    "Use quick commands, the \"My computer\" brain, or allow it in Settings > Privacy."
            )
        }
        val hasSecrets = screen.elements.any { it.isPassword || (it.editable && PrivacyFilter.isSecretLabel(it.hint)) }
        val cleaned = screen.copy(
            elements = screen.elements.map { e ->
                when {
                    e.isPassword || (e.editable && PrivacyFilter.isSecretLabel(e.hint)) -> e.copy(text = null)
                    cloud -> e.copy(
                        text = e.text?.let { PrivacyFilter.redact(it) },
                        description = e.description?.let { PrivacyFilter.redact(it) },
                        hint = e.hint?.let { PrivacyFilter.redact(it) }
                    )
                    else -> e
                }
            }
        )
        val screenshot = visionWanted && !hasSecrets && !personal && cat != AppCategory.PERMISSION_DIALOG
        return Decision.Allow(cleaned, screenshot)
    }

    fun clean(text: String, cloud: Boolean): String = if (cloud) PrivacyFilter.redact(text) else text
}
