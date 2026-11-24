package com.omnicontrol.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.omnicontrol.core.database.dao.ActionLogDao
import com.omnicontrol.core.database.dao.AliasDao
import com.omnicontrol.core.database.dao.AppPolicyDao
import com.omnicontrol.core.database.dao.MessageDao
import com.omnicontrol.core.database.dao.SessionDao
import com.omnicontrol.core.database.entity.ActionLogEntity
import com.omnicontrol.core.database.entity.AliasEntity
import com.omnicontrol.core.database.entity.AppPolicyEntity
import com.omnicontrol.core.database.entity.MessageEntity
import com.omnicontrol.core.database.entity.SessionEntity

@Database(
    entities = [
        SessionEntity::class,
        MessageEntity::class,
        ActionLogEntity::class,
        AliasEntity::class,
        AppPolicyEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class OmniDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun messageDao(): MessageDao
    abstract fun actionLogDao(): ActionLogDao
    abstract fun aliasDao(): AliasDao
    abstract fun appPolicyDao(): AppPolicyDao

    companion object {
        private const val DB_NAME = "omnicontrol.db"

        @Volatile
        private var INSTANCE: OmniDatabase? = null

        fun getInstance(context: Context): OmniDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    OmniDatabase::class.java,
                    DB_NAME
                ).fallbackToDestructiveMigration().build()
                INSTANCE = instance
                instance
            }
        }
    }
}
