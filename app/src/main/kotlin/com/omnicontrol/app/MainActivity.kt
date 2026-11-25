package com.omnicontrol.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.omnicontrol.app.ui.screens.BrainScreen
import com.omnicontrol.app.ui.screens.HomeScreen
import com.omnicontrol.app.ui.screens.SettingsScreen
import com.omnicontrol.app.ui.theme.OmniControlTheme
import com.omnicontrol.app.ui.theme.Palette
import com.omnicontrol.core.common.settings.SettingsState
import com.omnicontrol.feature.accessibility.OmniAccessibilityService

class MainActivity : ComponentActivity() {

    private val app get() = application as OmniApp

    private val micPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { app.permissions.refresh() }
    private val notifPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { app.permissions.refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        OmniAccessibilityService.connectionListener = { runOnUiThread { app.permissions.refresh() } }
        if (intent?.getBooleanExtra("request_mic", false) == true) micPermission.launch(Manifest.permission.RECORD_AUDIO)

        setContent {
            OmniControlTheme {
                val perms by app.permissions.state.collectAsState()
                val settings by app.settings.settingsFlow.collectAsState(initial = SettingsState())
                var screen by rememberSaveable { mutableStateOf("home") }
                BackHandler(enabled = screen != "home") { screen = "home" }

                Box(Modifier.fillMaxSize().background(Palette.Bg).safeDrawingPadding()) {
                    AnimatedContent(screen, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "nav") { s ->
                        when (s) {
                            "brain" -> BrainScreen(settings, app.settings) { screen = "home" }
                            "settings" -> SettingsScreen(settings, perms, app.settings) { screen = "home" }
                            else -> HomeScreen(
                                perms = perms,
                                settings = settings,
                                onRequestMic = { micPermission.launch(Manifest.permission.RECORD_AUDIO) },
                                onRequestNotifications = {
                                    if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                },
                                onOpenBrain = { screen = "brain" },
                                onOpenSettings = { screen = "settings" }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        app.permissions.refresh()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getBooleanExtra("request_mic", false)) micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }
}
