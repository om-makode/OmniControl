package com.omnicontrol.app.ui.navigation

sealed class Screen(val route: String) {
    data object Onboarding : Screen("onboarding")
    data object Dashboard : Screen("dashboard")
    data object Permissions : Screen("permissions")
    data object Capabilities : Screen("capabilities")
    data object Settings : Screen("settings")
    data object History : Screen("history")
    data object Models : Screen("models")
    data object Developer : Screen("developer")
    data object ManualConsole : Screen("manual_console")
}
