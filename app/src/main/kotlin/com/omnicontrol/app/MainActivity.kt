package com.omnicontrol.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.omnicontrol.app.ui.navigation.Screen
import com.omnicontrol.app.ui.screens.CapabilitiesScreen
import com.omnicontrol.app.ui.screens.DashboardScreen
import com.omnicontrol.app.ui.screens.DeveloperScreen
import com.omnicontrol.app.ui.screens.HistoryScreen
import com.omnicontrol.app.ui.screens.ManualConsoleScreen
import com.omnicontrol.app.ui.screens.ModelsScreen
import com.omnicontrol.app.ui.screens.OnboardingScreen
import com.omnicontrol.app.ui.screens.PermissionsScreen
import com.omnicontrol.app.ui.screens.SettingsScreen
import com.omnicontrol.app.ui.theme.OmniControlTheme
import com.omnicontrol.core.common.settings.OmniSettings
import com.omnicontrol.core.common.settings.SettingsState
import com.omnicontrol.core.database.dao.SessionDao
import com.omnicontrol.feature.permissions.PermissionChecker
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var permissionChecker: PermissionChecker

    @Inject
    lateinit var omniSettings: OmniSettings

    @Inject
    lateinit var sessionDao: SessionDao

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        com.omnicontrol.feature.accessibility.OmniAccessibilityService.highlightTargetListener = { bounds ->
            com.omnicontrol.feature.overlay.AssistantService.stateStore.dispatch(
                com.omnicontrol.feature.overlay.state.AssistantEvent.SetTargetHighlight(bounds)
            )
        }

        setContent {
            OmniControlTheme {
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) {
                            permissionChecker.refresh()
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val settings by omniSettings.settingsFlow.collectAsState(initial = SettingsState())
                val startDestination = if (settings.onboardingDone) Screen.Dashboard.route else Screen.Onboarding.route

                OmniNavGraph(
                    startDestination = startDestination,
                    permissionChecker = permissionChecker,
                    omniSettings = omniSettings,
                    sessionDao = sessionDao
                )
            }
        }
    }
}

@Composable
fun OmniNavGraph(
    startDestination: String,
    permissionChecker: PermissionChecker,
    omniSettings: OmniSettings,
    sessionDao: SessionDao
) {
    val navController = rememberNavController()

    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Onboarding.route) {
                OnboardingScreen(
                    onContinue = { navController.navigate(Screen.Permissions.route) }
                )
            }
            composable(Screen.Dashboard.route) {
                DashboardScreen(
                    permissionChecker = permissionChecker,
                    sessionDao = sessionDao,
                    onNavigateToPermissions = { navController.navigate(Screen.Permissions.route) },
                    onNavigateToCapabilities = { navController.navigate(Screen.Capabilities.route) },
                    onNavigateToModels = { navController.navigate(Screen.Models.route) },
                    onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                    onNavigateToHistory = { navController.navigate(Screen.History.route) },
                    onNavigateToDeveloper = { navController.navigate(Screen.Developer.route) }
                )
            }
            composable(Screen.Permissions.route) {
                PermissionsScreen(
                    permissionChecker = permissionChecker,
                    omniSettings = omniSettings,
                    onNavigateToDashboard = {
                        navController.navigate(Screen.Dashboard.route) {
                            popUpTo(Screen.Onboarding.route) { inclusive = true }
                        }
                    },
                    onNavigateToModels = { navController.navigate(Screen.Models.route) }
                )
            }
            composable(Screen.Capabilities.route) {
                CapabilitiesScreen(permissionChecker = permissionChecker)
            }
            composable(Screen.Settings.route) {
                SettingsScreen(omniSettings = omniSettings)
            }
            composable(Screen.History.route) {
                HistoryScreen(sessionDao = sessionDao)
            }
            composable(Screen.Models.route) {
                ModelsScreen()
            }
            composable(Screen.Developer.route) {
                DeveloperScreen(
                    onNavigateToConsole = { navController.navigate(Screen.ManualConsole.route) }
                )
            }
            composable(Screen.ManualConsole.route) {
                ManualConsoleScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
