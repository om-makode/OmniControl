package com.omnicontrol.feature.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechFixerTest {
    @Test fun fixesAppNames() {
        assertEquals("open YouTube", SpeechFixer.clean("open you tube"))
        assertEquals("open ChatGPT", SpeechFixer.clean("open chat gpt"))
        assertEquals("open ChatGPT", SpeechFixer.clean("open chad gbt"))
        assertEquals("open WhatsApp", SpeechFixer.clean("open what's app"))
    }

    @Test fun dropsFiller() {
        assertEquals("open YouTube", SpeechFixer.clean("hey omni can you please open YouTube"))
        assertEquals("Scroll down", SpeechFixer.clean("Scroll down please."))
        assertEquals("highlight this", SpeechFixer.clean("highlight this"))
        assertEquals("justify text", SpeechFixer.clean("justify text"))
    }

    @Test fun picksAlternativeMatchingKnownApp() {
        val best = SpeechFixer.pickBest(listOf("open spotty fly", "open Spotify"), listOf("Spotify", "YouTube"))
        assertEquals("open Spotify", best)
    }
}
