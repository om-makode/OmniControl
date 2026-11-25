package com.omnicontrol.feature.agent

import com.omnicontrol.core.model.RectBounds
import com.omnicontrol.core.model.Role
import com.omnicontrol.core.model.ScreenState
import com.omnicontrol.core.model.UiElement
import com.omnicontrol.feature.agent.quick.ScreenQueries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ScreenQueriesTest {

    private fun el(id: Int, label: String, top: Int, h: Int = 400, w: Int = 1080, clickable: Boolean = true, inList: Boolean = true, desc: String? = null) =
        UiElement(id, Role.BUTTON, text = label, description = desc, bounds = RectBounds(0, top, w, top + h), clickable = clickable, inList = inList)

    private val youtube = ScreenState(
        packageName = "com.google.android.youtube", screenWidth = 1080, screenHeight = 2400,
        elements = listOf(
            el(1, "Search", 80, h = 120, w = 120, inList = false),
            el(2, "All", 240, h = 90, w = 200),
            el(3, "Swift Tutorial for Beginners", 400),
            el(4, "Channel avatar", 650, h = 100, w = 100),
            el(5, "Sponsored · Buy now", 850),
            el(6, "Learn Swift in 1 hour", 1300),
            el(7, "SwiftUI Car App", 1750),
            el(8, "Home", 2250, h = 140, w = 200, inList = false)
        )
    )

    @Test fun contentItemsSkipChipsAvatarsAdsAndTabs() {
        val ids = ScreenQueries.contentItems(youtube).map { it.id }
        assertEquals(listOf(3, 6, 7), ids)
    }

    @Test fun nth() {
        assertEquals(6, ScreenQueries.nth(youtube, 2)!!.id)
        assertEquals(7, ScreenQueries.nth(youtube, -1)!!.id)
    }

    @Test fun labelMatching() {
        val m = ScreenQueries.findByLabel(youtube, "the search button")
        assertNotNull(m)
        assertEquals(1, m!!.element.id)
        assertEquals(7, ScreenQueries.findByLabel(youtube, "swiftui car app")!!.element.id)
    }
}
