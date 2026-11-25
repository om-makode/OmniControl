package com.omnicontrol.app

import android.app.Application
import com.omnicontrol.core.common.CrashHandler
import com.omnicontrol.core.common.settings.OmniSettings
import com.omnicontrol.feature.permissions.PermissionChecker

class OmniApp : Application() {

    lateinit var settings: OmniSettings
        private set
    lateinit var permissions: PermissionChecker
        private set

    override fun onCreate() {
        super.onCreate()
        CrashHandler.init(this)
        settings = OmniSettings(this)
        permissions = PermissionChecker(this)
    }
}
