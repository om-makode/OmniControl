package com.omnicontrol.feature.voice

import com.omnicontrol.feature.voice.router.FastRouteResult
import com.omnicontrol.feature.voice.router.FastRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FastRouterTest {

    @Test
    fun testStopCommands() {
        assertEquals(FastRouteResult.Stop, FastRouter.route("stop"))
        assertEquals(FastRouteResult.Stop, FastRouter.route("Stop!"))
        assertEquals(FastRouteResult.Stop, FastRouter.route("cancel"))
        assertEquals(FastRouteResult.Stop, FastRouter.route("wait"))
        assertEquals(FastRouteResult.Stop, FastRouter.route("hold on"))
    }

    @Test
    fun testDoneCommands() {
        assertEquals(FastRouteResult.Done, FastRouter.route("done"))
        assertEquals(FastRouteResult.Done, FastRouter.route("Done."))
        assertEquals(FastRouteResult.Done, FastRouter.route("stop listening"))
        assertEquals(FastRouteResult.Done, FastRouter.route("that's all"))
        assertEquals(FastRouteResult.Done, FastRouter.route("goodbye"))
    }

    @Test
    fun testNavigationCommands() {
        val back = FastRouter.route("go back")
        assertTrue(back is FastRouteResult.Action)
        assertEquals("back", (back as FastRouteResult.Action).action.type)

        val home = FastRouter.route("go home")
        assertTrue(home is FastRouteResult.Action)
        assertEquals("home", (home as FastRouteResult.Action).action.type)

        val recents = FastRouter.route("show recents")
        assertTrue(recents is FastRouteResult.Action)
        assertEquals("recents", (recents as FastRouteResult.Action).action.type)

        val notifs = FastRouter.route("show notifications")
        assertTrue(notifs is FastRouteResult.Action)
        assertEquals("notifications", (notifs as FastRouteResult.Action).action.type)
    }

    @Test
    fun testOpenAppCommands() {
        val yt = FastRouter.route("open YouTube")
        assertTrue(yt is FastRouteResult.Action)
        val ytAction = (yt as FastRouteResult.Action).action
        assertEquals("open_app", ytAction.type)
        assertEquals("YouTube", ytAction.app)

        val chrome = FastRouter.route("launch Chrome")
        assertTrue(chrome is FastRouteResult.Action)
        val chromeAction = (chrome as FastRouteResult.Action).action
        assertEquals("open_app", chromeAction.type)
        assertEquals("Chrome", chromeAction.app)

        val settings = FastRouter.route("switch to Settings")
        assertTrue(settings is FastRouteResult.Action)
        val settingsAction = (settings as FastRouteResult.Action).action
        assertEquals("open_app", settingsAction.type)
        assertEquals("Settings", settingsAction.app)
    }

    @Test
    fun testScrollCommands() {
        val scrollDown = FastRouter.route("scroll down")
        assertTrue(scrollDown is FastRouteResult.Action)
        assertEquals("scroll", (scrollDown as FastRouteResult.Action).action.type)
        assertEquals("down", scrollDown.action.direction)

        // Follow up command "more" after scroll down
        val moreDown = FastRouter.route("more")
        assertTrue(moreDown is FastRouteResult.Action)
        assertEquals("scroll", (moreDown as FastRouteResult.Action).action.type)
        assertEquals("down", moreDown.action.direction)

        val scrollUp = FastRouter.route("scroll up")
        assertTrue(scrollUp is FastRouteResult.Action)
        assertEquals("scroll", (scrollUp as FastRouteResult.Action).action.type)
        assertEquals("up", scrollUp.action.direction)

        // Follow up command "a bit more" after scroll up
        val moreUp = FastRouter.route("a bit more")
        assertTrue(moreUp is FastRouteResult.Action)
        assertEquals("scroll", (moreUp as FastRouteResult.Action).action.type)
        assertEquals("up", moreUp.action.direction)
    }

    @Test
    fun testOpenUrlCommands() {
        val url = FastRouter.route("open url github.com")
        assertTrue(url is FastRouteResult.Action)
        assertEquals("open_url", (url as FastRouteResult.Action).action.type)
        assertEquals("https://github.com", url.action.url)
    }

    @Test
    fun testPassToLlmForComplexQueries() {
        val complex1 = FastRouter.route("search for swift tutorials and tap the second video")
        assertTrue(complex1 is FastRouteResult.PassToLlm)
        assertEquals("search for swift tutorials and tap the second video", (complex1 as FastRouteResult.PassToLlm).utterance)

        val complex2 = FastRouter.route("send a message to Mom saying I will be home by 7pm")
        assertTrue(complex2 is FastRouteResult.PassToLlm)
        assertEquals("send a message to Mom saying I will be home by 7pm", (complex2 as FastRouteResult.PassToLlm).utterance)
    }
}
