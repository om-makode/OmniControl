package com.omnicontrol.feature.agent

import com.omnicontrol.core.model.AgentAction
import com.omnicontrol.core.model.RectBounds
import com.omnicontrol.core.model.Role
import com.omnicontrol.core.model.ScreenState
import com.omnicontrol.core.model.UiElement
import com.omnicontrol.feature.agent.safety.BrainPrivacy
import com.omnicontrol.feature.agent.safety.Risk
import com.omnicontrol.feature.agent.safety.SafetyPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyPolicyTest {

    private fun el(id: Int, text: String, top: Int = 1000, clickable: Boolean = true, editable: Boolean = false,
                   hint: String? = null, password: Boolean = false, checked: Boolean? = null, inList: Boolean = false, viewId: String? = null) =
        UiElement(id, if (editable) Role.TEXT_FIELD else Role.BUTTON, text = text, hint = hint, viewId = viewId,
            bounds = RectBounds(0, top, 500, top + 120), clickable = clickable, editable = editable,
            isPassword = password, checked = checked, inList = inList)

    private fun screen(pkg: String, vararg els: UiElement) =
        ScreenState(pkg, appLabel = pkg.substringAfterLast('.'), elements = els.toList(), screenWidth = 1080, screenHeight = 2400)

    private val whatsappChat = screen(
        "com.whatsapp",
        el(1, "Rahul", top = 120, clickable = false, viewId = "com.whatsapp:id/conversation_contact_name"),
        el(2, "Hey, I will reach in 20 minutes.", top = 2200, editable = true, hint = "Message"),
        el(3, "Send", top = 2200)
    )

    // ---- commands
    @Test fun blocksDangerousCommands() {
        listOf(
            "pay 500 to Rahul on GPay", "send money to mom", "approve the payment request", "do a UPI transfer",
            "factory reset my phone", "erase all data", "turn off screen lock", "disable play protect",
            "read my OTP", "type my password", "open porn videos"
        ).forEach { assertEquals(it, Risk.BLOCK, SafetyPolicy.checkCommand(it).risk) }
    }

    @Test fun allowsNormalCommands() {
        listOf("open YouTube and play the first video", "search swift tutorials", "scroll down", "open WhatsApp",
            "play despacito", "go back", "turn on wifi", "open settings")
            .forEach { assertEquals(it, Risk.SAFE, SafetyPolicy.checkCommand(it).risk) }
    }

    // ---- confirmations
    @Test fun sendNeedsConfirmationWithRecipientAndMessage() {
        val c = SafetyPolicy.checkAction(AgentAction("click", target = 3), whatsappChat)
        assertEquals(Risk.CONFIRM, c.risk)
        assertTrue(c.request!!.detail.contains("Rahul"))
        assertTrue(c.request!!.detail.contains("20 minutes"))
        assertEquals("Confirm send", c.request!!.confirmLabel)
        assertTrue(c.repeatKey != null)
    }

    @Test fun typingWithSubmitInMessengerNeedsConfirmation() {
        val c = SafetyPolicy.checkAction(AgentAction("type", target = 2, text = "On my way", submit = true), whatsappChat)
        assertEquals(Risk.CONFIRM, c.risk)
    }

    @Test fun callsPurchasesSettingsAndDeletesNeedConfirmation() {
        val dialer = screen("com.google.android.dialer", el(1, "Call"))
        assertEquals(Risk.CONFIRM, SafetyPolicy.checkAction(AgentAction("click", target = 1), dialer).risk)
        val shop = screen("com.flipkart.android", el(1, "Place order"))
        assertEquals(Risk.CONFIRM, SafetyPolicy.checkAction(AgentAction("click", target = 1), shop).risk)
        val settings = screen("com.android.settings", el(1, "Bluetooth", checked = false))
        assertEquals(Risk.CONFIRM, SafetyPolicy.checkAction(AgentAction("click", target = 1), settings).risk)
        val photos = screen("com.google.android.apps.photos", el(1, "Delete"))
        assertEquals(Risk.CONFIRM, SafetyPolicy.checkAction(AgentAction("click", target = 1), photos).risk)
        val yt = screen("com.google.android.youtube", el(1, "Subscribe"))
        assertEquals(Risk.CONFIRM, SafetyPolicy.checkAction(AgentAction("click", target = 1), yt).risk)
        val perm = screen("com.google.android.permissioncontroller", el(1, "Allow Camera to take pictures?", clickable = false), el(2, "While using the app"))
        assertEquals(Risk.CONFIRM, SafetyPolicy.checkAction(AgentAction("click", target = 2), perm).risk)
    }

    @Test fun unknownLinksWarn() {
        assertEquals(Risk.CONFIRM, SafetyPolicy.checkAction(AgentAction("open_url", url = "http://bit.ly/abc"), null).risk)
        assertEquals(Risk.CONFIRM, SafetyPolicy.checkAction(AgentAction("open_url", url = "https://example.com/app.apk"), null).risk)
        assertEquals(Risk.SAFE, SafetyPolicy.checkAction(AgentAction("open_url", url = "https://www.youtube.com/results?search_query=x"), null).risk)
    }

    // ---- blocks
    @Test fun blocksPaymentAndCredentialScreens() {
        val checkout = screen("com.flipkart.android", el(1, "Pay ₹499"), el(2, "Cancel"))
        assertEquals(Risk.BLOCK, SafetyPolicy.checkAction(AgentAction("click", target = 2), checkout).risk)
        val login = screen("com.instagram.android", el(1, "", editable = true, hint = "Password", password = true), el(2, "Log in"))
        assertEquals(Risk.BLOCK, SafetyPolicy.checkAction(AgentAction("type", target = 1, text = "abc"), login).risk)
        val otp = screen("com.amazon", el(1, "", editable = true, hint = "Enter OTP"))
        assertEquals(Risk.BLOCK, SafetyPolicy.checkAction(AgentAction("type", target = 1, text = "123456"), otp).risk)
        // leaving is always allowed
        assertEquals(Risk.SAFE, SafetyPolicy.checkAction(AgentAction("back"), checkout).risk)
    }

    @Test fun blocksBankingAppsAndSecuritySettings() {
        val gpay = screen("com.google.android.apps.nbu.paisa.user", el(1, "Scan QR"))
        assertEquals(Risk.BLOCK, SafetyPolicy.checkAction(AgentAction("click", target = 1), gpay).risk)
        assertEquals(Risk.BLOCK, SafetyPolicy.checkAction(AgentAction("open_app", app = "PhonePe"), null).risk)
        val security = screen("com.android.settings", el(1, "Screen lock"))
        assertEquals(Risk.BLOCK, SafetyPolicy.checkAction(AgentAction("click", target = 1), security).risk)
        val reset = screen("com.android.settings", el(1, "Erase all data (factory reset)"))
        assertEquals(Risk.BLOCK, SafetyPolicy.checkAction(AgentAction("click", target = 1), reset).risk)
    }

    @Test fun videoTitlesDoNotTriggerPaymentBlock() {
        val yt = screen("com.google.android.youtube", el(1, "How to set UPI PIN in 2 minutes", inList = true), el(2, "Search", top = 100))
        assertEquals(Risk.SAFE, SafetyPolicy.checkAction(AgentAction("click", target = 1), yt).risk)
    }

    @Test fun normalTapsAreSafe() {
        val yt = screen("com.google.android.youtube", el(1, "Swift Tutorial for Beginners", inList = true))
        assertEquals(Risk.SAFE, SafetyPolicy.checkAction(AgentAction("click", target = 1), yt).risk)
        assertEquals(Risk.SAFE, SafetyPolicy.checkAction(AgentAction("scroll", direction = "down"), yt).risk)
        assertEquals(Risk.SAFE, SafetyPolicy.checkAction(AgentAction("open_app", app = "YouTube"), yt).risk)
    }

    // ---- privacy
    @Test fun cloudBrainNeverSeesPersonalApps() {
        val d = BrainPrivacy.prepare(whatsappChat, cloud = true, allowPersonalCloud = false, visionWanted = true, brainName = "Gemini")
        assertTrue(d is BrainPrivacy.Decision.Refuse)
        val local = BrainPrivacy.prepare(whatsappChat, cloud = false, allowPersonalCloud = false, visionWanted = true, brainName = "Mac")
        assertTrue(local is BrainPrivacy.Decision.Allow)
        assertFalse((local as BrainPrivacy.Decision.Allow).screenshotAllowed)
    }

    @Test fun cloudBrainGetsRedactedText() {
        val s = screen("com.android.chrome", el(1, "Contact me at john.doe@gmail.com or +91 98765 43210"))
        val d = BrainPrivacy.prepare(s, cloud = true, allowPersonalCloud = false, visionWanted = false, brainName = "Gemini") as BrainPrivacy.Decision.Allow
        val text = d.screen.elements.first().text!!
        assertFalse(text.contains("gmail"))
        assertFalse(text.contains("98765"))
    }
}
