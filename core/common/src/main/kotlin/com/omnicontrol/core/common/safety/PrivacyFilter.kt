package com.omnicontrol.core.common.safety

/**
 * Removes personal and secret data from text before it leaves the phone (cloud AI) or is logged.
 * Redaction is best effort: OmniControl also refuses to send personal/sensitive app screens to a
 * cloud AI at all (see SafetyPolicy), this filter is the second layer.
 */
object PrivacyFilter {

    private val EMAIL = Regex("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}")
    private val UPI = Regex("\\b[A-Za-z0-9._-]{2,}@[A-Za-z]{2,}\\b")
    private val CARD = Regex("\\b(?:\\d[ -]?){13,19}\\b")
    private val PHONE = Regex("(?<!\\w)(?:\\+?\\d{1,3}[ -]?)?(?:\\(?\\d{2,5}\\)?[ -]?)?\\d{3,5}[ -]?\\d{4,5}(?!\\w)")
    private val AADHAAR = Regex("\\b\\d{4}\\s?\\d{4}\\s?\\d{4}\\b")
    private val PAN = Regex("\\b[A-Z]{5}\\d{4}[A-Z]\\b")
    private val IFSC = Regex("\\b[A-Z]{4}0[A-Z0-9]{6}\\b")
    private val OTP_CONTEXT = Regex("(?i)\\b(otp|one[- ]time|verification code|security code|passcode|pin|cvv|code)\\b[^\\n]{0,25}?\\b\\d{3,8}\\b")
    private val LONG_NUMBER = Regex("\\b\\d{6,}\\b")

    /** Replace secrets and personal identifiers with neutral placeholders. */
    fun redact(text: String?): String {
        if (text.isNullOrEmpty()) return text.orEmpty()
        var t = text
        t = OTP_CONTEXT.replace(t) { m -> m.value.replace(Regex("\\d{3,8}"), "[code]") }
        t = EMAIL.replace(t, "[email]")
        t = UPI.replace(t, "[upi-id]")
        t = CARD.replace(t, "[card]")
        t = AADHAAR.replace(t, "[id-number]")
        t = PAN.replace(t, "[id-number]")
        t = IFSC.replace(t, "[bank-code]")
        t = PHONE.replace(t) { m -> if (m.value.count { it.isDigit() } >= 8) "[phone]" else m.value }
        t = LONG_NUMBER.replace(t, "[number]")
        return t
    }

    /** true if the text contains contact details / identifiers worth a privacy warning. */
    fun containsPersonalInfo(text: String?): Boolean {
        if (text.isNullOrBlank()) return false
        return EMAIL.containsMatchIn(text) || UPI.containsMatchIn(text) || CARD.containsMatchIn(text) ||
            AADHAAR.containsMatchIn(text) || PAN.containsMatchIn(text) ||
            PHONE.findAll(text).any { m -> m.value.count { it.isDigit() } >= 8 } ||
            Regex("(?i)\\b(address|flat no|house no|pin ?code)\\b").containsMatchIn(text)
    }

    private val SECRET_LABEL = Regex(
        "(?i)\\b(password|passcode|pass code|pin|mpin|upi pin|otp|one[- ]time|verification code|security code|cvv|cvc|recovery code|backup code|2fa|two[- ]factor|authenticator)\\b"
    )

    /** Does this field label/hint ask for a secret (password, PIN, OTP, CVV...)? */
    fun isSecretLabel(text: String?): Boolean = !text.isNullOrBlank() && SECRET_LABEL.containsMatchIn(text)
}
