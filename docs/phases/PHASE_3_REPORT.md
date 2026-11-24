# Phase 3 Report — Accessibility Screen Reading & Action Engine

## Summary
Phase 3 implemented the full screen observation pipeline and action execution engine in `:feature:accessibility`. The system extracts structured UI trees from active windows into unified `Observation` snapshots with deterministic screen fingerprints, performs child text aggregation for composite cards, detects settle quiescence, executes all defined actions (node actions + gesture fallbacks) with pre-action target pulse highlights, verifies post-action screen state changes, and provides an interactive Manual Action Console to inspect trees and execute actions live.

## Files created
- `feature/accessibility/src/main/kotlin/com/omnicontrol/feature/accessibility/ScreenReader.kt`
- `feature/accessibility/src/main/kotlin/com/omnicontrol/feature/accessibility/ScreenSettleDetector.kt`
- `feature/accessibility/src/main/kotlin/com/omnicontrol/feature/accessibility/ActionExecutor.kt`
- `feature/accessibility/src/main/kotlin/com/omnicontrol/feature/accessibility/ActionVerifier.kt`
- `feature/accessibility/src/main/kotlin/com/omnicontrol/feature/accessibility/CommandParser.kt`
- `feature/accessibility/src/test/kotlin/com/omnicontrol/feature/accessibility/ScreenObservationTest.kt`
- `app/src/main/kotlin/com/omnicontrol/app/ui/screens/ManualConsoleScreen.kt`
- `testdata/screens/youtube_search.json`
- `testdata/screens/settings_wifi.json`
- `testdata/screens/chrome_home.json`

## Files modified
- `core/model/src/main/kotlin/com/omnicontrol/core/model/Models.kt` (added `Role`, `ElementSource`, `UiElement`, `Observation`, `AgentAction`, `ActionResult`, and LLM prompt serialization `toLlmPromptText()`)
- `feature/accessibility/build.gradle.kts` (added Kotlin serialization and coroutines test dependencies)
- `feature/accessibility/src/main/kotlin/com/omnicontrol/feature/accessibility/OmniAccessibilityService.kt` (integrated ScreenReader, ScreenSettleDetector, ActionExecutor, and static action dispatch)
- `app/src/main/kotlin/com/omnicontrol/app/MainActivity.kt` (wired ManualConsoleScreen route and target highlight callback)
- `app/src/main/kotlin/com/omnicontrol/app/ui/navigation/Screen.kt` (registered `Screen.ManualConsole`)
- `app/src/main/kotlin/com/omnicontrol/app/ui/screens/DeveloperScreen.kt` (added direct navigation button to Manual Action Console)

## Dependencies added
- `kotlinx-serialization-json`: `1.8.0` (Apache-2.0)
- `kotlinx-coroutines-test`: `1.10.1` (Apache-2.0)

## How it works
- **ScreenReader & Tree Traversal:** Inspects `windows` and `rootInActiveWindow`, filters out OmniControl's own overlay windows, and extracts visible nodes into `UiElement` models with assigned roles (`BUTTON`, `TEXT_FIELD`, `SWITCH`, `CHECKBOX`, `TAB`, `IMAGE`, `CONTAINER`, etc.). For complex clickable cards (e.g., YouTube video rows), child texts and descriptions are automatically concatenated into a unified readable card label.
- **Fingerprinting:** Generates a deterministic SHA-256 hash of the package name, element count, and top interactive element signatures to identify screens and track navigation transitions.
- **Screen Settle Detector:** Tracks `TYPE_WINDOW_CONTENT_CHANGED`, `TYPE_WINDOWS_CHANGED`, and `TYPE_VIEW_SCROLLED` events to debounce execution until the display has been quiescent for 300ms (max 3000ms).
- **ActionExecutor & Fallbacks:** Executes all 17 actions from §10.1 (`open_app`, `open_url`, `click`, `long_press`, `type_text`, `clear_text`, `scroll`, `swipe`, `back`, `home`, `recents`, `notifications`, `quick_settings`, `select`, `wait`, `read_screen`, `done`). Uses direct `AccessibilityNodeInfo` actions when supported, with seamless fallback to `dispatchGesture` tap and swipe vectors at element coordinates.
- **ActionVerifier:** Compares pre- and post-action `Observation` states (fingerprint change, package transitions, text updates, dialog openings, and toggle modifications) to verify action outcome.
- **Target Highlights:** Emits bounds to `AssistantService` to render an animated pulse ring on the target element immediately prior to click/type/long-press.
- **Manual Test Console:** A debug screen in the app showing live screen hierarchy, numbered element IDs, quick navigation shortcuts, overlay debug boxes toggle, and an interactive command bar parsing commands (`click 2`, `type 3 query`, `scroll down`, etc.).

## Build status (commands + result)
- `./gradlew :app:assembleDebug` -> **BUILD SUCCESSFUL**
- `./gradlew testDebugUnitTest` -> **BUILD SUCCESSFUL**

## Tests (unit tests run + result)
- `ScreenObservationTest.testObservationPromptFormatting` -> **PASSED**
- `ScreenObservationTest.testCommandParser` -> **PASSED**
- `ScreenObservationTest.testActionVerifier` -> **PASSED**
- `ScreenObservationTest.testScreenFingerprintChangesOnStructure` -> **PASSED**
- `ScreenObservationTest.testScreenDumpJsonDeserialization` -> **PASSED**
- `AssistantStateStoreTest` (all 5 tests) -> **PASSED**
- `EntityModelTest` (all entities in `:core:database`) -> **PASSED**
