package com.omnicontrol.app.di

import android.content.Context
import com.omnicontrol.core.common.DefaultDispatcherProvider
import com.omnicontrol.core.common.DispatcherProvider
import com.omnicontrol.core.common.settings.OmniSettings
import com.omnicontrol.core.database.OmniDatabase
import com.omnicontrol.core.database.dao.ActionLogDao
import com.omnicontrol.core.database.dao.AliasDao
import com.omnicontrol.core.database.dao.AppPolicyDao
import com.omnicontrol.core.database.dao.MessageDao
import com.omnicontrol.core.database.dao.SessionDao
import com.omnicontrol.feature.permissions.PermissionChecker
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDispatcherProvider(): DispatcherProvider = DefaultDispatcherProvider()

    @Provides
    @Singleton
    fun provideOmniSettings(@ApplicationContext context: Context): OmniSettings {
        return OmniSettings(context)
    }

    @Provides
    @Singleton
    fun provideOmniDatabase(@ApplicationContext context: Context): OmniDatabase {
        return OmniDatabase.getInstance(context)
    }

    @Provides
    fun provideSessionDao(db: OmniDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideMessageDao(db: OmniDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideActionLogDao(db: OmniDatabase): ActionLogDao = db.actionLogDao()

    @Provides
    fun provideAliasDao(db: OmniDatabase): AliasDao = db.aliasDao()

    @Provides
    fun provideAppPolicyDao(db: OmniDatabase): AppPolicyDao = db.appPolicyDao()

    @Provides
    @Singleton
    fun providePermissionChecker(@ApplicationContext context: Context): PermissionChecker {
        return PermissionChecker(context)
    }
}
