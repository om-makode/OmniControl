package com.omnicontrol.feature.accessibility

import com.omnicontrol.core.model.AgentAction
import com.omnicontrol.core.model.ElementSource
import com.omnicontrol.core.model.Observation
import com.omnicontrol.core.model.RectBounds
import com.omnicontrol.core.model.Role
import com.omnicontrol.core.model.UiElement
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ScreenObservationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun testObservationPromptFormatting() {
        val elements = listOf(
            UiElement(
                id = 1,
                role = Role.BUTTON,
                text = "Back",
                bounds = RectBounds(0, 0, 100, 100),
                clickable = true
            ),
            UiElement(
                id = 2,
                role = Role.TEXT_FIELD,
                text = "swift tutorials",
                bounds = RectBounds(100, 0, 500, 100),
                editable = true
            ),
            UiElement(
                id = 3,
                role = Role.CONTAINER,
                bounds = RectBounds(0, 100, 1080, 2000),
                scrollable = true
            ),
            UiElement(
                id = 4,
                role = Role.BUTTON,
                text = "Swift Tutorial for Beginners",
                bounds = RectBounds(0, 100, 1080, 400),
                clickable = true
            )
        )

        val obs = Observation(
            packageName = "com.google.android.youtube",
            appLabel = "YouTube",
            activityHint = "search results",
            elements = elements,
            keyboardVisible = false,
            focusedElementId = null
        )

        val prompt = obs.toLlmPromptText()
        assertTrue(prompt.contains("APP: YouTube (com.google.android.youtube)  SCREEN: search results"))
        assertTrue(prompt.contains("KEYBOARD: hidden   FOCUSED: none"))
        assertTrue(prompt.contains("[1] button \"Back\""))
        assertTrue(prompt.contains("[2] text_field \"swift tutorials\""))
        assertTrue(prompt.contains("[3] container \"container\" (scrollable)"))
        assertTrue(prompt.contains("[4] button \"Swift Tutorial for Beginners\""))
    }

    @Test
    fun testCommandParser() {
        val clickAction = CommandParser.parse("click 5")
        assertNotNull(clickAction)
        assertEquals("click", clickAction?.type)
        assertEquals(5, clickAction?.target)

        val typeAction = CommandParser.parse("type 2 swift 6 tutorial")
        assertNotNull(typeAction)
        assertEquals("type_text", typeAction?.type)
        assertEquals(2, typeAction?.target)
        assertEquals("swift 6 tutorial", typeAction?.text)
        assertEquals(true, typeAction?.submit)

        val scrollAction = CommandParser.parse("scroll down")
        assertNotNull(scrollAction)
        assertEquals("scroll", scrollAction?.type)
        assertEquals("down", scrollAction?.direction)

        val openAction = CommandParser.parse("open YouTube")
        assertNotNull(openAction)
        assertEquals("open_app", openAction?.type)
        assertEquals("YouTube", openAction?.app)

        val backAction = CommandParser.parse("back")
        assertNotNull(backAction)
        assertEquals("back", backAction?.type)

        val waitAction = CommandParser.parse("wait 2500")
        assertNotNull(waitAction)
        assertEquals("wait", waitAction?.type)
        assertEquals(2500L, waitAction?.ms)
    }

    @Test
    fun testActionVerifier() {
        val pre = Observation(
            packageName = "com.google.android.youtube",
            appLabel = "YouTube",
            screenFingerprint = "fp_pre",
            elements = listOf(
                UiElement(id = 1, role = Role.BUTTON, text = "Search", bounds = RectBounds(0, 0, 100, 100), clickable = true)
            )
        )

        val post = Observation(
            packageName = "com.google.android.youtube",
            appLabel = "YouTube",
            screenFingerprint = "fp_post",
            elements = listOf(
                UiElement(id = 1, role = Role.TEXT_FIELD, text = "Search YouTube", bounds = RectBounds(0, 0, 500, 100), editable = true)
            )
        )

        val clickResult = ActionVerifier.verify(AgentAction(type = "click", target = 1), pre, post)
        assertTrue(clickResult.isSuccess)

        val appChangePost = post.copy(packageName = "com.android.chrome", appLabel = "Chrome")
        val appResult = ActionVerifier.verify(AgentAction(type = "open_app", app = "Chrome"), pre, appChangePost)
        assertTrue(appResult.isSuccess)
    }

    @Test
    fun testScreenFingerprintChangesOnStructure() {
        val el1 = listOf(
            UiElement(id = 1, role = Role.BUTTON, text = "Button 1", clickable = true),
            UiElement(id = 2, role = Role.TEXT_FIELD, text = "Field 1", editable = true)
        )
        val fp1 = ScreenReader.computeFingerprint("com.example.app", el1)
        val fp1Again = ScreenReader.computeFingerprint("com.example.app", el1)
        assertEquals(fp1, fp1Again)

        val el2 = listOf(
            UiElement(id = 1, role = Role.BUTTON, text = "Button 1", clickable = true),
            UiElement(id = 2, role = Role.TEXT_FIELD, text = "Field 1", editable = true),
            UiElement(id = 3, role = Role.BUTTON, text = "Button 2", clickable = true)
        )
        val fp2 = ScreenReader.computeFingerprint("com.example.app", el2)
        assertTrue(fp1 != fp2)
    }

    @Test
    fun testScreenDumpJsonDeserialization() {
        // Load the sample JSON files
        val ytFile = File("../../testdata/screens/youtube_search.json")
        val settingsFile = File("../../testdata/screens/settings_wifi.json")
        val chromeFile = File("../../testdata/screens/chrome_home.json")

        // Also check direct path for test runner working dir
        val baseDir = if (ytFile.exists()) File("../../testdata/screens") else File("testdata/screens")
        if (baseDir.exists()) {
            val yt = json.decodeFromString<Observation>(File(baseDir, "youtube_search.json").readText())
            assertEquals("com.google.android.youtube", yt.packageName)
            assertEquals(5, yt.elements.size)

            val settings = json.decodeFromString<Observation>(File(baseDir, "settings_wifi.json").readText())
            assertEquals("com.android.settings", settings.packageName)
            assertEquals(Role.SWITCH, settings.elements.find { it.id == 3 }?.role)

            val chrome = json.decodeFromString<Observation>(File(baseDir, "chrome_home.json").readText())
            assertEquals("com.android.chrome", chrome.packageName)
            assertEquals(4, chrome.elements.size)
        }
    }
}
