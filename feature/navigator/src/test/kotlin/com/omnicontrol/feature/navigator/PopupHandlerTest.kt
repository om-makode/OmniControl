package com.omnicontrol.feature.navigator

import com.omnicontrol.core.model.Observation
import com.omnicontrol.core.model.Role
import com.omnicontrol.core.model.UiElement
import com.omnicontrol.feature.navigator.popup.PopupHandler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PopupHandlerTest {

    @Test
    fun testDetectDismissablePopup() {
        val obs = Observation(
            packageName = "com.sample.app",
            elements = listOf(
                UiElement(id = 1, role = Role.TEXT, text = "Rate this app?"),
                UiElement(id = 2, role = Role.BUTTON, text = "Not now", clickable = true),
                UiElement(id = 3, role = Role.BUTTON, text = "Rate 5 stars", clickable = true)
            )
        )

        val dismiss = PopupHandler.checkPopup(obs)
        assertNotNull(dismiss)
        assertEquals(2, dismiss?.element?.id)
        assertEquals("Not now", dismiss?.dismissText)
    }

    @Test
    fun testIgnorePermissionController() {
        val obs = Observation(
            packageName = "com.google.android.permissioncontroller",
            elements = listOf(
                UiElement(id = 1, role = Role.BUTTON, text = "Don't allow", clickable = true),
                UiElement(id = 2, role = Role.BUTTON, text = "While using the app", clickable = true)
            )
        )

        val dismiss = PopupHandler.checkPopup(obs)
        assertNull(dismiss)
    }
}
