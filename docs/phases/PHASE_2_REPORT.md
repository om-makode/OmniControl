# Phase 2 Report — Floating Overlay

## Summary
Phase 2 implemented the floating assistant overlay module (`:feature:overlay`) using Jetpack Compose rendered directly within `WindowManager` (`TYPE_APPLICATION_OVERLAY`). The architecture includes a single unidirectional state machine (`AssistantStateStore`) shared across the overlay views, ongoing notification, and background service (`AssistantService`). It provides a draggable 56dp bubble with physics-based edge snapping, an expandable/collapsible glassmorphic panel with conversation history, waveform visualizer and focus-managed text input, an unobtrusive status pill for minimized execution feedback, and a pass-through target pulse and debug bounding-box canvas.

## Files created
- `feature/overlay/build.gradle.kts`
- `feature/overlay/consumer-rules.pro`
- `feature/overlay/src/main/AndroidManifest.xml`
- `feature/overlay/src/main/kotlin/com/omnicontrol/feature/overlay/AssistantService.kt`
- `feature/overlay/src/main/kotlin/com/omnicontrol/feature/overlay/state/AssistantStateStore.kt`
- `feature/overlay/src/main/kotlin/com/omnicontrol/feature/overlay/window/OverlayLifecycleOwner.kt`
- `feature/overlay/src/main/kotlin/com/omnicontrol/feature/overlay/window/OverlayWindowManager.kt`
- `feature/overlay/src/main/kotlin/com/omnicontrol/feature/overlay/ui/BubbleView.kt`
- `feature/overlay/src/main/kotlin/com/omnicontrol/feature/overlay/ui/PanelView.kt`
- `feature/overlay/src/main/kotlin/com/omnicontrol/feature/overlay/ui/StatusPillView.kt`
- `feature/overlay/src/main/kotlin/com/omnicontrol/feature/overlay/ui/HighlightOverlayView.kt`
- `feature/overlay/src/test/kotlin/com/omnicontrol/feature/overlay/AssistantStateStoreTest.kt`
- `core/model/src/main/kotlin/com/omnicontrol/core/model/AssistantState.kt`

## Files modified
- `settings.gradle.kts` (included `:feature:overlay`)
- `gradle/libs.versions.toml` (added `androidx-lifecycle-service`, `androidx-lifecycle-viewmodel`, `androidx-savedstate`)
- `core/common/src/main/kotlin/com/omnicontrol/core/common/OmniLog.kt` (safe log fallback for pure JVM unit tests)
- `app/build.gradle.kts` (added project dependency `:feature:overlay`)
- `app/src/main/kotlin/com/omnicontrol/app/ui/screens/DashboardScreen.kt` (hooked up "Launch Floating Assistant" button)
- `app/src/main/kotlin/com/omnicontrol/app/ui/screens/DeveloperScreen.kt` (added Assistant Overlay Simulator with complete state cycle and highlight simulation)

## Dependencies added (name, version, license)
- `androidx.lifecycle:lifecycle-service`: `2.8.7` (Apache-2.0)
- `androidx.lifecycle:lifecycle-viewmodel`: `2.8.7` (Apache-2.0)
- `androidx.savedstate:savedstate`: `1.2.1` (Apache-2.0)

## Permissions & Services
- Service: `com.omnicontrol.feature.overlay.AssistantService`
- Foreground service type: `specialUse` (upgraded dynamically to `specialUse | microphone` when active speech input begins)
- Service property: `android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE` = "Floating assistant bubble that the user taps to control the phone by voice"

## How it works
- **Overlay Window Management:** `OverlayWindowManager` wraps window additions/removals/updates. Each `ComposeView` added to `WindowManager` is attached to a custom `OverlayLifecycleOwner` satisfying `LifecycleOwner`, `ViewModelStoreOwner`, and `SavedStateRegistryOwner`.
- **Bubble Drag & Edge Snapping:** 56dp circular bubble with animated multi-color state ring. Handles drag touch events with touch slop filtering; on release, snaps with a decelerating spring animation to the nearest screen edge respecting system bar and display cutout insets (`WindowInsets`).
- **Focus-Safe Panel Window:** The panel expands to 92% screen width with conversation history and controls. To prevent stealing keyboard focus from underlying applications (e.g., Chrome, WhatsApp), the panel stays `FLAG_NOT_FOCUSABLE` until the user focuses the "Type instead..." input field, dynamically toggling focusability.
- **Status Pill & Target Highlight Layers:** When the panel is minimized during execution, an unobtrusive pill at the top displays the active step. A full-screen `FLAG_NOT_TOUCHABLE` canvas renders an animated pulse ring on target bounding boxes before actions and numbered debug bounding boxes.
- **Ongoing Notification:** Persistent notification in channel `omnicontrol_overlay_channel` showing live state and action buttons: **Stop**, **Panel**, and **Quit**.

## Build status (commands + result)
- `./gradlew :app:assembleDebug` -> **BUILD SUCCESSFUL**
- `./gradlew testDebugUnitTest` -> **BUILD SUCCESSFUL**

## Tests (unit tests run + result)
- `AssistantStateStoreTest.testBubbleTapTogglesPanel` -> **PASSED**
- `AssistantStateStoreTest.testSessionStartAndStop` -> **PASSED**
- `AssistantStateStoreTest.testStateProgression` -> **PASSED**
- `AssistantStateStoreTest.testMessagesAppend` -> **PASSED**
- `AssistantStateStoreTest.testTargetHighlightAndDebugElements` -> **PASSED**
- `EntityModelTest` (all entities in `:core:database`) -> **PASSED**
