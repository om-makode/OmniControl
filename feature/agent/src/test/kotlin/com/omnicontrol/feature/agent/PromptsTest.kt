package com.omnicontrol.feature.agent

import com.omnicontrol.feature.agent.brain.DecisionKind
import com.omnicontrol.feature.agent.brain.Prompts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PromptsTest {

    @Test fun parsesPlainJson() {
        val d = Prompts.parseDecision("""{"thought":"search box","action":"type","target":4,"text":"swift","submit":true}""").getOrThrow()
        assertEquals(DecisionKind.ACTION, d.kind)
        assertEquals("type", d.action!!.type)
        assertEquals(4, d.action!!.target)
        assertEquals(true, d.action!!.submit)
    }

    @Test fun parsesFencedJsonWithChatter() {
        val raw = "Sure! Here you go:\n```json\n{\"action\": \"tap\", \"target\": \"[12]\", \"thought\": \"the {car} video\"}\n```"
        val d = Prompts.parseDecision(raw).getOrThrow()
        assertEquals("click", d.action!!.type)
        assertEquals(12, d.action!!.target)
    }

    @Test fun parsesNestedActionObject() {
        val d = Prompts.parseDecision("""{"thought":"x","action":{"type":"open_app","app":"YouTube"}}""").getOrThrow()
        assertEquals("open_app", d.action!!.type)
        assertEquals("YouTube", d.action!!.app)
    }

    @Test fun openWithAppBecomesOpenApp() {
        val d = Prompts.parseDecision("""{"action":"open","app":"Settings"}""").getOrThrow()
        assertEquals("open_app", d.action!!.type)
    }

    @Test fun doneAnswerAsk() {
        assertEquals(DecisionKind.DONE, Prompts.parseDecision("""{"action":"done","message":"Opened"}""").getOrThrow().kind)
        val a = Prompts.parseDecision("""{"action":"answer","message":"It is 5 PM"}""").getOrThrow()
        assertEquals(DecisionKind.ANSWER, a.kind)
        assertEquals("It is 5 PM", a.message)
        assertEquals(DecisionKind.ASK, Prompts.parseDecision("""{"action":"ask_user","question":"Which one?"}""").getOrThrow().kind)
    }

    @Test fun scrollAliasKeepsDirection() {
        val d = Prompts.parseDecision("""{"action":"scroll_up"}""").getOrThrow()
        assertEquals("scroll", d.action!!.type)
        assertEquals("up", d.action!!.direction)
    }

    @Test fun noteIsKept() {
        val d = Prompts.parseDecision("""{"action":"wait","note":"Steps: Settings > Display > Dark theme"}""").getOrThrow()
        assertTrue(d.note!!.contains("Dark theme"))
    }

    @Test fun garbageFails() {
        assertTrue(Prompts.parseDecision("I cannot help with that").isFailure)
    }
}
