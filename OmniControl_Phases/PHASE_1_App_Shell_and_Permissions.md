# Phase 1 — App Shell, Permissions Wizard, Settings & Database

**Goal:** a clean, good-looking main app that guides the user through every permission, shows what works on this phone, and has the storage foundations for later phases.

**Prerequisite:** Phase 0 accepted. Remove the temporary spike buttons from `MainActivity` (keep spike code in a `debug/spikes` package, reachable from a hidden "Developer" screen, until Phase 3 replaces it).

---

## Task 1.1 — Hilt, Application class, navigation

1. Add Hilt (plugin + `hilt-android` + `hilt-compiler` via **KSP**) and `hilt-navigation-compose`, `navigation-compose`.
2. `OmniApp : Application` annotated `@HiltAndroidApp`; register in manifest `android:name=".OmniApp"`.
3. `MainActivity` `@AndroidEntryPoint`, edge-to-edge (`enableEdgeToEdge()`), single `NavHost`.
4. Routes (sealed class `Screen`): `Onboarding`, `Dashboard`, `Permissions`, `Capabilities`, `Settings`, `History`, `Models`, `Developer`.
5. Start destination: `Onboarding` if `settings.onboardingDone == false`, else `Dashboard`.

## Task 1.2 — Theme (premium dark)

`core/common` or `app/ui/theme`:
- Dark color scheme always (light optional later). Background `#0B0B0F`, surface `#15151C`, surfaceVariant `#1E1E27`, primary (accent) `#7C5CFF`, secondary `#22D3EE`, error `#FF5C7A`, success `#34D399`, warning `#FBBF24`.
- Shapes: small 12dp, medium 20dp, large 28dp.
- Typography: default Material 3 with slightly larger titles. (Optional font: Inter via Google Fonts downloadable — skip if it causes issues.)
- State colors used later by the bubble ring: `listening = #22D3EE`, `thinking = #7C5CFF`, `executing = #FBBF24`, `done = #34D399`, `error = #FF5C7A`.

## Task 1.3 — Settings (DataStore)

`core/common/settings/OmniSettings.kt` — a repository over DataStore Preferences exposing `Flow<SettingsState>` and suspend setters:

| Key | Type | Default |
|---|---|---|
| onboardingDone | Boolean | false |
| offlineMode | Boolean | true |
| speakReplies | Boolean | true |
| silenceTimeoutMin | Int | 3 |
| endOfSpeechMs | Int | 700 |
| maxStepsPerCommand | Int | 15 |
| recordTraces | Boolean | false |
| wakeWordEnabled | Boolean | false |
| homeBrainEnabled | Boolean | false |
| homeBrainUrl | String | "" |
| selectedLlmModel | String | "" |
| selectedAsrModel | String | "" |
| confirmSubscribeAndLike | Boolean | true |
| panicKeysEnabled | Boolean | true |

Provide it via a Hilt `@Singleton` module.

## Task 1.4 — Room database skeleton

Create `:core:database`:
- `OmniDatabase` (version 1, `exportSchema = true`, schema dir `core/database/schemas` — add the KSP arg `room.schemaLocation`).
- Entities now: `SessionEntity`, `MessageEntity`, `ActionLogEntity`, `AliasEntity`, `AppPolicyEntity` (fields as in `OMNICONTROL_PLAN.md` §15). Graph/routine tables come in Phase 7 (with a migration).
- DAOs with `Flow` queries for lists, `suspend` for writes.
- Hilt module providing the DB and DAOs.
- Unit test (Robolectric or instrumented) inserting and reading a session with messages.
- `android:allowBackup="false"` and `android:dataExtractionRules` excluding everything.

## Task 1.5 — Permission checker

`:feature:permissions` → `PermissionChecker` (injectable) returning a `PermissionsState` data class:

```kotlin
data class PermissionsState(
    val overlay: Boolean,            // Settings.canDrawOverlays(ctx)
    val accessibility: Boolean,      // our service in enabled list (see below)
    val accessibilityConnected: Boolean, // service instance actually running
    val microphone: Boolean,         // checkSelfPermission(RECORD_AUDIO)
    val notifications: Boolean,      // POST_NOTIFICATIONS granted (API 33+) && NotificationManagerCompat.areNotificationsEnabled()
    val batteryUnrestricted: Boolean,// PowerManager.isIgnoringBatteryOptimizations(pkg)
    val modelsInstalled: ModelsState // which models exist in filesDir/models
)
```

Accessibility check (do not rely only on `AccessibilityManager`, it can lag):
```kotlin
val expected = ComponentName(ctx, OmniAccessibilityService::class.java).flattenToString()
val enabled = Settings.Secure.getString(ctx.contentResolver,
    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: ""
val isOn = enabled.split(':').any { it.equals(expected, ignoreCase = true) }
```

Expose `fun refresh()` and a `StateFlow`. Call `refresh()` on every `Lifecycle.Event.ON_RESUME` of `MainActivity` (user comes back from Settings).

## Task 1.6 — Permission wizard

A step-by-step screen, one card per step, each with: title, why it's needed (one sentence), status chip, "Open settings" button, and "How to" expandable help.

| Step | Button action |
|---|---|
| 1. Display over other apps | `Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$pkg"))` |
| 2. Allow restricted settings (sideloaded apps, Android 13+) | `Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg"))` + help text: "Tap ⋮ at the top right → Allow restricted settings → confirm with your PIN. If you don't see ⋮, first try step 3 once — Android shows the option only after a blocked attempt." |
| 3. Accessibility service | `Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)` + help: "Installed apps / Downloaded apps → OmniControl → On" |
| 4. Microphone | runtime request `RECORD_AUDIO` (if permanently denied → open app details) |
| 5. Notifications | runtime request `POST_NOTIFICATIONS` |
| 6. Battery (optional) | `Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg"))` (needs `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` permission in manifest) + OEM tip (Xiaomi "Autostart", Samsung "Never sleeping apps") |
| 7. Models | link to Model Manager |

Wrap every `startActivity` in try/catch (`ActivityNotFoundException`) with a fallback to `Settings.ACTION_SETTINGS`.

The wizard shows a green "All set" banner when steps 1–5 are done and `onboardingDone = true` is saved.

## Task 1.7 — Capability Report

Screen listing features with traffic lights computed from `PermissionsState` + device info:

| Feature | Needs |
|---|---|
| Floating bubble | overlay |
| Read screen | accessibility connected |
| Tap / type / scroll | accessibility connected |
| Screenshots | accessibility + API ≥ 30 |
| Voice (offline) | mic + notifications + ASR/VAD models |
| Local AI | LLM model + RAM tier OK |
| Visual matching | vision models (Phase 6) |
| Home Brain | setting on + reachable (Phase 5) |
| Protected screens (banking etc.) | ❌ not possible by Android design — explanation |

Show device info at the top: model, Android version, RAM, RAM tier.

## Task 1.8 — Dashboard, History, Model Manager, Developer

- **Dashboard:** big "Start assistant" button (disabled until required permissions are green; Phase 2 makes it start the overlay), permission summary chip, last session summary, quick links.
- **History:** list of sessions → messages (reads Room; empty state text).
- **Model Manager:** lists model slots (ASR, VAD, KWS, LLM, OCR-builtin, CLIP, VLM) with installed/missing, size on disk, "Import from file" (SAF `ACTION_OPEN_DOCUMENT` → copy stream into `filesDir/models/<slot>/` on IO with progress), "Delete". (Download-from-URL with SHA-256 can be added here or Phase 8.) Import of folders: accept `.tar` / `.zip` and extract.
- **Developer:** shows spike buttons, "Export logs", app version, build time.

---

## Files to expect
`OmniApp.kt`, `MainActivity.kt`, `ui/theme/*`, `ui/nav/*`, `core/common/settings/*`, `core/database/**`, `feature/permissions/**`, screens under `app/ui/screens/*`.

## Acceptance checklist

- [ ] Fresh install (`adb shell pm clear com.omnicontrol.app`) → onboarding → wizard → all required steps can be completed and turn green after returning from Settings.
- [ ] Revoking overlay or accessibility in Settings turns the item red when returning to the app.
- [ ] Capability Report correct for the phone.
- [ ] Importing a model file via Model Manager copies it and shows the size.
- [ ] No ANR / jank when opening screens (`adb logcat | grep -i "skipped frames"` shows nothing large).
- [ ] Unit tests pass.

## Common problems
TROUBLESHOOTING.md #2 (KSP/Hilt version mismatch), #3 (Room schema export), #4 (restricted settings), #5 (accessibility shows enabled but not connected).
