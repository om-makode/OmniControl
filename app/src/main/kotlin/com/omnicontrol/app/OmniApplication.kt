package com.omnicontrol.app

import android.app.Application
import com.omnicontrol.core.common.OmniLog

class OmniApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        OmniLog.i("App", "OmniApplication created")
    }
}
