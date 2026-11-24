package com.omnicontrol.feature.navigator

import com.omnicontrol.core.model.RectBounds
import com.omnicontrol.core.model.Role
import com.omnicontrol.core.model.UiElement
import com.omnicontrol.feature.navigator.signature.ElementSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ElementSignatureTest {

    @Test
    fun testNormalizeText() {
        assertEquals("orders", ElementSignature.normalizeText("Orders"))
        assertEquals("order ####", ElementSignature.normalizeText("Order 1234"))
        assertEquals(null, ElementSignature.normalizeText("   "))
    }

    @Test
    fun testSignatureMatching() {
        val element = UiElement(
            id = 5,
            role = Role.BUTTON,
            text = "Your Orders",
            viewId = "com.example.app:id/btn_orders",
            bounds = RectBounds(left = 100, top = 200, right = 300, bottom = 260),
            clickable = true
        )

        val sig = ElementSignature.from(element, screenWidth = 1080, screenHeight = 2400)

        // Same element should match with high confidence (>= 0.8)
        val score = sig.matches(element, screenWidth = 1080, screenHeight = 2400)
        assertTrue("Expected score >= 0.8 but was $score", score >= 0.8f)

        // Different role and text should score low (< 0.5)
        val otherElement = UiElement(
            id = 12,
            role = Role.SWITCH,
            text = "Dark Mode",
            bounds = RectBounds(left = 700, top = 1800, right = 900, bottom = 1860)
        )
        val otherScore = sig.matches(otherElement, screenWidth = 1080, screenHeight = 2400)
        assertTrue("Expected score < 0.5 but was $otherScore", otherScore < 0.5f)
    }
}
