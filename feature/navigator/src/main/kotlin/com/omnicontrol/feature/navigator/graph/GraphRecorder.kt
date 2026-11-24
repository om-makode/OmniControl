package com.omnicontrol.feature.navigator.graph

import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.database.dao.ScreenGraphDao
import com.omnicontrol.core.database.entity.EdgeEntity
import com.omnicontrol.core.database.entity.ScreenEntity
import com.omnicontrol.core.model.AgentAction
import com.omnicontrol.core.model.Observation
import com.omnicontrol.core.model.UiElement
import com.omnicontrol.feature.navigator.signature.ElementSignature
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class GraphRecorder(
    private val screenGraphDao: ScreenGraphDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun recordTransition(
        fromObs: Observation,
        toObs: Observation,
        action: AgentAction,
        targetElement: UiElement?,
        screenWidth: Int,
        screenHeight: Int,
        isSuccess: Boolean
    ) {
        if (fromObs.packageName != toObs.packageName) return
        if (fromObs.isSecureWindow || toObs.isSecureWindow) return
        if (action.type.lowercase() in listOf("back", "home", "recents")) return

        val pkg = fromObs.packageName
        val fromNodeId = getOrInsertScreen(fromObs)
        val toNodeId = getOrInsertScreen(toObs)

        if (fromNodeId == toNodeId) return

        val signature = if (targetElement != null) {
            ElementSignature.from(targetElement, screenWidth, screenHeight)
        } else {
            null
        }

        val sigJson = signature?.let { json.encodeToString(it) } ?: "{}"

        val existingEdges = screenGraphDao.getEdgesFrom(fromNodeId)
        val matchingEdge = existingEdges.find { it.toScreenId == toNodeId && it.actionType == action.type }

        if (matchingEdge != null) {
            val updated = if (isSuccess) {
                matchingEdge.copy(
                    successCount = matchingEdge.successCount + 1,
                    lastUsed = System.currentTimeMillis(),
                    verified = true
                )
            } else {
                matchingEdge.copy(
                    failCount = matchingEdge.failCount + 1,
                    lastUsed = System.currentTimeMillis()
                )
            }
            screenGraphDao.updateEdge(updated)
            OmniLog.d(TAG, "Updated edge $fromNodeId -> $toNodeId (success=${updated.successCount}, fail=${updated.failCount})")
        } else if (isSuccess) {
            val newEdge = EdgeEntity(
                fromScreenId = fromNodeId,
                toScreenId = toNodeId,
                actionType = action.type,
                signatureJson = sigJson,
                textArg = action.text,
                successCount = 1,
                failCount = 0,
                lastUsed = System.currentTimeMillis(),
                verified = true
            )
            screenGraphDao.insertEdge(newEdge)
            OmniLog.i(TAG, "Recorded new screen transition edge $fromNodeId -> $toNodeId via ${action.type}")
        }
    }

    private suspend fun getOrInsertScreen(obs: Observation): Long {
        val existing = screenGraphDao.findScreen(obs.packageName, obs.screenFingerprint)
        if (existing != null) {
            screenGraphDao.updateScreen(
                existing.copy(
                    visits = existing.visits + 1,
                    lastSeen = System.currentTimeMillis()
                )
            )
            return existing.id
        }

        val label = obs.activityHint?.ifBlank { null } ?: obs.appLabel.ifBlank { "Screen" }
        val newScreen = ScreenEntity(
            pkg = obs.packageName,
            fp = obs.screenFingerprint,
            label = label,
            visits = 1
        )
        return screenGraphDao.insertScreen(newScreen)
    }

    companion object {
        private const val TAG = "GraphRecorder"
    }
}
