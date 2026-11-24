# Phase 2 — Floating Overlay (bubble, panel, state machine, notification)

**Goal:** a smooth floating assistant that stays above all apps, can be dragged and snapped, expands to a panel, shows state, and can be stopped from the panel or notification.

**Prerequisite:** Phase 1 accepted. Spike D result known (direct tap or trampoline).

---

## Design decision for this phase

One long-running **`AssistantService`** (foreground service) owns:
- the overlay windows,
- (Phase 4) the voice session,
- (Phase 5) the agent.

Foreground types: `specialUse` while idle (bubble only), upgraded to `specialUse | microphone` when a voice session starts (using the method proven in Spike D). This avoids two services fighting over lifecycle.

```xml
<service android:name="com.omnicontrol.feature.overlay.AssistantService"
    android:exported="false"
    android:foregroundServiceType="specialUse|microphone">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Floating assistant bubble that the user taps to control the phone by voice" />
</service>
```

> Remove `SpikeOverlayService` from the manifest when this works.

---

## Task 2.1 — AssistantStateStore (single source of truth)

`:core:model` → states; `:feature:overlay` (or `:core:common`) → `@Singleton class AssistantStateStore`.

```kotlin
sealed interface AssistantState {
    data object Idle : AssistantState              // bubble only
    data object Listening : AssistantState
    data object Processing : AssistantState
    data object Observing : AssistantState
    data class Executing(val step: String, val index: Int, val total: Int?) : AssistantState
    data object Waiting : AssistantState            // session active, waiting for next command
    data class Confirming(val question: String) : AssistantState
    data class Paused(val reason: String) : AssistantState   // handoff to user
    data class Error(val message: String) : AssistantState
    data object Stopped : AssistantState
}

data class UiState(
    val assistant: AssistantState = AssistantState.Idle,
    val panelExpanded: Boolean = false,
    val sessionActive: Boolean = false,
    val partialTranscript: String = "",
    val messages: List<ChatItem> = emptyList(),   // user / assistant / action cards
    val currentApp: String? = null,
    val audioLevel: Float = 0f,                    // 0..1 for waveform
    val homeBrainInUse: Boolean = false
)
```

- `StateFlow<UiState>` + small intent functions (`onBubbleTap()`, `expand()`, `minimize()`, `stopSession()`, `appendMessage()`...).
- Legal transitions in a function `reduce(state, event)`; illegal transitions are logged and ignored. Unit-test the transition table.
- During Phase 2, add a **Developer → "Simulate states"** button that cycles through all states so the UI can be tested without voice/agent.

## Task 2.2 — AssistantService

- `@AndroidEntryPoint class AssistantService : LifecycleService()` (from `androidx.lifecycle:lifecycle-service`).
- Actions via intent: `ACTION_SHOW`, `ACTION_STOP_SESSION`, `ACTION_QUIT`.
- `onCreate()`: create notification channel `assistant` (importance LOW), `startForeground(NOTIF_ID, notification, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` — use `ServiceCompat.startForeground(...)`.
- Check `Settings.canDrawOverlays()` before adding windows; if false → stop self and post a notification "Overlay permission needed".
- `onDestroy()`: remove all windows (`removeViewImmediate` in try/catch), cancel scope.
- Dashboard "Start assistant" → `ContextCompat.startForegroundService(ctx, Intent(ctx, AssistantService::class.java).setAction(ACTION_SHOW))`.
- "Quit" from panel menu → remove overlay and stop service.

## Task 2.3 — Compose in WindowManager (lifecycle owner)

A `ComposeView` added to `WindowManager` crashes with *"ViewTreeLifecycleOwner not found"* unless owners are set. Create:

```kotlin
class OverlayLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val savedStateController = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry get() = savedStateController.savedStateRegistry

    fun attach(view: View) {
        savedStateController.performAttach()
        savedStateController.performRestore(null)
        view.setViewTreeLifecycleOwner(this)
        view.setViewTreeViewModelStoreOwner(this)
        view.setViewTreeSavedStateRegistryOwner(this)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }
    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        viewModelStore.clear()
    }
}
```

`OverlayWindowManager` helper:
- `addWindow(key, composable, params)`, `update(key, params)`, `remove(key)`.
- Base params:
```kotlin
WindowManager.LayoutParams(
    WRAP_CONTENT, WRAP_CONTENT,
    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
    FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_NO_LIMITS or FLAG_HARDWARE_ACCELERATED,
    PixelFormat.TRANSLUCENT
).apply { gravity = Gravity.TOP or Gravity.START }
```
- `setTouchable(key, Boolean)` toggles `FLAG_NOT_TOUCHABLE` (used in Phase 3 so our windows never block the agent's taps).
- `setFocusable(key, Boolean)` toggles `FLAG_NOT_FOCUSABLE` (only true while user types in the panel text box).
- All `WindowManager` calls on the main thread.

## Task 2.4 — Bubble

- 56dp circle, glass dark fill, animated gradient ring colored by state (Phase 1 state colors), small icon in center (mic / spinner / check / alert).
- **Drag:** use `Modifier.pointerInteropFilter` and `MotionEvent.rawX/rawY` (screen coordinates) — this is stable while the window itself moves. Track `downRaw`, `downParamsX/Y`; on move → `params.x = downParamsX + (rawX - downRawX)`; `update()`. Treat movement < 8dp as a **tap**.
- **Snap:** on release, animate `params.x` to left edge (0) or right edge (`screenWidth - bubbleWidth`) with a `ValueAnimator`/`Animatable` spring (≈250 ms). Clamp `y` inside safe area.
- **Insets:** compute safe area from `windowManager.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())`.
- Remember last position in DataStore (side + y percent). Recompute on rotation (`onConfigurationChanged` in service).
- Long-press bubble → small menu: Stop session, Open app, Hide for 1 hour, Quit.
- Optional: "dock" mode (bubble half off-screen when idle for 5 s).

## Task 2.5 — Panel

- Separate window (key `panel`), width = min(screenWidth − 32dp, 420dp), height wrap up to 55% of screen. Positioned near the bubble's vertical position (above or below to stay on screen).
- Expand: bubble tap when panel closed. Minimize: chevron button or tap outside (use `FLAG_WATCH_OUTSIDE_TOUCH` + `ACTION_OUTSIDE` → minimize).
- Enter/exit animation: scale from bubble side + fade (200 ms). Implement by animating content inside the window (keep window size fixed during animation to avoid jank).
- Content (top → bottom):
  1. Header: current app icon + name, state chip, Home Brain badge, minimize, overflow menu.
  2. Conversation list (`LazyColumn`, auto-scroll to newest): user bubbles, assistant bubbles, **action cards** (icon + "Tapped *Search*" + ✓/✗ + duration).
  3. Live transcript line + waveform (`Canvas`, bars driven by `audioLevel` history).
  4. Confirmation card when `Confirming` (Yes / No).
  5. Bottom bar: big **Stop** (red) button, mic toggle, "type instead" text field (on focus → make panel focusable; on send/blur → not focusable again).
- Conversation survives minimize/expand and app switches (it lives in `AssistantStateStore`, not in the composable).

## Task 2.6 — Status pill & target highlight layer

- **Status pill:** small window at top center, shown only when panel is minimized and state is Processing/Observing/Executing/Confirming. Text = short step ("Opening YouTube…"). Not touchable except a tiny ✕ (stop).
- **Highlight layer:** full-screen, **not touchable** (`FLAG_NOT_TOUCHABLE`), transparent window used to draw:
  - a pulse ring around a rectangle (`highlight(rect)` for 400 ms) — used by Phase 3 executor before taps;
  - debug element boxes with numbers (Phase 3 debug mode).
  Coordinates: screen coordinates from accessibility bounds — set this window to `MATCH_PARENT` with `FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS` so (0,0) is the top-left of the screen. Verify alignment in Phase 3.

## Task 2.7 — Notification

- Ongoing, low importance, small icon, title "OmniControl", text = current state.
- Actions: **Stop session** (when active), **Hide bubble / Show bubble**, **Quit**.
- Tapping notification opens MainActivity.
- Update text when state changes (throttle to max 1 update/second).

---

## Acceptance checklist

- [ ] Start from Dashboard → bubble appears over the home screen and over YouTube, Chrome, WhatsApp, Settings.
- [ ] Drag is smooth; release snaps to the nearest edge; never hides under status bar / nav bar / cutout; position remembered after restart and rotation.
- [ ] Tap expands panel; tap outside or chevron minimizes; conversation remains.
- [ ] Typing in the panel text box works; after sending, the app underneath gets keyboard focus back.
- [ ] Developer "Simulate states" shows each state correctly in bubble ring, panel chip, pill and notification.
- [ ] Notification Stop / Quit work.
- [ ] No crash when overlay permission is revoked while running (service stops gracefully).
- [ ] No memory leak warnings after opening/closing panel 30 times (optional: LeakCanary in debug).

## Common problems
TROUBLESHOOTING.md #11 (ViewTreeLifecycleOwner), #12 (FGS start), #13 (specialUse property missing), #14 (overlay steals touches/keyboard), #16 (BadTokenException).
