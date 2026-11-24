package com.omnicontrol.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.omnicontrol.core.database.entity.ActionLogEntity
import com.omnicontrol.core.database.entity.AliasEntity
import com.omnicontrol.core.database.entity.AppPolicyEntity
import com.omnicontrol.core.database.entity.EdgeEntity
import com.omnicontrol.core.database.entity.MessageEntity
import com.omnicontrol.core.database.entity.RoutineEntity
import com.omnicontrol.core.database.entity.ScreenEntity
import com.omnicontrol.core.database.entity.SessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions ORDER BY startedAt DESC")
    fun getAllSessions(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE sessionId = :sessionId")
    suspend fun getSession(sessionId: String): SessionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity)

    @Update
    suspend fun updateSession(session: SessionEntity)

    @Query("DELETE FROM sessions WHERE sessionId = :sessionId")
    suspend fun deleteSession(sessionId: String)
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long
}

@Dao
interface ActionLogDao {
    @Query("SELECT * FROM action_logs WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getLogsForSession(sessionId: String): Flow<List<ActionLogEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertActionLog(log: ActionLogEntity): Long
}

@Dao
interface AliasDao {
    @Query("SELECT * FROM app_aliases")
    fun getAllAliases(): Flow<List<AliasEntity>>

    @Query("SELECT * FROM app_aliases WHERE spokenName = :spokenName COLLATE NOCASE LIMIT 1")
    suspend fun findBySpokenName(spokenName: String): AliasEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlias(alias: AliasEntity)

    @Query("DELETE FROM app_aliases WHERE spokenName = :spokenName")
    suspend fun deleteAlias(spokenName: String)
}

@Dao
interface AppPolicyDao {
    @Query("SELECT * FROM app_policies WHERE packageName = :packageName")
    suspend fun getPolicy(packageName: String): AppPolicyEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setPolicy(policy: AppPolicyEntity)
}

@Dao
interface ScreenGraphDao {
    @Query("SELECT * FROM screens WHERE pkg = :pkg")
    suspend fun getScreensForApp(pkg: String): List<ScreenEntity>

    @Query("SELECT * FROM screens WHERE pkg = :pkg AND fp = :fp LIMIT 1")
    suspend fun findScreen(pkg: String, fp: String): ScreenEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertScreen(screen: ScreenEntity): Long

    @Update
    suspend fun updateScreen(screen: ScreenEntity)

    @Query("SELECT * FROM edges WHERE fromScreenId = :fromScreenId")
    suspend fun getEdgesFrom(fromScreenId: Long): List<EdgeEntity>

    @Query("SELECT edges.* FROM edges INNER JOIN screens ON edges.fromScreenId = screens.id WHERE screens.pkg = :pkg")
    suspend fun getEdgesForApp(pkg: String): List<EdgeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEdge(edge: EdgeEntity): Long

    @Update
    suspend fun updateEdge(edge: EdgeEntity)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY createdAt DESC")
    fun getAllRoutines(): Flow<List<RoutineEntity>>

    @Query("SELECT * FROM routines WHERE id = :id LIMIT 1")
    suspend fun getRoutine(id: String): RoutineEntity?

    @Query("SELECT * FROM routines WHERE name = :name COLLATE NOCASE LIMIT 1")
    suspend fun findRoutineByName(name: String): RoutineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoutine(routine: RoutineEntity)

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun deleteRoutine(id: String)
}
