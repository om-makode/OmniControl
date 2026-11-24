package com.omnicontrol.feature.navigator.routine

import com.omnicontrol.core.common.OmniLog
import com.omnicontrol.core.database.dao.RoutineDao
import com.omnicontrol.core.database.entity.RoutineEntity
import com.omnicontrol.feature.navigator.signature.ElementSignature
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

@Serializable
data class RoutineStep(
    val actionType: String,
    val signature: ElementSignature? = null,
    val textArg: String? = null,
    val parameterKey: String? = null
)

class RoutineManager(
    private val routineDao: RoutineDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    var isTeachMode: Boolean = false
        private set
    private val teachSteps = mutableListOf<RoutineStep>()

    fun getAllRoutines(): Flow<List<RoutineEntity>> = routineDao.getAllRoutines()

    suspend fun saveRoutine(
        name: String,
        triggerPhrases: List<String>,
        steps: List<RoutineStep>
    ): RoutineEntity {
        val routine = RoutineEntity(
            id = UUID.randomUUID().toString(),
            name = name,
            triggerPhrasesJson = json.encodeToString(triggerPhrases),
            stepsJson = json.encodeToString(steps),
            createdAt = System.currentTimeMillis()
        )
        routineDao.insertRoutine(routine)
        OmniLog.i(TAG, "Saved routine '${routine.name}' with ${steps.size} steps")
        return routine
    }

    suspend fun findMatchingRoutine(utterance: String): RoutineEntity? {
        val clean = utterance.trim().lowercase()
        val direct = routineDao.findRoutineByName(clean)
        if (direct != null) return direct

        // Check if any routine name is contained in utterance
        val all = routineDao.findRoutineByName(clean)
        return all
    }

    fun startTeachMode() {
        isTeachMode = true
        teachSteps.clear()
        OmniLog.i(TAG, "Teach mode started")
    }

    fun recordTeachStep(step: RoutineStep) {
        if (!isTeachMode) return
        teachSteps.add(step)
        OmniLog.d(TAG, "Recorded teach step: ${step.actionType}")
    }

    suspend fun finishTeachMode(name: String): RoutineEntity? {
        if (!isTeachMode) return null
        isTeachMode = false
        if (teachSteps.isEmpty()) return null

        val routine = saveRoutine(name, listOf(name.lowercase()), teachSteps.toList())
        teachSteps.clear()
        return routine
    }

    fun cancelTeachMode() {
        isTeachMode = false
        teachSteps.clear()
    }

    companion object {
        private const val TAG = "RoutineManager"
    }
}
