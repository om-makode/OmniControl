# OmniControl — Personal AI Android Phone Operator

**Master Plan & Architecture (v1)**
Personal-use, sideloaded, privacy-first, 100% free and on-device by default.
Built to be implemented step by step with Antigravity.

---

## 0. How to use this document

- Sections 1–4 explain **what** we are building and the **key decisions** (with reasons).
- Sections 5–15 are the **technical design** of each subsystem.
- Section 16 is the **phase-by-phase build plan** with acceptance tests.
- Section 17 has **ready-to-paste prompts for Antigravity**, one per phase.
- Give Antigravity one phase at a time. Do not let it move to the next phase until every acceptance test of the current phase passes on your real phone.

---

## 1. Vision in one paragraph

OmniControl is a floating AI operator that lives above every app on your Android phone. You tap the bubble (or say an optional wake word), then talk to it naturally and continuously. It **sees** the screen (accessibility tree + screenshots), **thinks** with a local AI model, **acts** like a human finger (taps, swipes, typing, back/home), **checks** that each step worked, and **remembers** how to reach places inside apps so it gets faster every time. It keeps listening until you say "Done". Nothing leaves your phone unless you explicitly turn on an optional feature that uses your own PC on your own Wi-Fi.

---

## 2. Key decisions (changed from the original idea, with reasons)

| Area | Original idea | Decision in this plan | Why |
|---|---|---|---|
| App UI framework | React Native + TypeScript | **100% native: Kotlin + Jetpack Compose** | 90% of this app is native services (overlay, accessibility, audio, ML). React Native would add a JS bridge, a second language, a second build system and lifecycle problems inside the overlay window, with no real benefit. Compose gives a modern UI in both the main app and the floating overlay with one codebase. |
| Speech-to-text | Vosk / whisper.cpp / Android SpeechRecognizer | **sherpa-onnx** (streaming Zipformer for live text, optional Whisper/Moonshine for a final accuracy pass) | One Apache-2.0 library gives streaming ASR, VAD, keyword spotting and optional TTS. Truly offline. Streaming means you see words as you speak and end-of-speech is detected fast. Android SpeechRecognizer is kept only as an optional fallback because it can silently go online and stops after each phrase. |
| Voice activity detection | WebRTC VAD / Silero | **Silero VAD via sherpa-onnx** | Much better than WebRTC VAD in noise, MIT license, already inside sherpa-onnx. |
| "Stop" while it works | Not covered | **Always-on keyword spotting** for "stop / cancel / wait" during execution | You must be able to interrupt the agent instantly, by voice, without waiting for it to finish. |
| LLM runtime | llama.cpp or MediaPipe | **Pluggable `LlmEngine` interface** with two backends: **llama.cpp** (main, MIT, GGUF models, grammar-constrained JSON) and **LiteRT-LM / MediaPipe** (for Gemma models on GPU/NPU) | llama.cpp's grammar (GBNF / JSON-schema) forces the model to output *valid actions only*, which matters a lot for small models. Keeping an interface lets you swap models as better ones appear. |
| How the AI picks targets | Text/role matching or coordinates | **Indexed element list ("Set-of-Marks")**: the AI answers with an element **ID** like `#14`, never raw coordinates | Small models are bad at coordinates but good at choosing from a numbered list. Coordinates are computed by our code from real element bounds. |
| Visual matching ("video with a car") | General vision model | **Tiered vision**: OCR → CLIP-style image/text matching on cropped thumbnails → small VLM only when needed | Cropping each thumbnail (bounds come from the accessibility tree) and comparing it to the text "a car" with a small image-text model takes milliseconds. A full VLM takes seconds; use it only as the last resort. |
| Screenshots | AccessibilityService or MediaProjection | **AccessibilityService.takeScreenshot() only** | Available since Android 11, no extra consent popup each session (MediaProjection asks every time on Android 14+), no "screen is being recorded" chip. |
| Simple commands | Everything through AI | **Two-speed brain**: instant rule-based fast path + LLM for the rest | "Open YouTube", "go back", "scroll down", "done" should run in <300 ms. The LLM is used only when needed. |
| Navigation memory | Store successful paths | **Per-app UI state graph** + named **Routines** you can teach by demonstration | A graph lets it find *new* routes from known screens, not only replay old ones. Teach mode lets you show it a task once. |
| Bigger brain | Not covered | **Optional "Home Brain"**: use a free model on your own PC (Ollama / llama.cpp server) over your local Wi-Fi | Free, private (never leaves your network), much smarter. Fully optional. Phone-only mode must always work. |

---

## 3. Scope

### In scope (v1)
- Floating bubble + expandable assistant panel above all apps.
- Continuous, hands-free voice session with live transcript and interruption.
- Understanding of the current screen (tree + OCR + visual matching).
- Taps, long press, swipe, scroll, text entry, back/home/recents, notifications, quick settings.
- Launch apps by spoken name (fuzzy, with aliases like "my shopping app").
- Multi-step and cross-app tasks with step verification.
- Navigation memory, screen graph, routines (teach by demonstration).
- Confirmation before sensitive actions; panic stop.
- Local history, local trace logs for debugging.

### Out of scope / not allowed by Android (documented, not hacked around)
- Reading or tapping inside `FLAG_SECURE` screens (most banking apps, some payment screens, Netflix player) — screenshots come back black; the tree may be empty. The assistant will say so and hand control to you.
- Entering passwords, PINs, OTPs, solving CAPTCHA, passing biometrics — **never**, by design.
- Working on the lock screen or keyguard.
- Reading the clipboard in the background (Android 10+ blocks it) — we read text from the screen instead (see §10.5).
- Root-only features.

---

## 4. Target environment

- **Phone:** your physical device, Android 15 (API 35) and Android 16 (API 36).
  `minSdk = 30` (needed for `takeScreenshot()`), `targetSdk = 36`, `compileSdk = 36`.
- **RAM tiers** (decide model sizes in Phase 0 after checking your phone):
  - 6 GB: tiny LLM (≈0.6–1.5B), no on-device VLM, Home Brain recommended for hard tasks.
  - 8 GB: 1.5–3B LLM (4-bit), CLIP matcher, small VLM loaded on demand.
  - 12 GB+: 3–4B LLM, VLM on demand, comfortable.
- **Dev machine:** your Mac with Android Studio (latest stable), JDK 17+, Android SDK + NDK + CMake, `adb`. Antigravity as the coding agent.
- **Distribution:** sideload debug/release APK via `adb install`. No Play Store.

---

## 5. High-level architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                         OmniControl (one APK)                        │
│                                                                      │
│  ┌────────────┐   ┌──────────────────────────────────────────────┐   │
│  │ Main App   │   │            SessionService (FGS: microphone)  │   │
│  │ (Compose)  │   │                                              │   │
│  │ dashboard, │   │  VoiceEngine ──► Transcript ──► Conversation │   │
│  │ permissions│   │   (sherpa-onnx:   (live text)    Manager     │   │
│  │ settings,  │   │    VAD+ASR+KWS)                    │         │   │
│  │ models,    │   │                                    ▼         │   │
│  │ routines,  │   │                         ┌──────────────────┐ │   │
│  │ history,   │   │                         │   Agent Core     │ │   │
│  │ debug      │   │                         │ Router → Planner │ │   │
│  └────────────┘   │                         │ → Executor →     │ │   │
│                   │                         │   Verifier       │ │   │
│  ┌────────────┐   │                         └──┬─────────┬─────┘ │   │
│  │ Overlay UI │◄──┤ state flows                │         │       │   │
│  │ (Compose in│   │                            ▼         ▼       │   │
│  │ WindowMgr) │   │                  LlmEngine     SafetyGuard   │   │
│  └────────────┘   └──────────────────────┬───────────────────────┘   │
│                                          │ actions / observations    │
│  ┌───────────────────────────────────────▼───────────────────────┐   │
│  │        OmniAccessibilityService (bound by system)              │   │
│  │  ScreenReader (tree) · Screenshotter · ActionExecutor          │   │
│  │  (click/scroll/text/gesture/global) · KeyFilter (panic keys)   │   │
│  └───────────────────────────────────────┬───────────────────────┘   │
│                                          │                           │
│  ScreenUnderstanding: OCR · ElementMerger · VisualMatcher · VLM      │
│  Navigator: AppLauncher · ScreenGraph · NavMemory · Routines         │
│  Storage: Room (sessions, messages, graph, routines, aliases, traces)│
│  Optional: ShizukuBridge · HomeBrainClient (LAN)                     │
└──────────────────────────────────────────────────────────────────────┘
```

### 5.1 Gradle modules (independent, testable)

```
:app                    Main activity, navigation, DI setup (Hilt)
:core:model             Shared data classes (Action, Observation, UiElement, ScreenState…)
:core:common            Coroutines dispatchers, Result types, logging, settings (DataStore)
:core:database          Room DB, DAOs, migrations
:feature:overlay        Overlay window manager + Compose overlay UI + state machine
:feature:voice          Audio capture, VAD, ASR, KWS, TTS, VoiceSession loop
:feature:accessibility  AccessibilityService, ScreenReader, ActionExecutor, Screenshotter
:feature:vision         OCR, element merging, CLIP matcher, VLM adapter, Set-of-Marks renderer
:feature:llm            LlmEngine interface + llama.cpp (JNI) + LiteRT-LM + HomeBrain backends
:feature:agent          Router, Planner, Executor, Verifier, ConversationManager, SafetyGuard
:feature:navigator      AppLauncher, ScreenGraph, NavigationMemory, Routines, Teach mode
:feature:permissions    Permission checks, guided setup screens, capability report
:feature:shizuku        Optional Shizuku bridge (input inject, uiautomator dump, auto re-enable)
:feature:debug          Trace recorder, trace viewer, element-box debug overlay, manual action console
:native:llama           CMake build of llama.cpp + JNI glue
```

Rule: feature modules talk only through interfaces in `:core:model`. The agent never imports Android framework classes directly; it receives `Observation` objects and emits `Action` objects.

### 5.2 Main libraries (all free)

| Purpose | Library | License |
|---|---|---|
| UI | Jetpack Compose, Material 3 | Apache 2.0 |
| DI | Hilt | Apache 2.0 |
| Async | Kotlin Coroutines + Flow | Apache 2.0 |
| DB | Room, DataStore | Apache 2.0 |
| JSON | kotlinx.serialization | Apache 2.0 |
| Speech (ASR, VAD, KWS, optional TTS) | sherpa-onnx (k2-fsa) | Apache 2.0 |
| TTS | Android `TextToSpeech` (offline voice installed) | system |
| LLM | llama.cpp (via JNI) | MIT |
| LLM (alt) | LiteRT-LM / MediaPipe LLM Inference | Apache 2.0 (runtime) |
| ML runtime | ONNX Runtime Android (comes with sherpa-onnx), LiteRT | MIT / Apache 2.0 |
| OCR | ML Kit Text Recognition v2 (bundled model, on-device, free) — or PaddleOCR (ONNX) if you want fully open source | Free ToS / Apache 2.0 |
| Fuzzy matching | own Jaro-Winkler/Levenshtein (tiny) or `java-string-similarity` | MIT |
| Optional root-free power | Shizuku API | Apache 2.0 (MIT for API lib) |
| Testing | JUnit5, Turbine, Robolectric, Compose UI test, Macrobenchmark | Apache 2.0 / MIT |

**Model license rule:** before downloading any model, record its license in `docs/MODELS.md`. Prefer Apache 2.0 / MIT models (Qwen family, SmolVLM, SigLIP, Silero, Zipformer). Gemma models are free to use under Google's Gemma terms (fine for personal use). **Avoid AGPL models** (e.g. some YOLO-based UI detectors) to keep things simple.

---

## 6. Permissions & Android 15/16 rules

| Permission / setting | Why | How it is granted |
|---|---|---|
| `SYSTEM_ALERT_WINDOW` | Floating bubble | Settings screen via `ACTION_MANAGE_OVERLAY_PERMISSION` |
| Accessibility service | Read screen, perform actions, screenshots, panic keys | User enables in Settings → Accessibility. **Sideloaded apps on Android 13+ first need: App info → ⋮ → "Allow restricted settings".** The setup screen must explain this with a step-by-step guide. |
| `RECORD_AUDIO` | Voice | Runtime permission |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MICROPHONE` | Keep listening while other apps are open | Manifest; service declared with `foregroundServiceType="microphone"` |
| `POST_NOTIFICATIONS` | Mandatory FGS notification (with Stop action) | Runtime permission |
| `QUERY_ALL_PACKAGES` *or* `<queries>` with launcher intent | List launchable apps for "open X" | `<queries><intent><action MAIN/><category LAUNCHER/></intent></queries>` is enough — prefer this |
| `INTERNET` | Only for model downloads and optional Home Brain | Manifest; a global "Offline mode" switch blocks all network use |
| Battery optimization exemption | Keep the accessibility/session stable on aggressive OEMs | Optional, guided (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) |
| Shizuku | Optional advanced features | Shizuku app + permission request |

Not needed: MediaProjection, Usage Access (the accessibility service already knows the foreground app), contacts, location.

**Important Android rules the code must respect**

1. **Microphone FGS start (Android 14+ "while-in-use" rules):** a microphone foreground service must be started while the app is considered in the foreground. Start the session **from a user tap** on the bubble/panel. Phase 0 must test this on your device; fallback is a transparent "trampoline" activity launched by the tap that starts the service and closes immediately.
2. **Overlay windows:** `TYPE_APPLICATION_OVERLAY` only. The overlay must not cover the area the agent is about to tap — the executor temporarily makes the overlay **non-touchable** (`FLAG_NOT_TOUCHABLE`) or moves the bubble away from the target before dispatching a gesture.
3. **Our own windows in screenshots/tree:** filter out OmniControl windows from observations so the AI never "sees" itself. Hide the overlay (or set it transparent) for the ~50 ms of a screenshot when it overlaps content.
4. **Accessibility config:** `canRetrieveWindowContent`, `canPerformGestures`, `canTakeScreenshot`, `canRequestFilterKeyEvents`, `flagReportViewIds`, `flagRetrieveInteractiveWindows`, `flagIncludeNotImportantViews`. Write an honest `description` string. Do **not** set `isAccessibilityTool=true` (that flag is for real assistive tools); be aware that if you ever turn on Android 16 **Advanced Protection**, non-tool accessibility services get disabled.
5. **takeScreenshot()** is rate-limited (about 1 per second per display on many builds). The vision layer must cache and not spam it.
6. **Service killed by OEM:** detect when the accessibility service is not connected and show a clear "Re-enable" prompt; with Shizuku you can auto re-enable via `settings put secure enabled_accessibility_services`.

The app shows a **Capability Report** screen: green/yellow/red for each feature based on what is actually granted and supported on this device.

---

## 7. Floating overlay (`:feature:overlay`)

### 7.1 Windows
- **Bubble window:** 56 dp circle, draggable, snaps to the nearest side edge with a spring animation, stays half-hidden at the edge when idle (optional), avoids the status bar, navigation bar and display cutout (use `WindowInsets`).
- **Panel window:** expands from the bubble; ~85% width, max 50% height; can be dragged to top or bottom. Must be focusable only while the user types in it (otherwise `FLAG_NOT_FOCUSABLE`, so the app underneath keeps keyboard focus).
- **Status pill (new):** a slim pill near the top that shows the current step ("Tapping *Search*…") while the panel is minimized. Least obstruction possible.
- **Target highlight (new):** before each tap, a short pulse ring is drawn on the target element so you can see what it is about to do.
- **Debug layer (dev only):** draws numbered boxes around all detected elements.

Compose inside `WindowManager` needs a `LifecycleOwner`, `ViewModelStoreOwner` and `SavedStateRegistryOwner` attached to the `ComposeView` — build one small `OverlayLifecycleOwner` helper for this.

### 7.2 States (single state machine, shared by overlay, notification and agent)

```
Idle ──tap──► Expanded ──mic──► Listening ──end of speech──► Processing
  ▲                                ▲                              │
  │                                │                       ┌──────┴──────┐
  │                             Waiting ◄──── done ─── Executing ◄── Observing
  │                                │                       │
  └──── "Done"/Stop ──── Stopped ◄─┘        any failure ──► Error ──► Waiting
                                    Confirming (needs your yes/no) ◄── SafetyGuard
                                    Paused (needs you: login, CAPTCHA, secure screen)
```

Added states: **Confirming** and **Paused (Handoff)**.

### 7.3 Panel content
- Conversation list (your words, assistant replies, action cards with ✓ / ✗).
- Live transcript line with waveform while listening.
- Current app icon + name, current step, step counter ("3 / 6").
- Big **Stop** button, mic toggle, minimize, "type instead" text box.
- Confirmation card (Yes / No buttons + voice "yes"/"no").

### 7.4 Design
Dark premium theme (near-black glass surface, one accent color), rounded 24 dp corners, 150–250 ms transitions, animated gradient ring on the bubble that changes per state (blue listening, purple thinking, amber executing, green done, red error). Respect "reduce motion".

---

## 8. Voice engine (`:feature:voice`)

### 8.1 Pipeline

```
AudioRecord (16 kHz mono, source VOICE_COMMUNICATION → enables echo cancel / noise suppression)
   │
   ├──► Silero VAD (speech start / speech end, end-of-speech silence ≈ 700 ms, adaptive)
   │
   ├──► Streaming ASR (sherpa-onnx Zipformer, English) ──► live partial text in the panel
   │         │
   │         └─ on speech end ──► final text  (optional 2nd pass: Whisper-small/Moonshine
   │                                            on the buffered audio for better accuracy)
   │
   └──► Keyword Spotter (sherpa-onnx KWS): "stop", "cancel", "wait", "done", optional wake word
```

- **One continuous audio stream** for the whole session. No restarting recognizers per phrase (this fixes the classic "Android SpeechRecognizer stops after each sentence" problem).
- **Half-duplex with barge-in:** while TTS is speaking, ASR is muted but KWS stays on, so "stop" still works. While the agent is executing, ASR keeps buffering: you can say the next command early, it is queued.
- **Hotwords / contextual biasing:** feed app names and on-screen labels to the recognizer as hotwords (sherpa-onnx supports this for transducer models) so "open Swiggy" or "tap Shorts" are recognized correctly.
- **Session end:** "done", "stop listening", "that's all", Stop button, notification Stop action, screen off, or N minutes of silence (configurable, default 3 min) → Stopped.
- **Optional wake word (opt-in, off by default):** "Hey Omni" via KWS while the bubble is visible. Only active when you turn it on, and the mic indicator is always visible.
- **Languages:** start with English; design so a Hindi/multilingual model can be added later (sherpa-onnx has multilingual models; Whisper handles many languages).
- **Fallback engine:** Android `SpeechRecognizer` with `EXTRA_PREFER_OFFLINE`, marked "may use network" in settings.

### 8.2 Text-to-speech
- Android `TextToSpeech` with an offline voice (check `Voice.isNetworkConnectionRequired == false`).
- Optional: high-quality offline voices via sherpa-onnx (Piper / Kokoro models).
- Replies are short ("Opened YouTube"). A "silent mode" shows text only.

### 8.3 Voice session loop (pseudo-code)

```kotlin
while (session.active) {
    val utterance = voice.nextUtterance()            // suspends until VAD end-of-speech
    if (StopWords.match(utterance)) { session.end(); break }
    agent.submit(utterance)                         // non-blocking; agent has its own queue
}
// KWS runs in parallel: on "stop"/"cancel" -> agent.cancelCurrentTask()
```

---

## 9. Screen understanding (`:feature:accessibility` + `:feature:vision`)

### 9.1 Observation object
Every time the agent "looks", it builds one `Observation`:

```kotlin
data class Observation(
    val packageName: String, val activityHint: String?, val appLabel: String,
    val elements: List<UiElement>,        // merged list, each with stable short ID
    val screenFingerprint: String,         // hash of structure, used for graph + change detection
    val keyboardVisible: Boolean, val focusedElementId: Int?,
    val dialogs: List<Int>,                // IDs of elements in dialog windows
    val screenshot: Bitmap?,               // only when requested
    val isSecureWindow: Boolean,           // FLAG_SECURE / empty tree detection
    val timestamp: Long
)

data class UiElement(
    val id: Int, val role: Role,           // BUTTON, TEXT_FIELD, TAB, LIST_ITEM, IMAGE, CHECKBOX, SWITCH, LINK, TEXT...
    val text: String?, val description: String?, val hint: String?, val viewId: String?,
    val bounds: Rect, val clickable: Boolean, val longClickable: Boolean,
    val scrollable: Boolean, val editable: Boolean, val checked: Boolean?, val selected: Boolean?,
    val enabled: Boolean, val isPassword: Boolean,
    val source: Source,                    // TREE, OCR, VISION
    val parentId: Int?, val containerId: Int?   // e.g. which list / card it belongs to
)
```

### 9.2 Layers (cheapest first)
1. **Accessibility tree** (always): walk all interactive windows (`getWindows()`), keep visible, meaningful nodes; collapse wrappers; attach child text to clickable parents ("card = thumbnail + title + channel"); assign roles from class names + actions.
2. **OCR** (when tree text is missing — games, Flutter/Unity apps, canvases, images with text): ML Kit on the screenshot; OCR boxes that don't overlap a tree element become new `UiElement(source=OCR)`.
3. **Visual matcher** (when the request is about appearance — "car in thumbnail", "red button", "blue icon"):
   - Take image-like elements (thumbnails, image views, cards) from the tree; crop each from the screenshot.
   - Embed crops with a small image-text model (SigLIP / MobileCLIP-class, ONNX, ~100–200 MB) and compare with the text query ("a car").
   - Colors ("red button") are handled with simple color statistics on the crop — no ML needed.
   - Return the best match with a confidence score; if low confidence or a tie → ask you, showing numbered highlights.
4. **Small VLM** (last resort, on demand): render a **Set-of-Marks** screenshot (numbered boxes drawn on top), send it + the question to a small vision model (e.g. Qwen-VL / SmolVLM / Gemma 3n class, picked in Phase 0), answer is an element number. Or send to Home Brain if enabled.

### 9.3 What the LLM actually sees (compact text, ~300–1,200 tokens)

```
APP: YouTube (com.google.android.youtube)  SCREEN: search results
KEYBOARD: hidden   FOCUSED: none
[1] button "Back"
[2] textfield "swift programming tutorials"
[3] list (scrollable ↓)
  [4] card "Swift Tutorial for Beginners – 3 hours" · "CodeWithChris" · 1.2M views
  [5] card "Learn Swift in 1 hour" · "Programming with Mosh"
  [6] card "SwiftUI Car App UI" · "Kavsoft"
[7] tab "Home"  [8] tab "Shorts"  [9] tab "Subscriptions"  [10] tab "You"
```

Long lists are trimmed with "… 14 more below (scroll)". Same element keeps the same ID while the screen doesn't change.

### 9.4 Secure / empty screens
If the tree is empty and the screenshot is black → `isSecureWindow = true` → agent enters **Paused (Handoff)**: "This screen is protected. Please do this step yourself and say *continue*."

---

## 10. Action engine (`:feature:accessibility`)

### 10.1 Action protocol (the only thing the AI can output)

```json
{ "thought": "The search box is element 2.",
  "action": { "type": "type_text", "target": 2, "text": "swift tutorials", "submit": true },
  "expect": { "kind": "screen_changes" },
  "say": "Searching for Swift tutorials." }
```

Allowed `type` values (closed list, validated):

| Type | Fields | Implementation |
|---|---|---|
| `open_app` | `app` (name) | AppLauncher fuzzy match → launch intent |
| `open_url` | `url` | `ACTION_VIEW` intent (only http/https) |
| `click` | `target` | `ACTION_CLICK` on node or nearest clickable parent; fallback: gesture tap at center |
| `long_press` | `target` | `ACTION_LONG_CLICK`; fallback: 600 ms gesture |
| `type_text` | `target`, `text`, `submit?` | focus → `ACTION_SET_TEXT`; `submit` → IME action (`ACTION_IME_ENTER`, API 30+) |
| `clear_text` | `target` | `ACTION_SET_TEXT ""` |
| `scroll` | `target?`, `direction`, `amount?` | `ACTION_SCROLL_FORWARD/BACKWARD` or `ACTION_SCROLL_DOWN/UP/LEFT/RIGHT`; fallback swipe gesture inside container bounds |
| `swipe` | `from`, `to` (relative 0–1), `durationMs` | `dispatchGesture` |
| `back` / `home` / `recents` | — | `performGlobalAction` |
| `notifications` / `quick_settings` | — | global actions |
| `expand` / `collapse` | `target` | `ACTION_EXPAND/COLLAPSE` |
| `select` | `target` | for checkboxes/switches/dropdown items |
| `wait` | `ms` (max 5000) or `until` | wait for screen to settle |
| `find` | `query`, `maxScrolls` | scroll a list until an element matching query appears |
| `read_screen` | `question` | returns information to the user (summarize, read message, extract URL) |
| `ask_user` | `question` | clarification |
| `done` | `summary` | task finished |

There is **no** shell, code, or free-form intent action. Ever.

### 10.2 Executor rules
- Validate: target ID exists in the **current** observation, is visible, enabled, and not a password field. Text length limits. URL scheme whitelist.
- Before acting: make overlay non-touchable if it overlaps target; show highlight pulse.
- After acting: wait for **screen settle** (no `TYPE_WINDOW_CONTENT_CHANGED` events for ~300 ms, max 3 s), then re-observe.
- Every action has a timeout and returns `ActionResult(success, reason, newObservation)`.

### 10.3 Verifier
- `screen_changes`: fingerprint differs.
- `element_appears(query)` / `text_visible(query)`.
- `app_is(package)`.
- `field_contains(id, text)`.
- If verification fails: retry once with the fallback method (node action ↔ gesture), then re-plan, then ask you.

### 10.4 Robustness
- **Stuck detection:** same fingerprint after 2 actions → change strategy.
- **Loop detection:** same (screen, action) pair 3 times → stop and ask.
- **Step budget:** max 15 steps per command (configurable), max 60 s per command.
- **Popups:** a small "popup handler" recognizes common interruptions (permission dialogs, "Rate this app", cookie banners, ads with "Skip") and either dismisses safe ones or asks you.

### 10.5 Clipboard & copying (Android limits)
- Copy: prefer **reading the text directly from the screen** (the URL in a message bubble is in the tree). Store it in the agent's own "session clipboard". If the user really wants the system clipboard, OmniControl sets it (setting is allowed; reading in background is not).
- "Open the link I copied": use the session clipboard; otherwise briefly bring the overlay panel into focus (allowed to read the clipboard while focused) after asking you.

---

## 11. AI brain (`:feature:agent` + `:feature:llm`)

### 11.1 Two-speed design

```
utterance ─► Normalizer ─► FastRouter ──match──► direct Action(s)       (≈ 50–300 ms)
                              │
                              └─no match─► LLM Planner (ReAct loop)     (≈ 1–6 s per step)
```

**FastRouter** (rules + fuzzy): "open/launch/start X", "go back", "go home", "scroll up/down", "next/previous", "tap/click <label>" (when label matches exactly one element), "type <text>", "search for <x>" (if a visible search field exists), "first/second/third result", "done/stop", "yes/no" (in Confirming), "continue" (in Paused). This covers maybe 50–70% of daily commands with zero AI latency.

### 11.2 LLM agent loop (ReAct: Observe → Think → Act → Verify)

```
goal = utterance + conversation context
repeat until done / ask_user / budget:
    obs  = observe()                            # tree (+ OCR/vision if needed)
    plan = llm(system_prompt, context, goal, obs, last_results)   # grammar-constrained JSON
    act  = validate(plan.action)                # schema + safety
    if risky(act): confirm with user
    result = execute(act); verify(result, plan.expect)
    context.append(step summary)
```

- **Grammar-constrained output:** llama.cpp JSON-schema/GBNF so the model can only emit valid actions. No parsing failures.
- **Prompt budget:** system prompt (~600 tokens, action list + rules + 3 short examples) + rolling context (~800) + observation (~1,000). Keep total < 4k tokens for speed. Use **KV-cache reuse** for the fixed system prompt (llama.cpp prompt caching) → much faster each step.
- **Sub-goal planning for long commands:** "Open WhatsApp, find the latest link, open it in Chrome" → LLM first writes a short checklist (3–5 sub-goals), then runs the ReAct loop per sub-goal. Checklist is shown in the panel.

### 11.3 Conversation context (ConversationManager)
Keeps a structured **session memory**, not just raw chat:
- `turns`: last N user/assistant messages (summarized when long).
- `appStack`: apps used in this session and their last screens.
- `lastResults`: last list the user saw (e.g. search results with their element summaries), so "the one with the blue thumbnail" or "go back and open the third one" resolves correctly.
- `entities`: things mentioned ("the link", "that video", "my order") → resolved references.
- `pending`: queued commands spoken while executing.
- `preferences`: session-only preferences ("always use Chrome", "speak less").

### 11.4 Model selection (do this in Phase 0, don't hardcode)
Benchmark on your phone with a small test set (≈40 recorded screens + commands from §18). Measure: valid-action rate, correct-target rate, tokens/sec, RAM, load time, battery.

Candidates to evaluate (all free; check the latest versions at build time):
- **Text LLM (llama.cpp, GGUF Q4_K_M):** Qwen3 1.7B / 4B (Apache 2.0), Qwen2.5 1.5B/3B Instruct (Apache 2.0), Llama 3.2 1B/3B (Llama community license), Phi-4-mini (MIT), Gemma 3 1B/4B (Gemma terms).
- **On-device NPU/GPU path (LiteRT-LM):** Gemma 3n E2B/E4B (also accepts images → can double as the VLM).
- **VLM:** SmolVLM (Apache 2.0), Qwen-VL small (2–3B), Gemma 3n.
- **Image–text matcher:** SigLIP base / MobileCLIP-class (ONNX).
Pick the smallest model that reaches ≥ 90% valid actions and ≥ 75% correct target on the test set. Run "thinking" modes **off** for speed.

### 11.5 Optional Home Brain (free, private, LAN only)
- Settings → "Use my PC as brain": IP + port of an Ollama or `llama-server` running on your Mac/PC (e.g. Qwen3 8B/14B, or a VLM like Qwen-VL 7B).
- Same `LlmEngine` interface, same JSON schema.
- Only used when reachable on local Wi-Fi; data never goes to the internet. Clear "🏠 Home Brain" badge in the panel when in use.
- Automatic fallback to on-device when PC is off.

### 11.6 Prompt rules (system prompt summary)
- You control an Android phone through the listed actions only. Answer with one JSON action.
- Use element IDs from the current screen. Never invent IDs.
- Prefer the most direct path. If unsure, `ask_user`.
- Never type passwords/OTPs; never confirm payments/sends without the `confirm` flow.
- If the screen is protected or needs login → `ask_user` with a handoff message.

---

## 12. Universal navigator (`:feature:navigator`)

### 12.1 Strategy order
1. **Known Routine** (user-taught or learned) whose start screen matches.
2. **Deep link / intent** from a small built-in table (YouTube search URL, Maps query, Play Store page, `tel:`, `mailto:`, `https://`, Settings screens like `Settings.ACTION_WIFI_SETTINGS`).
3. **Screen graph route**: BFS/Dijkstra from current screen node to a node that contains the goal label.
4. **Exploration** with the LLM: look for likely entry points ("Profile", "Account", "You", avatar icon, ☰ menu, "More"), try, verify, record.
5. **Vision** if labels are missing.

### 12.2 Screen graph (per app)
- **Node** = screen fingerprint: package + activity hint + normalized set of stable labels / view IDs (ignores dynamic content like video titles, counts, times).
- **Edge** = action with an element **signature** (viewId + role + text/description pattern + relative position), not coordinates.
- Every executed step is recorded as an edge. Success count, failure count, last-used, app version code stored.
- Before reusing an edge, check the signature still matches an element on the current screen. If not → mark stale → rediscover.
- On app update (versionCode change) → edges are kept but marked "unverified" until they work again.

### 12.3 Routines and Teach mode (new)
- **Learned routines:** when a multi-step command succeeds, offer "Save as routine?" ("Show my orders" → Amazon: Home → You → Your Orders).
- **Teach mode:** say "Watch me". OmniControl records *your* taps (via accessibility events) as semantic steps, then asks for a name. Next time: "show my orders" runs it.
- **Routines with parameters:** "Search YouTube for {query}".
- **Aliases:** "my shopping app" → Amazon, "the bank app" → (refused/handoff), "mom" → contact name in WhatsApp.
- Routine manager screen: list, rename, test-run, delete, export/import as JSON file.

---

## 13. Cross-app workflows

Handled by the same agent loop + sub-goal checklist + session clipboard. Example traces the system must support (also used as tests):

1. **WhatsApp → Chrome:** open WhatsApp → open most recent chat → find latest message with URL (regex on tree text) → store in session clipboard → `open_url` in Chrome → verify Chrome shows the domain.
2. **Chrome summarize:** open Chrome → search → open first result → `read_screen` (collect page text from tree, scroll 2–3 times) → LLM summary → speak 2–3 sentences.
3. **YouTube subscribe:** search → open 2nd result → tap channel name → tap "Subscribe" → **Confirm** (publishing-like action, configurable) → verify button says "Subscribed".
4. **Files:** open file manager → sort by date / Recent → find first `.pdf` → open.
5. **Shopping orders:** routine or graph route → open first order.

---

## 14. Safety & privacy (`SafetyGuard`)

### 14.1 Risk levels
| Level | Examples | Behaviour |
|---|---|---|
| Safe | open app, scroll, back, search, read | Run directly |
| Confirm | send message, post/comment/subscribe, delete, call, change settings, install/uninstall, share | Panel shows "About to tap **Send** to *Mom*. OK?" — voice "yes" or tap. Times out → cancel |
| Handoff | payment, purchase, bank apps, password/OTP fields, biometrics, CAPTCHA, login screens, secure windows | Agent stops; you do it; say "continue" |
| Blocked | anything in OmniControl's own settings or accessibility settings, factory reset, disabling security | Never done by the agent |

Risk detection = target label keywords (send, pay, buy, place order, delete, remove, post, publish, transfer, confirm, uninstall…) + `isPassword` + package categories (banking/payment list you can edit) + action type. Configurable per app.

### 14.2 Kill switches
- Say "stop" / "cancel" (KWS, always on during session).
- Big Stop button in panel; Stop action in notification.
- **Panic keys:** press Volume Up + Volume Down together → immediate stop of all actions (accessibility key filter).
- Screen off → session ends.

### 14.3 Privacy
- Offline by default; a global **Offline mode** switch disables all network code paths (model downloads and Home Brain included).
- No analytics, no crash reporting services.
- Screenshots live in memory only; traces (for debugging) are saved only if you enable "Record traces", stored in app-private storage, auto-deleted after 7 days, with a "Delete all" button.
- Room database stays in app-private storage; `android:allowBackup="false"`.
- Mic indicator: Android's own green dot + our bubble ring + persistent notification.

---

## 15. Storage (`:core:database`, Room)

| Table | Key fields |
|---|---|
| `sessions` | id, startedAt, endedAt, commandCount |
| `messages` | id, sessionId, role, text, createdAt |
| `action_log` | id, sessionId, type, targetSignature, success, durationMs, error |
| `screens` | id, package, fingerprint, labelsJson, appVersionCode, lastSeen |
| `edges` | id, fromScreenId, toScreenId, actionJson, elementSignatureJson, successCount, failCount, lastUsed, verified |
| `routines` | id, name, triggerPhrasesJson, package, stepsJson, paramsJson, createdBy(user/learned) |
| `aliases` | phrase, kind(app/contact/place), value |
| `app_policies` | package, riskLevelOverride, allowAgent(bool) |
| `traces` (optional) | id, sessionId, stepIndex, obsJson, promptText, outputJson, screenshotPath |

Settings (thresholds, voice, models, Home Brain address) in DataStore. Models stored in app-private files dir; a **Model Manager** screen downloads them once (with SHA-256 checks) or imports from a file you `adb push`.

---

## 16. Build plan — phases with acceptance tests

Each phase ends with: build passes, tests pass, installed on your phone, acceptance tests done by you, and a short `docs/phases/PHASE_X_REPORT.md` (files created/changed, dependencies, permissions, how to test, known limits).

### Phase 0 — Environment & feasibility spikes (2–4 days)
Tasks
- Check Mac tooling: Android Studio, SDK 36, NDK, CMake, JDK, `adb devices`, phone model/RAM/SoC/Android version (`adb shell getprop`).
- Create empty Kotlin + Compose project with the module layout from §5.1 (modules can be empty).
- **Spike A:** minimal accessibility service that logs the tree of the current app and takes one screenshot.
- **Spike B:** sherpa-onnx streaming ASR demo with VAD running for 5 minutes continuously; measure latency + CPU.
- **Spike C:** llama.cpp JNI running 2 candidate models; measure tokens/sec, RAM, and JSON-grammar output.
- **Spike D:** start microphone FGS from an overlay tap while another app is in front (verify Android 15/16 rules on your device).
- Write `docs/ARCHITECTURE.md`, `docs/MODELS.md` (candidates, sizes, licenses, benchmark numbers), `docs/CAPABILITIES.md`.

Acceptance: all 4 spikes work on your phone; model choice written down with numbers.

### Phase 1 — App shell, permissions, settings (3–4 days)
- Compose app: Dashboard (start/stop, status), Permissions wizard (overlay → restricted settings guide → accessibility → mic → notifications → battery), Capability Report, Settings, History (empty), Model Manager (UI only).
- Hilt, DataStore, Room skeleton, logging.
Acceptance: fresh install → wizard brings every permission to green; Capability Report correct when you revoke one.

### Phase 2 — Floating overlay (4–5 days)
- Overlay service, bubble (drag, snap, insets), panel (expand/minimize), status pill, state machine, notification with Stop.
- Overlay works over YouTube, Chrome, WhatsApp, Settings; survives rotation and app switching.
Acceptance: drag/snap smooth; panel keeps conversation while switching apps; Stop works from panel and notification; no keyboard focus stolen.

### Phase 3 — Accessibility screen reading + action engine + manual console (6–8 days)
- ScreenReader → `Observation` with merged element list and fingerprint; filter own windows.
- ActionExecutor for every action in §10.1, settle detection, verifier, fallbacks.
- **Manual Test Console** (in debug panel): shows the numbered element list for the current screen; you type `click 5`, `type 2 hello`, `scroll down`, `back`… and see results. Debug box overlay.
- Unit tests using saved tree dumps (JSON) from real apps.
Acceptance: using only the console you can: open YouTube, search, open 2nd result, go to channel, go back; type in Chrome's address bar; scroll Settings; toggle Wi-Fi tile in quick settings.

### Phase 4 — Voice engine (5–7 days)
- AudioRecord pipeline, Silero VAD, streaming Zipformer ASR, KWS (stop/cancel/done), session loop, TTS, hotword biasing with app names.
- Connect to a temporary command echo + FastRouter (only rule-based commands for now).
Acceptance: one session, 10 consecutive spoken commands without touching the phone ("open YouTube", "scroll down", "go back", "open Chrome", "go home", …, "done"); "stop" interrupts within 0.5 s; works offline in airplane mode.

### Phase 5 — AI agent (8–12 days)
- `LlmEngine` (llama.cpp backend), grammar-constrained JSON, prompt builder, observation serializer, ConversationManager, ReAct loop, sub-goal checklist, SafetyGuard (confirm/handoff), error handling, budgets.
- Optional: Home Brain backend.
- Offline evaluation harness: run the agent against recorded observations (§18) on the Mac/phone and print scores.
Acceptance: the "YouTube conversation" from §1 of the original idea works end-to-end by voice, except visual-only requests; evaluation scores recorded in `docs/MODELS.md`; send-message action always asks for confirmation.

### Phase 6 — Visual understanding (6–8 days)
- Screenshot manager (rate limit, own-window hiding), OCR merge, color analysis, CLIP-style matcher, Set-of-Marks renderer, on-demand VLM, confidence + "which one?" clarification with numbered highlights.
Acceptance: "open the video with a car in the thumbnail" picks correctly in ≥ 4 of 5 real tries; "tap the red button" works on a test screen; text inside images found via OCR.

### Phase 7 — Navigator, memory, routines, cross-app (7–10 days)
- Screen graph recording, route finding, stale-edge detection, deep-link table, Routines + Teach mode + aliases, session clipboard, `find` action, popup handler.
- All 5 workflows in §13.
Acceptance: second time "show my orders" is ≥ 2× faster than the first; after you rename/move something (or app update), it rediscovers; Teach mode recording replays correctly; all 5 workflows pass.

### Phase 8 — Hardening, performance, polish (ongoing, 5–7 days)
- Test: incoming call during session, headphones/Bluetooth mic, screen lock, airplane mode, low battery, app killed by OEM, rotation, dark/light apps, Android 15 vs 16.
- Performance: model load once and keep warm while session active; unload after idle; measure battery per 10-minute session; Macrobenchmark for overlay jank.
- Privacy audit: no network calls in offline mode (verify with `adb shell dumpsys netstats` / a local proxy); traces cleanup.
- Docs: README, SETUP.md, TESTING.md, CAPABILITIES.md final.
Acceptance: 30-minute mixed session without crash; battery and RAM numbers documented.

### Phase 9 — Extras (pick any later)
- Optional Shizuku mode (faster input, `uiautomator dump` for apps with poor accessibility, auto re-enable service).
- Quick Settings tile + home-screen widget to start a session.
- Multilingual voice (Hindi/Telugu models).
- "Explain this screen" / "read this aloud" mode.
- Scheduled routines ("every morning at 8 open news and read headlines") — still requires phone unlocked.
- Notification reader ("read my latest WhatsApp message").
- Export/import routines and graph as a backup file.

---

## 17. Prompts for Antigravity (paste one at a time)

Use this **base rule block** at the top of every prompt:

```
You are building OmniControl, a personal Android app. Read OMNICONTROL_PLAN.md fully before coding.
Rules:
- Kotlin + Jetpack Compose only. No React Native. minSdk 30, targetSdk 36.
- Follow the module layout in section 5.1 and the interfaces in sections 9–11.
- Only free, open-source or free on-device libraries. Ask me before adding any dependency not listed in section 5.2.
- No placeholder or fake implementations reported as done. If something is stubbed, say so clearly.
- Never add network calls except model download and optional Home Brain.
- Keep heavy work off the main thread (coroutines, Dispatchers.Default/IO).
- After coding: build with ./gradlew assembleDebug, run unit tests, give me exact adb commands to install and test.
- End with a report: files created, files changed, dependencies added, permissions, how to test, known limits.
```

**Phase 0:** "Do Phase 0 from section 16. Inspect my Mac environment and connected phone first and show me the results. Then create the empty multi-module project and the four spikes (A–D). Do not build real features yet. Write ARCHITECTURE.md, MODELS.md and CAPABILITIES.md with real measured numbers."

**Phase 1:** "Do Phase 1 from section 16 using sections 6 and 15. Build the permissions wizard including the 'Allow restricted settings' guide for sideloaded apps, and the Capability Report."

**Phase 2:** "Do Phase 2 using section 7. Overlay must use TYPE_APPLICATION_OVERLAY with a ComposeView and a proper lifecycle owner. Implement the full state machine from 7.2 as a single StateFlow shared with the notification."

**Phase 3:** "Do Phase 3 using sections 9.1, 9.3, 10. Build the Manual Test Console first, then the executor and verifier. Add unit tests using saved JSON tree dumps."

**Phase 4:** "Do Phase 4 using section 8. Use sherpa-onnx with Silero VAD, a streaming Zipformer English model and keyword spotting. One continuous AudioRecord stream for the whole session. Must work in airplane mode."

**Phase 5:** "Do Phase 5 using section 11 and 14. Implement LlmEngine with llama.cpp via JNI, grammar-constrained JSON output matching the action schema in 10.1, the FastRouter, the ReAct loop, ConversationManager and SafetyGuard. Build the offline evaluation harness."

**Phase 6:** "Do Phase 6 using section 9.2. Implement OCR merge, color analysis, the image-text matcher on cropped thumbnails, Set-of-Marks rendering and on-demand VLM, with confidence-based clarification."

**Phase 7:** "Do Phase 7 using sections 12 and 13. Implement the screen graph, routines, teach mode, aliases, session clipboard, popup handler, and make all five workflows in section 13 pass."

**Phase 8:** "Do Phase 8 using section 16. Run the stability checklist, measure battery/RAM, verify no network traffic in offline mode, and finalize README, SETUP, TESTING and CAPABILITIES docs."

---

## 18. Testing strategy

- **Unit tests:** router rules, fuzzy app matching, action validation, safety classifier, fingerprinting, graph routing, JSON schema.
- **Snapshot tests on real screens:** save `Observation` JSON (+ screenshot) from ~40 real screens (YouTube home/search/video/channel, Chrome, WhatsApp chat list/chat, Settings, Files, Amazon/Flipkart home/account/orders, Instagram profile). Commit them to `testdata/screens/`.
- **Agent eval set:** `testdata/eval.jsonl` — each line: screen file + conversation context + command + expected action (type + target). Script prints valid-action %, correct-target %, avg latency. Run after every prompt/model change.
- **Device scenario tests:** the 5 workflows + the YouTube conversation, run manually with traces on; failures turn into new eval cases.
- **Trace viewer** (debug screen): step-by-step view of what the agent saw, the prompt, the JSON it produced and the result. This is the most important debugging tool for an agent — build it early (Phase 3/5).

---

## 19. Performance targets

| Metric | Target |
|---|---|
| Fast-path command (open app / back / scroll) | < 300 ms after end of speech |
| End-of-speech detection | ≤ 0.8 s of silence |
| Live transcript delay | < 300 ms |
| LLM step (on-device, 1.5–4B Q4) | 1–5 s |
| Observation build (tree only) | < 150 ms |
| CLIP match on 10 thumbnails | < 500 ms |
| "Stop" reaction | < 500 ms |
| Overlay frame time | no jank (Macrobenchmark) |
| Idle (bubble only, no session) | near-zero CPU, models unloaded |

---

## 20. Risks & mitigations

| Risk | Mitigation |
|---|---|
| Small on-device LLM makes wrong choices | Fast path for common commands, grammar-constrained output, indexed elements, verification + retry, eval set to pick the best model, optional Home Brain |
| Apps with poor accessibility (Flutter/games/custom views) | OCR + vision layer; Shizuku `uiautomator dump` as alternative; gestures as last resort |
| OEM kills services (Xiaomi, Oppo, Samsung battery) | Battery exemption guide, autostart guide, reconnect detection, Shizuku auto re-enable |
| Mic FGS start blocked from background | Start only from user tap; trampoline activity fallback (tested in Phase 0) |
| Heat/battery from models | Load on session start, unload after idle, small quantized models, GPU/NPU via LiteRT where available |
| Overlay intercepting the agent's own taps | Non-touchable flag / move bubble before gestures; filter own windows |
| Accidental harmful actions | SafetyGuard levels, confirmations, handoff, panic keys, blocked list |
| Model licenses | `docs/MODELS.md` license check before integration; Apache/MIT preferred |
| Scope creep | Strict phase gates with acceptance tests |

---

## 21. Repository layout (final)

```
OmniControl/
├── app/
├── core/{model,common,database}/
├── feature/{overlay,voice,accessibility,vision,llm,agent,navigator,permissions,shizuku,debug}/
├── native/llama/                 # llama.cpp as git submodule + CMake + JNI
├── testdata/{screens,eval.jsonl}
├── tools/                        # eval runner script, model download/push scripts
├── docs/
│   ├── OMNICONTROL_PLAN.md       # this file
│   ├── ARCHITECTURE.md
│   ├── MODELS.md
│   ├── CAPABILITIES.md
│   ├── SETUP.md  ·  TESTING.md
│   └── phases/PHASE_X_REPORT.md
└── README.md
```

### Handy commands
```bash
adb devices
adb shell getprop ro.build.version.release        # Android version
adb shell getprop ro.product.model
adb shell cat /proc/meminfo | head -1               # RAM
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb push qwen-model.gguf /sdcard/Download/          # then import in Model Manager
adb shell settings get secure enabled_accessibility_services
adb logcat -s OmniControl
```

---

## 22. Definition of "done" for v1

You can tap the bubble and, in one hands-free session, say:

> "Open YouTube." → "Search for Swift tutorials." → "Open the video with the blue thumbnail." → "Go to the channel." → "Subscribe." *(it asks: "Subscribe to X?" → "Yes")* → "Now open Chrome." → "Search for the latest Swift documentation." → "Open the first result." → "Summarize it." → "Done."

…and it completes every step correctly, offline (or with your Home Brain), shows each step in the panel, stops instantly when you say "stop", and the second time you ask for a familiar place, it gets there faster.
