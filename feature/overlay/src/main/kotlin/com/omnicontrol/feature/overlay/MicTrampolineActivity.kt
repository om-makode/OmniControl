package com.omnicontrol.feature.overlay

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Invisible activity used only when Android refuses to start the microphone from the floating
 * orb (some phones treat the overlay as "background"). A visible activity makes the app
 * foreground for a moment, which is enough to start the microphone service.
 */
class MicTrampolineActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startService(Intent(this, AssistantService::class.java).setAction(AssistantService.ACTION_START_LISTENING_FG))
        finish()
        overridePendingTransition(0, 0)
    }
}
