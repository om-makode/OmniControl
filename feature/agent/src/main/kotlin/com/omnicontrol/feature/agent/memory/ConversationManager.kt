package com.omnicontrol.feature.agent.memory

import com.omnicontrol.core.database.dao.MessageDao
import com.omnicontrol.core.database.dao.SessionDao
import com.omnicontrol.core.database.entity.MessageEntity
import com.omnicontrol.core.database.entity.SessionEntity
import java.util.UUID

class ConversationManager(
    private val sessionDao: SessionDao? = null,
    private val messageDao: MessageDao? = null
) {
    constructor() : this(null, null)

    var currentSessionId: String = UUID.randomUUID().toString()
        private set

    val turns = mutableListOf<Pair<String, String>>()
    val appStack = mutableListOf<String>()
    val subGoals = mutableListOf<String>()
    var sessionClipboard: String? = null

    suspend fun startNewSession(goal: String) {
        currentSessionId = UUID.randomUUID().toString()
        turns.clear()
        appStack.clear()
        subGoals.clear()
        sessionClipboard = null

        sessionDao?.insertSession(
            SessionEntity(
                sessionId = currentSessionId,
                startedAt = System.currentTimeMillis(),
                status = "ACTIVE",
                summary = goal
            )
        )
    }

    suspend fun recordTurn(role: String, text: String) {
        turns.add(role to text)
        messageDao?.insertMessage(
            MessageEntity(
                sessionId = currentSessionId,
                role = role,
                content = text,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    fun recordApp(packageName: String) {
        if (packageName.isNotBlank() && (appStack.isEmpty() || appStack.last() != packageName)) {
            appStack.add(packageName)
        }
    }

    suspend fun endSession(status: String = "COMPLETED") {
        sessionDao?.let { dao ->
            val existing = dao.getSession(currentSessionId)
            if (existing != null) {
                dao.updateSession(
                    existing.copy(
                        status = status,
                        endedAt = System.currentTimeMillis()
                    )
                )
            }
        }
    }
}
