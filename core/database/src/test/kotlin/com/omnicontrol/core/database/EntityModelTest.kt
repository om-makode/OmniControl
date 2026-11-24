package com.omnicontrol.core.database

import com.omnicontrol.core.database.entity.ActionLogEntity
import com.omnicontrol.core.database.entity.AliasEntity
import com.omnicontrol.core.database.entity.AppPolicyEntity
import com.omnicontrol.core.database.entity.MessageEntity
import com.omnicontrol.core.database.entity.SessionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EntityModelTest {

    @Test
    fun testSessionEntityCreation() {
        val now = System.currentTimeMillis()
        val session = SessionEntity(
            sessionId = "test-session-123",
            startedAt = now,
            status = "active",
            summary = "Opening YouTube"
        )

        assertEquals("test-session-123", session.sessionId)
        assertEquals(now, session.startedAt)
        assertEquals("active", session.status)
        assertEquals("Opening YouTube", session.summary)
    }

    @Test
    fun testMessageEntityCreation() {
        val message = MessageEntity(
            id = 1L,
            sessionId = "test-session-123",
            role = "user",
            content = "Play music",
            timestamp = 1000L
        )

        assertEquals("user", message.role)
        assertEquals("Play music", message.content)
        assertEquals("test-session-123", message.sessionId)
    }

    @Test
    fun testActionLogEntityCreation() {
        val action = ActionLogEntity(
            id = 1L,
            sessionId = "test-session-123",
            actionType = "click",
            targetId = 4,
            parametersJson = "{\"target\": 4}",
            success = true,
            timestamp = 2000L
        )

        assertEquals("click", action.actionType)
        assertEquals(4, action.targetId)
        assertTrue(action.success)
    }

    @Test
    fun testAliasAndPolicyEntities() {
        val alias = AliasEntity(
            spokenName = "my music",
            packageName = "com.google.android.apps.youtube.music",
            appName = "YouTube Music"
        )
        assertEquals("my music", alias.spokenName)

        val policy = AppPolicyEntity(
            packageName = "com.bank.app",
            requireConfirm = true,
            allowGestures = false
        )
        assertTrue(policy.requireConfirm)
    }
}
