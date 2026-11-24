package com.omnicontrol.feature.navigator.graph

import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.database.dao.ScreenGraphDao
import com.omnicontrol.core.database.entity.EdgeEntity
import com.omnicontrol.feature.navigator.signature.ElementSignature
import kotlinx.serialization.json.Json
import java.util.PriorityQueue

data class NavStep(
    val actionType: String,
    val signature: ElementSignature?,
    val textArg: String?
)

class DijkstraNavigator(
    private val screenGraphDao: ScreenGraphDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun findRoute(
        pkg: String,
        currentFp: String,
        goalLabel: String
    ): List<NavStep> {
        val allScreens = screenGraphDao.getScreensForApp(pkg)
        if (allScreens.isEmpty()) return emptyList()

        val startNode = allScreens.find { it.fp == currentFp } ?: return emptyList()
        val targetNodes = allScreens.filter { screen ->
            screen.label.contains(goalLabel, ignoreCase = true) ||
            screen.labelsJson.contains(goalLabel, ignoreCase = true)
        }

        if (targetNodes.isEmpty()) {
            OmniLog.d(TAG, "No screen in $pkg matching goal '$goalLabel'")
            return emptyList()
        }

        val targetIds = targetNodes.map { it.id }.toSet()
        if (targetIds.contains(startNode.id)) {
            OmniLog.d(TAG, "Already at target screen!")
            return emptyList()
        }

        val allEdges = screenGraphDao.getEdgesForApp(pkg)
        val edgeMap = allEdges.groupBy { it.fromScreenId }

        // Dijkstra algorithm
        val dist = mutableMapOf<Long, Float>().withDefault { Float.MAX_VALUE }
        val prevEdge = mutableMapOf<Long, EdgeEntity>()
        val queue = PriorityQueue<Pair<Long, Float>>(compareBy { it.second })

        dist[startNode.id] = 0f
        queue.add(startNode.id to 0f)

        var destinationId: Long? = null

        while (queue.isNotEmpty()) {
            val (currentId, currentDist) = queue.poll() ?: break

            if (targetIds.contains(currentId)) {
                destinationId = currentId
                break
            }

            if (currentDist > (dist[currentId] ?: Float.MAX_VALUE)) continue

            val edges = edgeMap[currentId] ?: emptyList()
            for (edge in edges) {
                val cost = calculateEdgeCost(edge)
                val newDist = currentDist + cost
                if (newDist < (dist[edge.toScreenId] ?: Float.MAX_VALUE)) {
                    dist[edge.toScreenId] = newDist
                    prevEdge[edge.toScreenId] = edge
                    queue.add(edge.toScreenId to newDist)
                }
            }
        }

        if (destinationId == null) {
            OmniLog.d(TAG, "No path found from screen ${startNode.id} to goal '$goalLabel'")
            return emptyList()
        }

        // Reconstruct path
        val path = mutableListOf<NavStep>()
        var curr = destinationId
        while (curr != null && curr != startNode.id) {
            val edge = prevEdge[curr] ?: break
            val sig = try {
                if (edge.signatureJson.isNotBlank() && edge.signatureJson != "{}") {
                    json.decodeFromString<ElementSignature>(edge.signatureJson)
                } else null
            } catch (e: Exception) {
                null
            }

            path.add(0, NavStep(actionType = edge.actionType, signature = sig, textArg = edge.textArg))
            curr = edge.fromScreenId
        }

        OmniLog.i(TAG, "Found Dijkstra route with ${path.size} steps to '$goalLabel'")
        return path
    }

    private fun calculateEdgeCost(edge: EdgeEntity): Float {
        val baseCost = 1.0f
        val failPenalty = edge.failCount * 2.0f
        val successBonus = minOf(edge.successCount, 5) * 0.10f
        val unverifiedPenalty = if (!edge.verified) 1.0f else 0.0f
        return (baseCost + failPenalty - successBonus + unverifiedPenalty).coerceAtLeast(0.1f)
    }

    companion object {
        private const val TAG = "DijkstraNavigator"
    }
}
