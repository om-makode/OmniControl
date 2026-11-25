package com.omnicontrol.feature.agent

import com.omnicontrol.feature.agent.quick.QuickCommands
import com.omnicontrol.feature.agent.quick.QuickStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickCommandsTest {

    private fun steps(cmd: String) = QuickCommands.parse(cmd).map { it.second }

    @Test fun openApp() {
        val s = QuickCommands.parseClause("Open YouTube")
        assertTrue(s is QuickStep.OpenOrTap && s.name == "youtube")
    }

    @Test fun compoundCommand() {
        val s = steps("open YouTube, search swift tutorials and play the second video")
        assertEquals(3, s.size)
        assertTrue(s[0] is QuickStep.OpenOrTap)
        assertTrue(s[1] is QuickStep.Search && (s[1] as QuickStep.Search).query == "swift tutorials")
        assertTrue(s[2] is QuickStep.Nth && (s[2] as QuickStep.Nth).n == 2)
    }

    @Test fun andInsideSearchIsNotSplit() {
        val s = steps("search for salt and pepper")
        assertEquals(1, s.size)
        assertEquals("salt and pepper", (s[0] as QuickStep.Search).query)
    }

    @Test fun searchOnApp() {
        val s = QuickCommands.parseClause("search Kotlin coroutines on YouTube") as QuickStep.Search
        assertEquals("Kotlin coroutines", s.query)
        assertEquals("youtube", s.app)
    }

    @Test fun ordinals() {
        assertEquals(1, (QuickCommands.parseClause("play the first video") as QuickStep.Nth).n)
        assertEquals(3, (QuickCommands.parseClause("open 3rd result") as QuickStep.Nth).n)
        assertEquals(3, (QuickCommands.parseClause("open 3rd video") as QuickStep.Nth).n)
        assertEquals(3, (QuickCommands.parseClause("open video 3") as QuickStep.Nth).n)
        assertEquals(3, (QuickCommands.parseClause("video 3") as QuickStep.Nth).n)
        assertEquals(3, (QuickCommands.parseClause("open 3") as QuickStep.Nth).n)
        assertEquals(3, (QuickCommands.parseClause("tap 3") as QuickStep.Nth).n)
        assertEquals(3, (QuickCommands.parseClause("3") as QuickStep.Nth).n)
        assertEquals(2, (QuickCommands.parseClause("number 2") as QuickStep.Nth).n)
        assertEquals(4, (QuickCommands.parseClause("play 4th video") as QuickStep.Nth).n)
        assertEquals(-1, (QuickCommands.parseClause("tap the last one") as QuickStep.Nth).n)
    }

    @Test fun playMedia() {
        val s = QuickCommands.parseClause("play despacito on youtube") as QuickStep.Play
        assertEquals("despacito", s.query)
        assertEquals("youtube", s.app)
    }

    @Test fun scrollAndSwipe() {
        assertEquals("down", (QuickCommands.parseClause("scroll down") as QuickStep.Scroll).direction)
        assertEquals("down", (QuickCommands.parseClause("more") as QuickStep.Scroll).direction)
        assertEquals("down", (QuickCommands.parseClause("more down") as QuickStep.Scroll).direction)
        assertEquals("down", (QuickCommands.parseClause("down") as QuickStep.Scroll).direction)
        assertEquals("down", (QuickCommands.parseClause("scroll again") as QuickStep.Scroll).direction)
        assertEquals("down", (QuickCommands.parseClause("scroll down please") as QuickStep.Scroll).direction)
        assertEquals("up", (QuickCommands.parseClause("scroll up a bit") as QuickStep.Scroll).direction)
        assertEquals("up", (QuickCommands.parseClause("more up") as QuickStep.Scroll).direction)
        assertEquals("up", (QuickCommands.parseClause("up") as QuickStep.Scroll).direction)
        assertEquals("up", (QuickCommands.parseClause("swipe up") as QuickStep.Swipe).direction)
        assertTrue(QuickCommands.parseClause("go back") is QuickStep.Global)
        assertTrue(QuickCommands.parseClause("go") !is QuickStep.Scroll)
    }

    @Test fun complexGoesToBrain() {
        assertNull(QuickCommands.parseClause("open the video with a car in the thumbnail"))
        // "go to X" tries an app named X, then an on-screen label, then hands over to the AI brain.
        assertTrue(QuickCommands.parseClause("go to the channel") is QuickStep.OpenOrTap)
        val s = steps("open ChatGPT and ask how to turn on dark mode, then do it")
        assertTrue(s[0] is QuickStep.OpenOrTap)
        assertTrue(s.drop(1).any { it == null })
    }

    @Test fun tapLabel() {
        val s = QuickCommands.parseClause("tap on the subscribe button") as QuickStep.TapLabel
        assertEquals("the subscribe button", s.label)
    }

    @Test fun urls() {
        val s = QuickCommands.parseClause("open github.com") as QuickStep.OpenUrl
        assertEquals("github.com", s.url)
    }

    @Test fun mediaControl() {
        val pause = QuickCommands.parseClause("pause video") as QuickStep.MediaControl
        assertEquals(false, pause.play)
        val resume = QuickCommands.parseClause("resume video") as QuickStep.MediaControl
        assertEquals(true, resume.play)
        val resumeShort = QuickCommands.parseClause("resume") as QuickStep.MediaControl
        assertEquals(true, resumeShort.play)
    }

    @Test fun stopDoneYesNo() {
        assertTrue(QuickCommands.isStop("Stop"))
        assertTrue(QuickCommands.isEnd("That's all."))
        assertTrue(QuickCommands.isEnd("stop listening"))
        assertTrue(QuickCommands.isYes("yes"))
        assertTrue(QuickCommands.isNo("no"))
    }
}
