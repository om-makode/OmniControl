package com.omnicontrol.app.spike

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.omnicontrol.core.common.OmniLog

class TrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        OmniLog.i("OmniVoice", "TrampolineActivity opened, dispatching intent to SpikeOverlayService")
        val serviceIntent = Intent(this, SpikeOverlayService::class.java).apply {
            action = SpikeOverlayService.ACTION_START_MIC_FROM_TRAMPOLINE
        }
        startService(serviceIntent)
        finish()
    }
}
