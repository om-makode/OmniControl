package com.omnicontrol.core.common

import com.omnicontrol.core.common.safety.ContentFilter
import com.omnicontrol.core.common.safety.LinkSafety
import com.omnicontrol.core.common.safety.PrivacyFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyFiltersTest {
    @Test fun contentFilterWholeWordsOnly() {
        assertTrue(ContentFilter.containsExplicit("search porn"))
        assertTrue(ContentFilter.containsExplicit("Sex video"))
        assertFalse(ContentFilter.containsExplicit("Essex cricket highlights"))
        assertFalse(ContentFilter.containsExplicit("classic cocktail recipes"))
        assertFalse(ContentFilter.containsExplicit("play the sixth video"))
        assertTrue(ContentFilter.isExplicitUrl("https://www.xvideos.com"))
    }

    @Test fun redaction() {
        val r = PrivacyFilter.redact("Mail a.b@x.com, call +91 98765 43210, card 4111 1111 1111 1111, OTP is 482913, UPI rahul@okhdfc")
        assertFalse(r.contains("a.b@x.com"))
        assertFalse(r.contains("98765"))
        assertFalse(r.contains("4111"))
        assertFalse(r.contains("482913"))
        assertFalse(r.contains("rahul@okhdfc"))
        assertTrue(PrivacyFilter.redact("Swift tutorial 2024 · 1.2M views").contains("2024"))
    }

    @Test fun secretLabels() {
        assertTrue(PrivacyFilter.isSecretLabel("Enter OTP"))
        assertTrue(PrivacyFilter.isSecretLabel("Password"))
        assertFalse(PrivacyFilter.isSecretLabel("Search YouTube"))
    }

    @Test fun links() {
        assertTrue(LinkSafety.warnings("http://192.168.1.4/login").isNotEmpty())
        assertTrue(LinkSafety.warnings("https://bit.ly/xyz").isNotEmpty())
        assertTrue(LinkSafety.warnings("https://www.youtube.com/watch?v=1").isEmpty())
    }
}
