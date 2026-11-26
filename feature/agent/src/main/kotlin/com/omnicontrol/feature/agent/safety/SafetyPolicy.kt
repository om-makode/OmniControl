package com.omnicontrol.feature.agent.safety

import com.omnicontrol.core.common.safety.ContentFilter
import com.omnicontrol.core.common.safety.LinkSafety
import com.omnicontrol.core.common.safety.PrivacyFilter
import com.omnicontrol.core.model.AgentAction
import com.omnicontrol.core.model.ConfirmRequest
import com.omnicontrol.core.model.ScreenState
import com.omnicontrol.core.model.UiElement

enum class Risk { SAFE, CONFIRM, BLOCK }

data class SafetyCheck(
    val risk: Risk,
    val request: ConfirmRequest? = null,
    /** Message for the user when blocked. */
    val reason: String = "",
    /** Identifies a send/call/purchase/post so repeats can be detected. */
    val repeatKey: String? = null
) {
    companion object {
        val SAFE = SafetyCheck(Risk.SAFE)
        fun block(reason: String) = SafetyCheck(Risk.BLOCK, reason = reason)
    }
}

enum class AppCategory { FINANCE, SECURE, PERSONAL, SETTINGS, PERMISSION_DIALOG, OTHER }

/**
 * The rules OmniControl follows no matter what the AI says.
 *
 *  BLOCK   - payments, banking apps, passwords/PINs/OTPs, security settings, factory reset,
 *            CAPTCHA/biometric checks, adult content. Control is handed back to the user.
 *  CONFIRM - calls, messages, email, posts/likes/follows, purchases, contacts, files, settings,
 *            permissions, installs, location, camera, unknown links, deletes. Shown as a
 *            "Proposed action" card; every single action needs its own approval.
 *  SAFE    - everything else (open apps, search, scroll, read, navigate).
 *
 * The AI only *proposes* actions; this class decides. The AI can never approve a confirmation.
 */
object SafetyPolicy {

    // ------------------------------------------------------------------------- app categories

    private val FINANCE_PACKAGES = setOf(
        "com.google.android.apps.nbu.paisa.user", "com.phonepe.app", "net.one97.paytm", "in.org.npci.upiapp",
        "com.dreamplug.androidapp", "com.mobikwik_new", "com.freecharge.android", "com.paypal.android.p2pmobile",
        "com.google.android.apps.walletnfcrel", "com.samsung.android.spay", "com.sbi.lotusintouch", "com.sbi.upi",
        "com.snapwork.hdfc", "com.csam.icici.bank.imobile", "com.axis.mobile", "com.msf.kbank.mobile",
        "com.canarabank.mobility", "com.pnb.pnbone", "com.bankofbaroda.mconnect", "com.idfcfirstbank.optimus",
        "com.indusind.indie", "com.fedmobile", "com.amazon.mshop.android.shopping.pay", "com.whatsapp.payments",
        "com.zerodha.kite3", "com.nextbillion.groww", "com.upstox.pro", "com.angelbroking.angelone"
    )
    private val FINANCE_WORDS = listOf(
        "bank", "upi", "wallet", "paytm", "phonepe", "paisa", "mobikwik", "paypal", "freecharge", "bhim",
        "yono", "imobile", "netbanking", "creditcard", "loan", "trading", "groww", "zerodha"
    )
    private val SECURE_WORDS = listOf(
        "authenticator", "authy", "bitwarden", "lastpass", "onepassword", "agilebits", "dashlane", "keeper",
        "keepass", "passwordmanager", "vault", "digilocker", "aadhaar", "uidai", "securitycenter", "lool"
    )
    private val PERSONAL_PACKAGES = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.facebook.orca",
        "com.instagram.android", "com.snapchat.android", "com.discord", "com.Slack", "com.microsoft.teams",
        "com.google.android.gm", "com.microsoft.office.outlook", "com.samsung.android.email.provider",
        "com.yahoo.mobile.client.android.mail", "com.google.android.contacts", "com.android.contacts",
        "com.samsung.android.app.contacts", "com.google.android.dialer", "com.samsung.android.dialer",
        "com.android.dialer", "com.google.android.apps.photos", "com.sec.android.gallery3d", "com.miui.gallery",
        "com.google.android.apps.nbu.files", "com.google.android.documentsui", "com.sec.android.app.myfiles",
        "com.google.android.calendar", "com.samsung.android.calendar", "com.google.android.keep",
        "com.samsung.android.app.notes", "com.android.systemui", "com.truecaller"
    )
    private val MESSAGING_PACKAGES = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.thoughtcrime.securesms",
        "com.google.android.apps.messaging", "com.samsung.android.messaging", "com.facebook.orca",
        "com.instagram.android", "com.snapchat.android", "com.discord", "com.Slack", "com.microsoft.teams"
    )
    private val EMAIL_PACKAGES = setOf(
        "com.google.android.gm", "com.microsoft.office.outlook", "com.samsung.android.email.provider",
        "com.yahoo.mobile.client.android.mail"
    )
    private val CONTACT_PACKAGES = setOf(
        "com.google.android.contacts", "com.android.contacts", "com.samsung.android.app.contacts",
        "com.google.android.dialer", "com.samsung.android.dialer", "com.android.dialer", "com.truecaller"
    )
    private val PERMISSION_PACKAGES = setOf(
        "com.google.android.permissioncontroller", "com.android.permissioncontroller",
        "com.android.packageinstaller", "com.google.android.packageinstaller"
    )

    fun category(pkg: String?): AppCategory {
        val p = pkg.orEmpty().lowercase()
        if (p.isEmpty()) return AppCategory.OTHER
        val flat = p.replace(".", "").replace("_", "")
        return when {
            pkg in FINANCE_PACKAGES || FINANCE_WORDS.any { flat.contains(it) } -> AppCategory.FINANCE
            SECURE_WORDS.any { flat.contains(it) } -> AppCategory.SECURE
            pkg in PERMISSION_PACKAGES -> AppCategory.PERMISSION_DIALOG
            pkg in PERSONAL_PACKAGES -> AppCategory.PERSONAL
            p == "com.android.settings" || p.endsWith(".settings") -> AppCategory.SETTINGS
            else -> AppCategory.OTHER
        }
    }

    /** Apps whose screens must stay on the phone (never sent to a cloud AI by default). */
    fun isPersonalOrSensitive(pkg: String?): Boolean =
        category(pkg).let { it == AppCategory.PERSONAL || it == AppCategory.FINANCE || it == AppCategory.SECURE }

    /** App names that are clearly banking / payment / password apps. */
    fun isSensitiveAppName(name: String?): Boolean {
        val n = name.orEmpty().lowercase().replace(" ", "")
        return listOf("bank", "upi", "paytm", "phonepe", "gpay", "googlepay", "bhim", "wallet", "paypal", "cred",
            "mobikwik", "yono", "netbanking", "authenticator", "password", "bitwarden", "lastpass", "1password",
            "digilocker", "aadhaar").any { n.contains(it) }
    }

    // ------------------------------------------------------------------------- commands

    private val PAY_INTENT = Regex(
        "\\b(pay|payment|payments|send money|transfer (money|funds|rs|inr|₹|\\d)|money transfer|upi|recharge|" +
            "bank transfer|neft|imps|rtgs|collect request|approve (the )?(payment|request|transaction)|withdraw|" +
            "add money|buy (crypto|stocks?|shares))\\b", RegexOption.IGNORE_CASE
    )
    private val CREDENTIAL_INTENT = Regex(
        "\\b(password|passcode|pin|mpin|otp|one time password|cvv|recovery code|backup code|verification code|2fa code)\\b",
        RegexOption.IGNORE_CASE
    )
    private val SECURITY_INTENT = Regex(
        "\\b(disable|turn off|switch off|remove|bypass|deactivate|uninstall)\\b.{0,40}\\b(screen lock|lock screen|" +
            "play protect|antivirus|anti virus|security|fingerprint|face unlock|biometric|find my device|captcha|" +
            "verification|device admin|encryption|omnicontrol)\\b|\\bbypass\\b",
        RegexOption.IGNORE_CASE
    )
    private val DESTRUCTION_INTENT = Regex(
        "\\b(factory reset|hard reset|erase (all|everything|my phone|the phone|device|data)|wipe (all|my|the)? ?(data|phone|device)|" +
            "format (my |the )?(phone|device|storage)|reset (my |the )?(phone|device))\\b",
        RegexOption.IGNORE_CASE
    )

    /** Checked on the user's words before anything runs. */
    fun checkCommand(command: String): SafetyCheck {
        if (ContentFilter.containsExplicit(command)) {
            return SafetyCheck.block("That sounded like adult or offensive content, so I didn't do anything. If I misheard, please say it again differently.")
        }
        if (DESTRUCTION_INTENT.containsMatchIn(command)) return SafetyCheck.block("I never reset, erase or wipe the phone. Please do that yourself in Settings.")
        if (PAY_INTENT.containsMatchIn(command)) return SafetyCheck.block("I don't handle payments, UPI or bank transfers. Please do that yourself.")
        if (SECURITY_INTENT.containsMatchIn(command)) return SafetyCheck.block("I don't change or bypass security protections. Please do that yourself.")
        if (CREDENTIAL_INTENT.containsMatchIn(command)) return SafetyCheck.block("I never read, type or share passwords, PINs or OTPs. Please enter them yourself.")
        return SafetyCheck.SAFE
    }

    // ------------------------------------------------------------------------- screens

    private val PAYMENT_UI = Regex(
        "\\b(upi pin|enter (your )?(upi )?pin|enter mpin|cvv|cvc|card number|expiry date|proceed to pay|pay now|pay ₹|pay rs|" +
            "pay inr|make payment|complete payment|payment method|payment options|net ?banking|collect request|" +
            "approve request|authori[sz]e (payment|transaction)|confirm payment|enter amount|add money|send money|" +
            "transfer money|bank account|cash on delivery|emi options)\\b",
        RegexOption.IGNORE_CASE
    )
    private val AUTH_CHALLENGE = Regex(
        "\\b(captcha|i'?m not a robot|verify (it'?s|that it'?s) you|confirm (it'?s|that it'?s) you|use (your )?fingerprint|" +
            "touch the fingerprint sensor|face unlock|enter (your )?(screen lock|device) (pin|password|pattern)|" +
            "draw (your )?pattern|two[- ]step verification|2-step verification|enter the code we sent)\\b",
        RegexOption.IGNORE_CASE
    )

    /** Labels of buttons and input fields (not feed content like video titles). */
    private fun controlLabels(screen: ScreenState): List<String> = screen.elements
        .filter { !it.inList && (it.clickable || it.editable) }
        .flatMap { listOfNotNull(it.text, it.description, it.hint) }
        .filter { it.length <= 60 }

    /** Short non-feed texts (titles of dialogs/pages) plus control labels. */
    private fun pageLabels(screen: ScreenState): List<String> = screen.elements
        .filter { !it.inList && it.primaryLabel.length <= 60 }
        .flatMap { listOfNotNull(it.text, it.description, it.hint) }

    /**
     * Returns a reason when this screen must be handed back to the user (OmniControl will not
     * touch it), or null when it is fine.
     */
    fun screenHandBack(screen: ScreenState): String? {
        when (category(screen.packageName)) {
            AppCategory.FINANCE -> return "This is a banking or payment app. I'm handing control back to you."
            AppCategory.SECURE -> return "This is a password, security or identity app. I'm handing control back to you."
            else -> Unit
        }
        if (screen.isSecure) return "This screen is protected. Please continue yourself."
        if (controlLabels(screen).any { PAYMENT_UI.containsMatchIn(it) }) return "This is a payment screen. I never make payments. Please continue yourself."
        if (pageLabels(screen).any { AUTH_CHALLENGE.containsMatchIn(it) }) return "This screen asks you to prove it's you (CAPTCHA, fingerprint or code). Please do it yourself."
        if (screen.elements.any { it.editable && (it.isPassword || PrivacyFilter.isSecretLabel(it.hint) || (PrivacyFilter.isSecretLabel(it.text) && (it.text?.length ?: 0) < 40)) }) {
            return "This screen asks for a password, PIN or OTP. Please type it yourself."
        }
        return null
    }

    // ------------------------------------------------------------------------- actions

    private val BLOCK_LABEL = Regex(
        "\\b(factory reset|factory data reset|erase all|erase everything|reset phone|wipe|pay|pay now|proceed to pay|" +
            "confirm payment|approve|authori[sz]e|add money|send money|transfer)\\b",
        RegexOption.IGNORE_CASE
    )
    private val SECURITY_LABEL = Regex(
        "\\b(screen lock|lock screen|play protect|scan apps|device admin|device administrators|unknown sources|" +
            "install unknown apps|find my device|fingerprint|face unlock|biometric|encryption|smart lock|" +
            "sim lock|accessibility|developer options|usb debugging|reset options|security update)\\b",
        RegexOption.IGNORE_CASE
    )
    private val CALL_LABEL = Regex("\\b(call|voice call|video call|dial|redial|call back)\\b", RegexOption.IGNORE_CASE)
    private val SEND_LABEL = Regex("\\b(send|send message|send sms|reply|reply all|forward|share|send photo|send file)\\b", RegexOption.IGNORE_CASE)
    private val SOCIAL_LABEL = Regex("\\b(post|publish|comment|like|unlike|subscribe|unsubscribe|follow|unfollow|repost|retweet|tweet|upload|react|join|go live)\\b", RegexOption.IGNORE_CASE)
    private val PURCHASE_LABEL = Regex("\\b(buy|buy now|place order|order now|checkout|check out|book now|book tickets?|confirm booking|purchase|start (free )?trial|subscribe now|get premium|upgrade|add to cart)\\b", RegexOption.IGNORE_CASE)
    private val APP_LABEL = Regex("\\b(install|uninstall|update|disable|force stop|clear data|clear storage|clear cache)\\b", RegexOption.IGNORE_CASE)
    private val DATA_LABEL = Regex("\\b(delete|remove|clear|reset|archive|block|report|discard|erase|trash|move to bin|log ?out|sign ?out|save|edit|rename|move)\\b", RegexOption.IGNORE_CASE)
    private val LOCATION_LABEL = Regex("\\b(share (live )?location|live location|send (my )?location|current location|share my location)\\b", RegexOption.IGNORE_CASE)
    private val CAMERA_LABEL = Regex("\\b(shutter|take (a )?photo|take picture|capture|record|start recording|record video|voice note)\\b", RegexOption.IGNORE_CASE)
    private val PERMISSION_ALLOW = Regex("\\b(allow|while using the app|only this time|allow all the time|allow access|grant)\\b", RegexOption.IGNORE_CASE)
    private val OPEN_WIFI = Regex("\\b(open network|not secure|unsecured|unsecure|no password)\\b", RegexOption.IGNORE_CASE)

    private fun label(el: UiElement?): String =
        listOfNotNull(el?.text, el?.description).joinToString(" ").take(80)

    /**
     * Decide what to do with one proposed action on the current screen.
     * @param appPackageForOpen package that open_app would launch (resolved by the caller), if known.
     */
    fun checkAction(action: AgentAction, screen: ScreenState?, appPackageForOpen: String? = null): SafetyCheck {
        val type = action.type.lowercase()
        // Leaving a screen is always allowed (that's how control is handed back).
        if (type in setOf("back", "home", "wait")) return SafetyCheck.SAFE

        // ---- whole-screen hand back
        if (screen != null && type !in setOf("open_app", "open_url", "recents")) {
            screenHandBack(screen)?.let { return SafetyCheck.block(it) }
        }

        val pkg = screen?.packageName.orEmpty()
        val cat = category(pkg)
        val app = screen?.appLabel?.ifBlank { pkg }.orEmpty()

        when (type) {
            "open_app" -> {
                val name = action.app ?: action.text
                if (isSensitiveAppName(name) || category(appPackageForOpen).let { it == AppCategory.FINANCE || it == AppCategory.SECURE }) {
                    return SafetyCheck.block("I don't open banking, payment or password apps. Please open it yourself.")
                }
                if (ContentFilter.containsExplicit(name)) return SafetyCheck.block("I won't open that.")
                return SafetyCheck.SAFE
            }
            "open_url" -> {
                val url = (action.url ?: action.text).orEmpty()
                if (ContentFilter.isExplicitUrl(url)) return SafetyCheck.block("That link looks like adult content, so I won't open it.")
                if (Regex("(?i)(upi:|paytm|phonepe|pay\\.google|/pay(ment)?[/?]|checkout)").containsMatchIn(url)) {
                    return SafetyCheck.block("That looks like a payment link. Please open it yourself.")
                }
                val warnings = LinkSafety.warnings(url)
                if (warnings.isNotEmpty() && !LinkSafety.isTrusted(url)) {
                    return confirm(
                        title = if (LinkSafety.isDownload(url)) "Download a file" else "Open an unfamiliar link",
                        detail = url,
                        label = "Open link",
                        warning = "Careful: " + warnings.joinToString("; ") + ".",
                        key = "url:$url"
                    )
                }
                return SafetyCheck.SAFE
            }
            "type", "clear" -> {
                val target = screen?.element(action.target) ?: screen?.elements?.firstOrNull { it.editable && it.focused }
                if (target != null && (target.isPassword || PrivacyFilter.isSecretLabel(target.hint) || PrivacyFilter.isSecretLabel(label(target)))) {
                    return SafetyCheck.block("I never type passwords, PINs or OTPs. Please type it yourself.")
                }
                val text = action.text.orEmpty()
                if (ContentFilter.containsExplicit(text)) return SafetyCheck.block("I won't type or search that. If I misheard, please say it again.")
                if (type == "type" && action.submit == true && (pkg in MESSAGING_PACKAGES || pkg in EMAIL_PACKAGES)) {
                    val to = chatTitle(screen)
                    return confirm(
                        title = if (pkg in EMAIL_PACKAGES) "Send an email" else "Send a message in $app",
                        detail = (if (to != null) "To $to:\n" else "") + text,
                        label = "Confirm send",
                        warning = if (PrivacyFilter.containsPersonalInfo(text)) "This message contains personal details." else null,
                        key = "send:$pkg:$to:$text"
                    )
                }
                return SafetyCheck.SAFE
            }
            "media_play", "media_pause", "scroll", "swipe", "recents", "notifications", "enter" -> {
                if (type == "enter" && (pkg in MESSAGING_PACKAGES || pkg in EMAIL_PACKAGES)) {
                    val to = chatTitle(screen)
                    val msg = composeText(screen).orEmpty()
                    return confirm("Send a message in $app", (if (to != null) "To $to:\n" else "") + msg, "Confirm send", key = "send:$pkg:$to:$msg")
                }
                return SafetyCheck.SAFE
            }
            "quick_settings" -> return SafetyCheck.SAFE
        }

        // ---- taps (click / tap / long_press)
        val el = screen?.element(action.target)
        val lbl = label(el)
        val short = lbl.length <= 50
        val where = if (app.isNotBlank()) " in $app" else ""

        if (el == null && action.x != null) {
            // Tapping by coordinates: we can't read what it is, so be careful in risky places.
            return if (cat == AppCategory.OTHER) SafetyCheck.SAFE
            else confirm("Tap an unlabeled spot$where", "At (${action.x}, ${action.y}). I can't read what this button does.", "Tap it", key = null)
        }
        if (el != null && el.isPassword) return SafetyCheck.block("I never touch password fields.")
        if (short && BLOCK_LABEL.containsMatchIn(lbl) && !Regex("(?i)\\b(spotify|wipe screen)\\b").containsMatchIn(lbl)) {
            return SafetyCheck.block("\"$lbl\" is a payment, approval or erase action. Please do it yourself.")
        }
        if (cat == AppCategory.SETTINGS && SECURITY_LABEL.containsMatchIn(lbl)) {
            return SafetyCheck.block("\"$lbl\" is a security setting. Please change it yourself.")
        }
        if (Regex("(?i)play protect").containsMatchIn(lbl)) return SafetyCheck.block("I don't change Play Protect.")

        if (cat == AppCategory.PERMISSION_DIALOG && PERMISSION_ALLOW.containsMatchIn(lbl)) {
            val ask = screen?.elements?.firstOrNull { !it.clickable && it.primaryLabel.length in 10..160 }?.primaryLabel
            return confirm("Grant a permission", ask ?: "An app is asking for a permission.", "Allow",
                warning = "Only allow permissions the app really needs.", key = null)
        }
        if (short && LOCATION_LABEL.containsMatchIn(lbl)) {
            return confirm("Share your location$where", lbl, "Share location", warning = "This shares where you are.", key = "loc:$pkg:${chatTitle(screen)}")
        }
        if (short && CALL_LABEL.containsMatchIn(lbl)) {
            val who = chatTitle(screen) ?: lbl
            return confirm("Make a phone call", "Call $who", "Confirm call", key = "call:$who")
        }
        if (short && SEND_LABEL.containsMatchIn(lbl)) {
            val to = chatTitle(screen)
            val msg = composeText(screen)
            return confirm(
                title = when {
                    pkg in EMAIL_PACKAGES -> "Email: ${lbl.lowercase()}"
                    lbl.contains("share", true) -> "Share$where"
                    lbl.contains("forward", true) -> "Forward$where"
                    else -> "Send a message$where"
                },
                detail = buildString {
                    if (to != null) append("To $to")
                    if (!msg.isNullOrBlank()) { if (isNotEmpty()) append(":\n"); append(msg) }
                    if (isEmpty()) append("Tap \"$lbl\"")
                },
                label = if (lbl.contains("share", true)) "Confirm share" else "Confirm send",
                warning = if (PrivacyFilter.containsPersonalInfo(msg)) "This contains personal details." else null,
                key = "send:$pkg:$to:$msg"
            )
        }
        if (short && PURCHASE_LABEL.containsMatchIn(lbl)) {
            return confirm("Purchase / booking step$where", "Tap \"$lbl\"", "Continue",
                warning = "I will stop before any payment. You complete payments yourself.", key = "buy:$pkg:$lbl")
        }
        if (short && SOCIAL_LABEL.containsMatchIn(lbl)) {
            return confirm("Public action$where", "Tap \"$lbl\"", "Confirm", key = "social:$pkg:$lbl:${chatTitle(screen)}")
        }
        if (short && APP_LABEL.containsMatchIn(lbl)) {
            return confirm("Change an app", "Tap \"$lbl\"$where", "Confirm", key = "app:$pkg:$lbl")
        }
        if (short && CAMERA_LABEL.containsMatchIn(lbl)) {
            return confirm("Use the camera or microphone$where", "Tap \"$lbl\"", "Confirm", warning = "This records a photo, video or audio.", key = null)
        }
        if (cat == AppCategory.SETTINGS && (el?.checked != null || Regex("(?i)\\b(allow|deny|turn on|turn off|enable|disable|permission|connect)\\b").containsMatchIn(lbl))) {
            val state = el?.checked?.let { if (it) "turn OFF" else "turn ON" } ?: "change"
            return confirm("Change a setting", "${lbl.ifBlank { "This setting" }}: $state",
                "Confirm", warning = if (OPEN_WIFI.containsMatchIn(lbl)) "This looks like an open Wi-Fi network. Others may see your traffic." else null, key = null)
        }
        if (cat == AppCategory.SETTINGS && OPEN_WIFI.containsMatchIn(lbl)) {
            return confirm("Connect to Wi-Fi", lbl, "Connect", warning = "This network is not secured. Others may see your traffic.", key = null)
        }
        if (pkg == "com.android.systemui" && el?.checked != null) {
            return confirm("Change a quick setting", "${lbl.ifBlank { "Tile" }}: ${if (el.checked == true) "turn OFF" else "turn ON"}", "Confirm", key = null)
        }
        if (short && DATA_LABEL.containsMatchIn(lbl) && (cat != AppCategory.OTHER || Regex("(?i)\\b(delete|remove|erase|trash|log ?out|sign ?out|block|report)\\b").containsMatchIn(lbl))) {
            return confirm(
                title = when {
                    pkg in CONTACT_PACKAGES -> "Change a contact"
                    category(pkg) == AppCategory.PERSONAL -> "Change or delete your data$where"
                    else -> "Change or delete something$where"
                },
                detail = "Tap \"$lbl\"", label = "Confirm", key = "data:$pkg:$lbl"
            )
        }
        return SafetyCheck.SAFE
    }

    private fun confirm(title: String, detail: String, label: String, warning: String? = null, key: String?): SafetyCheck =
        SafetyCheck(
            Risk.CONFIRM,
            ConfirmRequest(title = title, detail = detail.take(400), confirmLabel = label, warning = warning,
                spoken = title + if (detail.length in 1..90 && !PrivacyFilter.containsPersonalInfo(detail)) ". $detail" else ""),
            repeatKey = key
        )

    // ------------------------------------------------------------------------- screen helpers

    /** Name of the person/chat in the top bar of a messaging, email or dialer screen. */
    fun chatTitle(screen: ScreenState?): String? {
        if (screen == null) return null
        val top = screen.screenHeight * 0.16f
        val skip = Regex("(?i)^(back|navigate up|more options|search|menu|video call|voice call|call|camera|\\d+)$")
        val candidates = screen.elements.filter { it.bounds.centerY < top && !it.editable }
        val byId = candidates.firstOrNull {
            it.viewId?.contains(Regex("(?i)(contact_name|conversation_contact|toolbar_title|chat_title|title|name)")) == true &&
                it.primaryLabel.length in 2..40 && !skip.matches(it.primaryLabel)
        }
        return (byId ?: candidates.firstOrNull { (it.text?.length ?: 0) in 2..40 && !skip.matches(it.text!!) })?.primaryLabel?.take(40)
    }

    /** Text currently typed into the compose box. */
    fun composeText(screen: ScreenState?): String? =
        screen?.elements?.firstOrNull { it.editable && !it.text.isNullOrBlank() && it.text != it.hint }?.text?.take(300)
}
