package com.omnicontrol.feature.voice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceEditTest {
    @Test fun eachCutRemovesOneWord() {
        val r = VoiceEdit.apply("Open YouTube and search for cut cut go to my profile")
        assertEquals("Open YouTube and go to my profile", r.text)
        assertFalse(r.endsWithCut)
        assertTrue(r.hadCut)
    }

    @Test fun trailingCutKeepsDraft() {
        val r = VoiceEdit.apply("Open YouTube and search for cut cut")
        assertEquals("Open YouTube and", r.text)
        assertTrue(r.endsWithCut)
    }

    @Test fun draftContinuesWithNextPhrase() {
        val r = VoiceEdit.apply(VoiceEdit.join("Open YouTube and", "go to my profile"))
        assertEquals("Open YouTube and go to my profile", r.text)
    }

    @Test fun cutCanEditTheDraft() {
        val r = VoiceEdit.apply(VoiceEdit.join("Open YouTube and", "cut then go home"))
        assertEquals("Open YouTube then go home", r.text)
    }

    @Test fun gluedAndPunctuatedCuts() {
        assertEquals("open", VoiceEdit.apply("open YouTube now cut-cut").text)
        assertEquals("open YouTube", VoiceEdit.apply("open YouTube now Cut.").text)
    }

    @Test fun moreCutsThanWordsIsEmpty() {
        val r = VoiceEdit.apply("open cut cut cut")
        assertEquals("", r.text)
        assertTrue(r.endsWithCut)
    }

    @Test fun noCutLeavesTextAlone() {
        val r = VoiceEdit.apply("search haircut styles")
        assertEquals("search haircut styles", r.text)
        assertFalse(r.hadCut)
    }
}
