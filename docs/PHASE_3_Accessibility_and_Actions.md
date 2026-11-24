# Phase 3 — Screen Reading, Action Engine & Manual Test Console

**Goal:** OmniControl can (1) turn any screen into a clean, numbered list of elements, (2) perform every action reliably, (3) verify the result. All of it testable by hand through a console **before any AI exists**.

**Prerequisite:** Phase 2 accepted.

This is the foundation of the whole project. Spend the time to make it solid.

---

## Shared models (`:core:model`)

```kotlin
enum class Role { BUTTON, TEXT_FIELD, TEXT, IMAGE, IMAGE_BUTTON, CHECKBOX, SWITCH, RADIO, TAB,
                  LIST, LIST_ITEM, CARD, LINK, MENU_ITEM, DROPDOWN, SLIDER, PROGRESS, WEBVIEW,
                  CONTAINER, UNKNOWN }
enum class Source { TREE, OCR, VISION }

@Serializable data class Box(val l: Int, val t: Int, val r: Int, val b: Int) {
    val cx get() = (l + r) / 2; val cy get() = (t + b) / 2
    val w get() = r - l; val h get() = b - t
}

@Serializable data class UiElement(
    val id: Int, val role: Role,
    val text: String? = null, val desc: String? = null, val hint: String? = null,
    val viewId: String? = null, val className: String? = null,
    val box: Box,
    val clickable: Boolean = false, val longClickable: Boolean = false,
    val scrollable: Boolean = false, val editable: Boolean = false,
    val checkable: Boolean = false, val checked: Boolean? = null, val selected: Boolean? = null,
    val enabled: Boolean = true, val focused: Boolean = false, val isPassword: Boolean = false,
    val parentId: Int? = null, val containerId: Int? = null,
    val windowType: Int = 0, val source: Source = Source.TREE
)

@Serializable data class Observation(
    val packageName: String, val appLabel: String, val windowTitle: String?,
    val elements: List<UiElement>, val fingerprint: String,
    val keyboardVisible: Boolean, val focusedId: Int?,
    val dialogIds: List<Int>, val isSecure: Boolean,
    val screenW: Int, val screenH: Int, val timestamp: Long
)
```

Actions (`sealed interface Action`, `@Serializable`): `OpenApp(name)`, `OpenUrl(url)`, `Click(target)`, `LongPress(target)`, `TypeText(target?, text, submit)`, `ClearText(target)`, `Scroll(target?, direction, amount=1)`, `Swipe(fromX,fromY,toX,toY,durationMs)` (relative 0..1), `Back`, `Home`, `Recents`, `Notifications`, `QuickSettings`, `Expand(target)`, `Collapse(target)`, `Select(target)`, `Wait(ms)`, `Find(query, maxScrolls)`.

`data class ActionResult(val ok: Boolean, val message: String, val durationMs: Long, val after: Observation?)`.

---

## Task 3.1 — Final accessibility service

Move the Spike A service to its final form in `:feature:accessibility`:
- Keep the XML config from Phase 0.
- `OmniAccessibilityService` exposes itself through a `@Singleton A11yBridge` (holder with `StateFlow<OmniAccessibilityService?>`) set in `onServiceConnected()`, cleared in `onUnbind()`/`onDestroy()`. **No static `instance`** anymore.
- Forward events into a `MutableSharedFlow<A11yEventLite>(extraBufferCapacity = 64)` (only type, package, time, className — **never hold `AccessibilityEvent` or `AccessibilityNodeInfo` objects beyond the callback**).
- Track `currentPackage` from `TYPE_WINDOW_STATE_CHANGED`, ignoring our own package and system UI overlays where sensible.
- `onKeyEvent`: if panic keys enabled and VOLUME_UP + VOLUME_DOWN are both down within 300 ms → emit `PanicStop` and return true. Otherwise return false (never swallow normal key presses).
- `onInterrupt()` logs.

## Task 3.2 — ScreenReader → Observation

`ScreenReader.observe(): Observation` (runs on `Dispatchers.Default`, timeout 1.5 s):

1. `service.windows` → keep windows of type APPLICATION, SYSTEM (for notifications shade), INPUT_METHOD (to detect keyboard), and **skip** `ACCESSIBILITY_OVERLAY` and any window whose root package is `com.omnicontrol.app`.
2. Walk each root depth-first (max depth 60, max 1500 nodes). For each node: `refresh()` not needed during walk; read properties once.
3. **Keep** a node if visible to user (`isVisibleToUser`) AND (has text/desc/hint OR is clickable/longClickable/scrollable/editable/checkable OR is an image with size ≥ 48dp).
4. **Collapse wrappers:** a non-clickable container with exactly one kept child is skipped (child takes its place).
5. **Merge text into clickable parents:** if a clickable node has no text/desc, concatenate the text of its non-clickable descendants (max 120 chars, joined with " · "). Mark it `role = CARD` if it contains an image + text and is larger than 25% screen width × 8% screen height.
6. **Role mapping** from className + properties:
   - `EditText` or editable → TEXT_FIELD; `Button`/`ImageButton` → BUTTON/IMAGE_BUTTON; `CheckBox` → CHECKBOX; `Switch`/`SwitchCompat` → SWITCH; `RadioButton` → RADIO; `RecyclerView`/`ListView`/`ScrollView`/scrollable → LIST/CONTAINER; child of a scrollable list that is clickable → LIST_ITEM; `TabWidget` child or `selected`+in bottom 15% → TAB; `WebView` → WEBVIEW; `ImageView` → IMAGE; `Spinner` → DROPDOWN; `SeekBar` → SLIDER; clickable TextView → BUTTON or LINK (if text looks like URL).
7. **Order:** reading order (top→bottom, then left→right, within 12px row tolerance).
8. **IDs:** 1..N in order. Keep a `Map<Int, NodeRef>` for the **current observation only**, where `NodeRef` stores `windowId`, the path of child indexes from root, bounds and a signature, so the node can be **re-found** at action time (see 3.5). Never store `AccessibilityNodeInfo` long-term.
9. `isPassword` from `node.isPassword`.
10. `keyboardVisible` = an INPUT_METHOD window exists.
11. `dialogIds` = elements in a window smaller than screen and above the app window (or class names containing "Dialog"/"AlertDialog").
12. `isSecure` = (app window root is null or has 0 kept nodes) AND app is not a launcher. Phase 6 confirms with black screenshot.
13. App label via `PackageManager.getApplicationLabel` (cache by package).

**Performance target:** < 150 ms for YouTube home on the test phone. Log the timing.

## Task 3.3 — Fingerprint

`Fingerprint.of(obs)` = SHA-1 (hex, first 16 chars) of: packageName + sorted set of *stable tokens*:
- viewIds (without package prefix),
- texts/descs of BUTTON, TAB, TEXT_FIELD hints, MENU_ITEM (lowercase, digits replaced by `#`, max 40 chars each),
- **exclude** LIST_ITEM/CARD texts (dynamic content like video titles) and times/counts.

Two fingerprints are used:
- `structureFp` (above) → "same screen type" (used by graph in Phase 7),
- `contentFp` (hash of all element texts + boxes) → "did anything change" (used by verifier).

## Task 3.4 — Screen settle detector

`suspend fun awaitSettle(quietMs = 300, maxMs = 3000)`: waits until no `WINDOW_CONTENT_CHANGED` / `WINDOW_STATE_CHANGED` / `VIEW_SCROLLED` event has arrived for `quietMs`, or `maxMs` passed. Implement with the event `SharedFlow` + `withTimeoutOrNull` + `debounce`-like logic. Always wait at least 120 ms after an action.

## Task 3.5 — ActionExecutor

`suspend fun execute(action: Action, obs: Observation): ActionResult`, serialized with a `Mutex` (one action at a time).

**Re-finding the node** (`NodeResolver.resolve(id, obs)`):
1. Get fresh root of the stored `windowId`, walk the stored child-index path.
2. Verify the found node matches the stored signature (className + viewId + text/desc start + bounds within 24 px). If not, search the current tree for the best match by signature.
3. If nothing matches → fail with `"element #$id is gone; screen changed"`.

**Actions:**

| Action | Primary | Fallback |
|---|---|---|
| Click | `node.performAction(ACTION_CLICK)` on node, else nearest clickable ancestor | tap gesture at box center (50 ms) |
| LongPress | `ACTION_LONG_CLICK` | gesture 650 ms at center |
| TypeText | if target: click to focus → `ACTION_SET_TEXT` with `ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE`; if no target: use `findFocus(FOCUS_INPUT)`; verify text; `submit=true` → `AccessibilityAction.ACTION_IME_ENTER` (API 30+) | if SET_TEXT unsupported: set system clipboard + `ACTION_PASTE`; submit fallback: tap a visible "Search"/"Go"/"Send" button only if action allows (Send needs confirmation!) |
| ClearText | `ACTION_SET_TEXT` with "" | select all + delete not needed |
| Scroll | on target or the largest scrollable container: `ACTION_SCROLL_FORWARD/BACKWARD` or directional `AccessibilityAction.ACTION_SCROLL_DOWN/UP/LEFT/RIGHT` | swipe gesture inside container bounds (from 75% to 25% height, 300 ms) |
| Swipe | `dispatchGesture` with a `Path` from→to | — |
| Back/Home/Recents/Notifications/QuickSettings | `performGlobalAction(GLOBAL_ACTION_*)` | — |
| Expand/Collapse | `ACTION_EXPAND` / `ACTION_COLLAPSE` | click |
| Select | click if not already checked/selected | — |
| OpenApp | `AppLauncher` (below) | — |
| OpenUrl | only `http`/`https`: `Intent(ACTION_VIEW, uri).addFlags(FLAG_ACTIVITY_NEW_TASK)` | — |
| Wait | `delay(min(ms, 5000))` | — |
| Find | repeat: search current obs for query (fuzzy) → found → return; else Scroll down → settle → observe; max `maxScrolls` (default 6); stop if contentFp unchanged after scroll (end of list) | — |

`dispatchGesture` as suspend:
```kotlin
suspend fun AccessibilityService.gesture(g: GestureDescription): Boolean =
    suspendCancellableCoroutine { cont ->
        val ok = dispatchGesture(g, object : GestureResultCallback() {
            override fun onCompleted(d: GestureDescription) { if (cont.isActive) cont.resume(true) }
            override fun onCancelled(d: GestureDescription) { if (cont.isActive) cont.resume(false) }
        }, null)
        if (!ok && cont.isActive) cont.resume(false)
    }
```

**Rules:**
- Refuse `TypeText` into `isPassword` fields (return error "I can't type passwords").
- Refuse any action on our own package's windows.
- Every action: timeout 5 s (Find: 20 s). After action → `awaitSettle()` → `observe()` → return in `ActionResult.after`.
- Log each action to `ActionLogEntity` (type, target signature, ok, duration) — no screen text.

**AppLauncher:**
- Load launchable apps: `packageManager.queryIntentActivities(Intent(ACTION_MAIN).addCategory(CATEGORY_LAUNCHER), 0)` (works thanks to `<queries>` element — add it to the manifest). Cache label → package; refresh on `ACTION_PACKAGE_ADDED/REMOVED`.
- Fuzzy match spoken name: normalize (lowercase, remove "app", punctuation), exact → startsWith → Jaro-Winkler ≥ 0.88 → alias table (Phase 7). Return top match + score; ambiguous (two scores within 0.03) → return both for clarification.
- Launch with `packageManager.getLaunchIntentForPackage(pkg)?.addFlags(FLAG_ACTIVITY_NEW_TASK)`.
- Starting activities from an accessibility service is allowed (it is exempt from background activity start limits while bound).

## Task 3.6 — Verifier

```kotlin
sealed interface Expectation {
    data object ScreenChanges : Expectation
    data class AppIs(val pkg: String) : Expectation
    data class TextVisible(val query: String) : Expectation
    data class ElementGone(val signature: String) : Expectation
    data class FieldContains(val id: Int, val text: String) : Expectation
    data object None : Expectation
}
```
Default expectation per action: Click → ScreenChanges (or checked state toggled for checkbox/switch); TypeText → FieldContains; OpenApp → AppIs; Back → ScreenChanges; Scroll → ScreenChanges (unchanged = end of list, not an error, report "end of list").
On failure: retry once with the fallback method; still failing → `ActionResult(ok=false, …)`.

## Task 3.7 — Overlay-safe tapping

Before any gesture or node action whose target box intersects one of our overlay windows:
1. Set bubble + panel + pill **not touchable** (`OverlayWindowManager.setTouchable(false)`), and if the bubble covers the target, move it temporarily to the opposite edge.
2. Show highlight pulse on the target box (Phase 2 layer) for ~250 ms (skippable in settings for speed).
3. Execute. 4. Restore after settle.

## Task 3.8 — Manual Test Console + debug boxes (`:feature:debug`)

A tab inside the panel (debug builds or Developer toggle):
- **Elements list:** live numbered list of the current observation (role, text, flags like 👆 clickable, ✎ editable, ↕ scrollable). Tap row → highlight on screen.
- **Command line:** text field accepting:
  `obs` · `click 5` · `long 5` · `type 2 hello world` · `type 2 hello !` (`!` = submit) · `clear 2` · `scroll down` · `scroll 3 up` · `swipe 0.5 0.8 0.5 0.2` · `back` · `home` · `recents` · `notif` · `qs` · `open youtube` · `url https://example.com` · `find subscribe` · `wait 1000` · `dump` (export JSON).
  Parser is a pure Kotlin class with unit tests. It produces the same `Action` objects the AI will use later.
- **Result line:** ok/fail, message, duration, fingerprint changed yes/no.
- **Debug boxes toggle:** draws numbered boxes on the highlight layer for all elements (color by role). Use this to verify coordinates line up exactly with the real UI.

## Task 3.9 — Tree dumps + unit tests

- `dump` writes `Observation` JSON (+ optional screenshot PNG) to `files/dumps/<pkg>_<time>.json`. Pull with `adb shell run-as com.omnicontrol.app cat files/dumps/x.json > testdata/screens/x.json`.
- Collect ≥ 25 dumps now: YouTube (home, search typing, results, video, channel), Chrome (new tab, results, page), WhatsApp (chats, a chat), Settings (main, Wi-Fi, apps), Play Store, Files, a shopping app (home, account, orders), Instagram (profile), launcher home.
  **Privacy:** pick chats/accounts with no sensitive content, or edit texts in the JSON before committing.
- Unit tests (JVM) for: role mapping helpers, fingerprint stability (same screen type, different video titles → same `structureFp`), console parser, fuzzy app matching, Find logic with a fake executor.

---

## Acceptance checklist (using only the console)

- [ ] `open youtube` → `click <search>` → `type <field> swift tutorials !` → `click <2nd result>` → `click <channel name>` → `back` → `back` all succeed with ✓.
- [ ] Chrome: type a URL in the address bar and open it.
- [ ] Settings: `scroll down` until the end; `find about phone`; click it.
- [ ] `qs` then click the Wi-Fi tile (toggle) then `back`.
- [ ] Debug boxes align with the real UI on 3 apps (±4 px).
- [ ] Bubble positioned on top of a target does not block the tap.
- [ ] `observe` < 150 ms on YouTube home (log).
- [ ] Typing into a password field is refused.
- [ ] Unit tests pass; ≥ 25 dumps in `testdata/screens/`.

## Common problems
TROUBLESHOOTING.md #5 (service not connected), #17 (empty/partial tree), #18 (ACTION_CLICK returns true but nothing happens), #19 (gesture coordinates off), #20 (SET_TEXT ignored), #21 (stale node / IllegalStateException).
