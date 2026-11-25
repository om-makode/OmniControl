package com.omnicontrol.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {
    @Test fun labelScores() {
        assertTrue(TextMatch.labelScore("subscribe", "Subscribe") > 0.95)
        assertTrue(TextMatch.labelScore("privacy", "Privacy and security") > 0.8)
        assertTrue(TextMatch.labelScore("youtube", "YouTube Music") < TextMatch.labelScore("youtube", "YouTube"))
        assertTrue(TextMatch.labelScore("settings", "Display") < 0.5)
    }

    @Test fun ordinals() {
        assertEquals(2, TextMatch.ordinal("the second video"))
        assertEquals(-1, TextMatch.ordinal("last one"))
        assertEquals(4, TextMatch.ordinal("number 4"))
        assertEquals(3, TextMatch.ordinal("video 3"))
        assertEquals(3, TextMatch.ordinal("3"))
        assertEquals(3, TextMatch.ordinal("3rd"))
    }
}
