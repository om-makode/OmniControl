package com.omnicontrol.feature.agent

import com.omnicontrol.feature.agent.llm.ActionGrammar
import com.omnicontrol.feature.agent.llm.FallbackLlmEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FallbackLlmEngineTest {

    @Test
    fun testFindVisibleTargetAndClick() {
        val prompt = """
<|system|>
You are an agent.
<|end|>
<|observation|>
APP: YouTube (com.google.android.youtube)
KEYBOARD: hidden   FOCUSED: none
[1] button "Search"
[2] view "100 JavaScript Interview Questions | Technical Suneja"
[3] view "Python Full Course"
<|end|>
<|user|>
Goal: open the video of Technical suneja
Output your JSON action:
<|assistant|>
        """.trimIndent()

        val json = FallbackLlmEngine.planFromPrompt(prompt)
        val plan = ActionGrammar.parsePlan(json).getOrNull()
        assertNotNull(plan)
        assertEquals("click", plan!!.action.type)
        assertEquals(2, plan.action.target)
        assertTrue(plan.say?.contains("JavaScript", ignoreCase = true) == true)
    }

    @Test
    fun testScrollDownWhenTargetNotVisible() {
        val prompt = """
<|system|>
You are an agent.
<|end|>
<|observation|>
APP: YouTube (com.google.android.youtube)
KEYBOARD: hidden   FOCUSED: none
[1] button "Search"
[2] view "2027 Software Engineer Job Market"
[3] view "Neuroscientist Guide To Focus"
<|end|>
<|user|>
Goal: open the video of Technical suneja
Output your JSON action:
<|assistant|>
        """.trimIndent()

        val json = FallbackLlmEngine.planFromPrompt(prompt)
        val plan = ActionGrammar.parsePlan(json).getOrNull()
        assertNotNull(plan)
        assertEquals("scroll", plan!!.action.type)
        assertEquals("down", plan.action.direction)
    }

    @Test
    fun testConcludeTaskAfterTargetClicked() {
        val prompt = """
<|system|>
You are an agent.
<|end|>
<|history|>
user: open the video of Technical suneja
assistant: [Step 1] click -> Click registered, screen updated
<|end|>
<|observation|>
APP: YouTube (com.google.android.youtube)
KEYBOARD: hidden   FOCUSED: none
[10] view "Video Player"
<|end|>
<|user|>
Goal: open the video of Technical suneja
Output your JSON action:
<|assistant|>
        """.trimIndent()

        val json = FallbackLlmEngine.planFromPrompt(prompt)
        val plan = ActionGrammar.parsePlan(json).getOrNull()
        assertNotNull(plan)
        assertEquals("done", plan!!.action.type)
    }
}
