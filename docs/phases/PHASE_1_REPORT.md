# Phase 1 Report — App Shell, Permissions Wizard, Settings & Database

## Summary
Phase 1 implemented the complete application shell, dependency injection architecture with Dagger Hilt (via KSP), persistent settings via DataStore Preferences, local offline SQLite schema via Room database, dynamic permission verification, and all primary UI screens styled in the premium dark theme. Navigation is managed through a single Jetpack Compose `NavHost` directing users dynamically to Onboarding or Dashboard based on setup completion state.

## Files created
- `core/database/build.gradle.kts`
- `core/database/src/main/AndroidManifest.xml`
- `core/database/src/main/kotlin/com/omnicontrol/core/database/entity/Entities.kt` (SessionEntity, MessageEntity, ActionLogEntity, AliasEntity, AppPolicyEntity)
- `core/database/src/main/kotlin/com/omnicontrol/core/database/dao/Daos.kt` (SessionDao, MessageDao, ActionLogDao, AliasDao, AppPolicyDao)
- `core/database/src/main/kotlin/com/omnicontrol/core/database/OmniDatabase.kt`
- `core/database/src/test/kotlin/com/omnicontrol/core/database/EntityModelTest.kt`
- `core/common/src/main/kotlin/com/omnicontrol/core/common/settings/OmniSettings.kt`
- `feature/permissions/build.gradle.kts`
- `feature/permissions/src/main/AndroidManifest.xml`
- `feature/permissions/src/main/kotlin/com/omnicontrol/feature/permissions/PermissionChecker.kt`
- `app/src/main/kotlin/com/omnicontrol/app/OmniApp.kt`
- `app/src/main/kotlin/com/omnicontrol/app/di/AppModule.kt`
- `app/src/main/kotlin/com/omnicontrol/app/ui/navigation/Screen.kt`
- `app/src/main/kotlin/com/omnicontrol/app/ui/screens/OnboardingScreen.kt`
- `app/src/main/kotlin/com/omnicontrol/app/ui/screens/PermissionsScreen.kt`
- `app/src/main/kotlin/com/omnicontrol/app/ui/screens/CapabilitiesScreen.kt`
- `app/src/main/kotlin/com/omnicontrol/app/ui/screens/DashboardScreen.kt`
- `app/src/main/kotlin/com/omnicontrol/app/ui/screens/SettingsScreen.kt`
- `app/src/main/kotlin/com/omnicontrol/app/ui/screens/HistoryScreen.kt`
- `app/src/main/kotlin/com/omnicontrol/app/ui/screens/ModelsScreen.kt`
- `app/src/main/kotlin/com/omnicontrol/app/ui/screens/DeveloperScreen.kt`

## Files modified
- `build.gradle.kts` (applied Hilt & KSP plugins)
- `settings.gradle.kts` (included `:core:database` and `:feature:permissions`)
- `gradle/libs.versions.toml` (added hilt, ksp, room, datastore, navigation-compose)
- `core/common/build.gradle.kts` (added datastore-preferences)
- `app/build.gradle.kts` (configured hilt, ksp, room, and UI dependencies)
- `app/src/main/AndroidManifest.xml` (registered OmniApp, allowBackup=false)
- `app/src/main/kotlin/com/omnicontrol/app/MainActivity.kt` (refactored to `@AndroidEntryPoint` with Compose NavHost)
- `app/src/main/kotlin/com/omnicontrol/app/ui/theme/Color.kt`, `Theme.kt` (custom dark palette)

## Dependencies added (name, version, license)
- Dagger Hilt: `2.55` (Apache-2.0)
- Google KSP: `2.1.20-1.0.31` (Apache-2.0)
- AndroidX Room: `2.6.1` (Apache-2.0)
- AndroidX DataStore Preferences: `1.1.1` (Apache-2.0)
- AndroidX Navigation Compose: `2.8.5` (Apache-2.0)
- AndroidX Hilt Navigation Compose: `1.2.0` (Apache-2.0)

## Permissions added
- `android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (in `:feature:permissions`)

## How it works (short)
- **Dependency Injection:** Hilt injects singletons (`OmniSettings`, `OmniDatabase`, `SessionDao`, `PermissionChecker`) directly into `MainActivity` and across modules.
- **Permission Wizard:** `PermissionChecker` queries system APIs (`canDrawOverlays`, `checkSelfPermission`, `ENABLED_ACCESSIBILITY_SERVICES`, `NotificationManagerCompat`) and emits a reactive `StateFlow<PermissionsState>`. Cards dynamically reflect real-time grant status and offer direct settings intents.
- **Settings & DB:** `OmniSettings` exposes reactive Preferences flows for offline mode and safety toggles. `OmniDatabase` provides Room SQLite storage with foreign-key cascade integrity for conversation sessions and action traces.
- **Device Capabilities:** Inspects physical CPU ABIs, Android OS level, and calculates Total RAM GB to display hardware tier rankings and traffic lights.

## Build status (commands + result)
- `./gradlew :app:assembleDebug` -> **BUILD SUCCESSFUL**
- `./gradlew testDebugUnitTest` -> **BUILD SUCCESSFUL**

## Tests (unit tests run + result)
- `core:database:testDebugUnitTest`: Session, Message, ActionLog, Alias, and AppPolicy entity creation and integrity tests passed.

## Phone testing steps (exact)
1. Install debug APK:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Fresh state verification:
   ```bash
   adb shell pm clear com.omnicontrol.app
   ```
3. Open OmniControl:
   - Verify Onboarding screen displays. Tap "Configure Permissions".
   - Step through the Wizard:
     - Tap Step 1 -> Grant "Display over other apps". Return -> Status turns green.
     - Follow Step 2 for Android 13+ restricted settings if needed.
     - Tap Step 3 -> Enable OmniControl Accessibility Service -> Status turns green.
     - Tap Step 4 & 5 -> Grant Microphone and Notification runtime permissions.
   - Observe green "All set" banner appear. Tap "Continue to Dashboard".
4. Dashboard and Navigation:
   - Verify big "Launch Floating Assistant" button is active.
   - Tap "Device Capabilities" -> Observe RAM tier, specs, and feature traffic lights.
   - Tap "Model Manager" -> Test "Import File / Zip" via system document picker.
   - Tap "Settings" -> Toggle "Offline Mode" and other preferences.
   - Tap "Developer & Spikes" -> Verify spikes A, B, C, D can be run.
