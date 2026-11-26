package com.omnicontrol.feature.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceGateTest {
    @Test fun explicitWordIsNotExecuted() {
        val r = VoiceGate.evaluate(listOf("open youtube and search sexy videos"), null, emptyList())
        assertTrue(r is VoiceGate.Result.Repeat)
    }

    @Test fun misheardSixUsesCleanNumberedAlternative() {
        val r = VoiceGate.evaluate(listOf("play sex video", "play six video"), null, emptyList())
        assertEquals("play six video", (r as VoiceGate.Result.Accept).text)
    }

    @Test fun misheardSixWithoutAlternativeAsksToRepeat() {
        assertTrue(VoiceGate.evaluate(listOf("play sex video"), null, emptyList()) is VoiceGate.Result.Repeat)
    }

    @Test fun lowConfidenceAsksToRepeat() {
        assertTrue(VoiceGate.evaluate(listOf("open settings"), floatArrayOf(0.2f), emptyList()) is VoiceGate.Result.Repeat)
        assertTrue(VoiceGate.evaluate(listOf("open settings"), floatArrayOf(0.9f), emptyList()) is VoiceGate.Result.Accept)
        // 0 = "unknown" on many phones, so it must not block
        assertTrue(VoiceGate.evaluate(listOf("open settings"), floatArrayOf(0f), emptyList()) is VoiceGate.Result.Accept)
    }

    @Test fun noiseIsIgnored() {
        assertTrue(VoiceGate.evaluate(listOf("uh"), null, emptyList()) is VoiceGate.Result.Ignore)
    }
}
