package com.omnicontrol.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey
    val sessionId: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val status: String = "active", // active, completed, cancelled, error
    val summary: String? = null
)

@Entity(
    tableName = "messages",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val role: String, // user, assistant, system
    val content: String,
    val timestamp: Long
)

@Entity(
    tableName = "action_logs",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["sessionId"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class ActionLogEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val actionType: String,
    val targetId: Int? = null,
    val parametersJson: String = "{}",
    val success: Boolean = true,
    val errorMessage: String? = null,
    val timestamp: Long
)

@Entity(tableName = "app_aliases")
data class AliasEntity(
    @PrimaryKey
    val spokenName: String,
    val packageName: String,
    val appName: String
)

@Entity(tableName = "app_policies")
data class AppPolicyEntity(
    @PrimaryKey
    val packageName: String,
    val requireConfirm: Boolean = false,
    val allowGestures: Boolean = true
)
