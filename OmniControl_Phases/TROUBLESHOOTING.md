# Troubleshooting — Known Errors and Exact Fixes

Antigravity: when you hit an error, search this file for a keyword from the error message **before** trying random fixes. Numbers are referenced from the phase files.

---

## Build & Gradle

### #1 — SDK / NDK / CMake not found
**Errors:** `SDK location not found`, `NDK not configured`, `CMake '3.22.1' was not found`, `No version of NDK matched the requested version`.
**Fix:**
1. `local.properties` must contain `sdk.dir=/Users/<user>/Library/Android/sdk` (Android Studio writes this; don't commit it).
2. Install NDK r28+ and CMake 3.22.1+ via SDK Manager → SDK Tools (tick "Show Package Details" to choose versions).
3. `ndkVersion` in `native/llama/build.gradle.kts` must match a folder name in `$ANDROID_HOME/ndk/` exactly.
4. CMake `version = "3.22.1"` in Gradle must match an installed folder in `$ANDROID_HOME/cmake/` (or remove the version line to use the default).

### #1b — JDK mismatch
**Errors:** `Unsupported class file major version`, `Android Gradle plugin requires Java 17 to run`.
**Fix:** Android Studio → Settings → Build Tools → Gradle → Gradle JDK = "jbr-17" (or bundled JBR). For terminal builds: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"`.

### #2 — KSP / Hilt / Kotlin version mismatch
**Errors:** `ksp-<x> is too old for kotlin-<y>`, `[Hilt] ... was not processed`, `Unresolved reference: Hilt_...`, `@HiltAndroidApp missing`.
**Fix:**
- KSP version prefix must equal the Kotlin version (`2.x.y-1.0.z` style or the newer KSP2 versioning — check the KSP GitHub releases for the matching one).
- Apply `com.google.devtools.ksp` and `com.google.dagger.hilt.android` plugins in **every** module that has `@Inject`/`@HiltViewModel`/`@AndroidEntryPoint`, and add `ksp(libs.hilt.compiler)` there.
- Never mix `kapt` and `ksp` for Hilt.
- Application class must be `@HiltAndroidApp` and registered in the manifest.
- `@AndroidEntryPoint` services must extend a supported base (`Service`, `LifecycleService` are fine; `AccessibilityService` is a `Service` → OK).

### #3 — Room schema export warning / error
**Error:** `Schema export directory was not provided to the annotation processor`.
**Fix:** in `core/database/build.gradle.kts`: `ksp { arg("room.schemaLocation", "$projectDir/schemas") }` (or use the Room Gradle plugin `room { schemaDirectory("$projectDir/schemas") }`). Commit the `schemas/` folder.

### #6 — Local AAR in a library module
**Error:** `Direct local .aar file dependencies are not supported when building an AAR. The resulting AAR would be broken because the classes and Android resources from any local .aar file dependencies would not be packaged.`
**Fix:** wrap the AAR in its own module:
```kotlin
// third_party/sherpa-onnx/build.gradle.kts  (no plugins block)
configurations.maybeCreate("default")
artifacts.add("default", file("sherpa-onnx.aar"))
```
`settings.gradle.kts`: `include(":third_party:sherpa-onnx")`. Consumers: `implementation(project(":third_party:sherpa-onnx"))`.
Also build with `./gradlew :app:assembleDebug` (not plain `assembleDebug`).
If it still fails: use the source approach (copy sherpa-onnx `kotlin-api` `.kt` files of the same version + `jniLibs/arm64-v8a/*.so` into `:feature:voice`).

### #7 — llama.cpp CMake build fails
**Typical errors & fixes:**
- `add_subdirectory given source "llama.cpp" which is not an existing directory` → run `git submodule update --init --recursive`.
- C++ API errors in your JNI file (`llama_load_model_from_file` deprecated / `no member named ...`) → your JNI code doesn't match the pinned tag. Open `examples/llama.android` and `examples/simple/simple.cpp` **in the pinned submodule** and follow their API usage exactly.
- `target "common" not found` → in that version `common` may require `LLAMA_BUILD_COMMON=ON`; add `-DLLAMA_BUILD_COMMON=ON` or don't link `common`.
- Very slow build → ensure only `arm64-v8a` ABI; first build takes several minutes, that's normal.
- `OpenMP` link errors → `-DGGML_OPENMP=OFF`.
- Unsupported CPU instruction crashes on older phones (`SIGILL`) → don't force `-march` flags; let ggml detect, or set `-DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod` only if the phone supports it (check `adb shell cat /proc/cpuinfo | grep Features` for `asimddp`).

### #8 — libcurl not found (llama.cpp)
**Error:** `Could NOT find CURL` / `LLAMA_CURL`.
**Fix:** add `-DLLAMA_CURL=OFF` to CMake arguments. We never download from native code.

### #9 — Duplicate native libraries
**Error:** `2 files found with path 'lib/arm64-v8a/libonnxruntime.so'` (or `libc++_shared.so`).
**Fix:**
```kotlin
// app/build.gradle.kts
android { packaging { jniLibs {
    pickFirsts += listOf("**/libc++_shared.so", "**/libonnxruntime.so")
} } }
```
⚠️ For `libonnxruntime.so`, `pickFirst` is only safe if both copies are the **same onnxruntime version**. Use the `onnxruntime-android` version that the sherpa-onnx release was built with (see its release notes / CMake), or avoid the second copy by running the vision model with LiteRT instead (Phase 6.4 option b).

### #10 — 16 KB page size / alignment
**Symptoms:** app crashes at `System.loadLibrary` on devices with 16 KB pages (`adb shell getconf PAGE_SIZE` → 16384); Android Studio warns "not 16 KB aligned".
**Fix:** build native code with NDK r28+ (aligned by default). For older NDK add linker flag `-Wl,-z,max-page-size=16384`. Prebuilt `.so` from third parties (sherpa-onnx, onnxruntime) must be recent versions that are 16 KB aligned — check with `zipalign -c -P 16 -v 4 app-debug.apk`. Use AGP 8.5.1+ (packaging alignment).

### #37 — Release build (R8) breaks JNI / serialization
**Symptoms:** release APK crashes with `NoSuchMethodError` from JNI, `ClassNotFoundException`, or `Serializer for class ... not found`.
**Fix:** `app/proguard-rules.pro`:
```
-keep class com.omnicontrol.native.llama.** { *; }
-keepclasseswithmembernames class * { native <methods>; }
-keep class com.k2fsa.sherpa.onnx.** { *; }
-keep class ai.onnxruntime.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature
-keep,includedescriptorclasses class com.omnicontrol.**$$serializer { *; }
-keepclassmembers class com.omnicontrol.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
```
Test release build after every phase from Phase 8 on.

---

## Permissions & services

### #4 — Accessibility toggle greyed out / "Restricted setting"
**Cause:** Android 13+ blocks accessibility for sideloaded apps until allowed.
**Fix (user does this):** try to enable once (shows "Restricted setting" dialog) → Settings → Apps → OmniControl → ⋮ (top-right) → **Allow restricted settings** → authenticate → go back to Accessibility and enable. On some OEM skins the menu is in "App info" → three dots. After reinstalling with `adb install` (not `-r` update) this may be needed again.

### #5 — Accessibility "On" but service not connected
**Symptoms:** settings show enabled, but `onServiceConnected` never logs / `A11yBridge` is null.
**Fix:**
- After `adb install -r`, the system sometimes unbinds the service → toggle it off/on. Show a "Re-enable" hint when `enabled && !connected` for > 3 s.
- Check the service class name in manifest matches the package (use fully-qualified `android:name`).
- Check `res/xml/omni_accessibility_config.xml` is valid (a bad attribute silently prevents binding) — look for `AccessibilityServiceInfo` errors in `adb logcat | grep -i accessib`.
- OEM battery killers: whitelist the app (battery unrestricted, autostart).

### #12 — Foreground service / microphone start not allowed
**Errors:** `ForegroundServiceStartNotAllowedException`, `SecurityException: Starting FGS with type microphone ... requires permissions ... and the app must be in the eligible state`, or AudioRecord records silence.
**Fix:**
- Manifest needs `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`, `RECORD_AUDIO` granted at runtime, and the service declares `foregroundServiceType="...microphone..."`.
- Upgrade to microphone type only **in direct response to a user tap** (bubble/panel/notification action/tile).
- If the direct overlay tap is rejected on this device: use the **trampoline activity** (Phase 0, Task 0.8 step 4) — a visible activity makes the app foreground for that moment.
- Call `ServiceCompat.startForeground(service, id, notification, types)` with the combined type bits.
- Never start the microphone from `BOOT_COMPLETED` or a background timer.

### #13 — specialUse foreground service rejected
**Error:** `MissingForegroundServiceTypeException` or `specialUse requires PROPERTY_SPECIAL_USE_FGS_SUBTYPE`.
**Fix:** declare `FOREGROUND_SERVICE_SPECIAL_USE` permission and the `<property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="..."/>` inside the `<service>` tag. Pass `FOREGROUND_SERVICE_TYPE_SPECIAL_USE` in `startForeground`.

### #15 — `run-as` not working for pushing models
**Error:** `run-as: package not debuggable` / `unknown package`.
**Fix:** install the **debug** build (`debuggable=true`). For release builds use the Model Manager "Import from file" (SAF). Some Samsung builds restrict `run-as` on `/data/local/tmp` reads — then push to `/sdcard/Download/` and import with the Model Manager.

---

## Overlay

### #11 — `ViewTreeLifecycleOwner not found` (ComposeView in WindowManager)
**Fix:** attach `OverlayLifecycleOwner` (Phase 2, Task 2.3) to the ComposeView **before** `windowManager.addView()`, and move lifecycle to RESUMED. Each window gets its own owner (or share one for all overlay windows; destroy only when the service dies).

### #14 — Overlay steals touches or keyboard focus
**Symptoms:** apps under the panel can't be tapped; keyboard doesn't open in the app; back button closes our panel instead of the app.
**Fix:**
- Bubble/panel windows use `WRAP_CONTENT` (never `MATCH_PARENT` for touchable windows).
- Default `FLAG_NOT_FOCUSABLE`; only remove it while the panel's text field is focused, and add it back on send/blur.
- Highlight/debug layer: `FLAG_NOT_TOUCHABLE` always.
- During agent gestures: `setTouchable(false)` on all our windows (Phase 3.7).

### #16 — `BadTokenException` / `permission denied for window type 2038`
**Fix:** check `Settings.canDrawOverlays(context)` right before `addView`; use `TYPE_APPLICATION_OVERLAY` only; add views from a Service context (not Application) on the main thread; wrap `addView/removeView` in try/catch.

---

## Accessibility actions

### #17 — Tree is empty or missing elements
**Causes & fixes:**
- Missing `flagRetrieveInteractiveWindows` / `canRetrieveWindowContent` in config → add them, re-enable service.
- Using `rootInActiveWindow` only (misses dialogs, keyboard, popups) → iterate `windows`.
- App draws with canvas/Flutter/Unity/Compose without semantics → use OCR (Phase 6) or gestures.
- WebView content not exposed until loaded → `awaitSettle()` longer (up to 3 s) and re-observe.
- `FLAG_SECURE` apps → expected, handoff.
- `flagIncludeNotImportantViews` missing → some views hidden.

### #18 — `ACTION_CLICK` returns true but nothing happens
**Fix:** click the nearest **clickable ancestor** (many apps put the click listener on a parent). If the screen doesn't change → fallback to a **gesture tap** at the center of the element (Verifier triggers this automatically). For Compose apps, the semantics node is often a child; the parent clickable works best.

### #19 — Gesture taps land in the wrong place
**Fix:** use `getBoundsInScreen()` (not `getBoundsInParent`). Gestures use **screen** coordinates. Check rotation and split-screen. Make sure the highlight layer uses `FLAG_LAYOUT_IN_SCREEN | FLAG_LAYOUT_NO_LIMITS` so drawn boxes match. Clamp coordinates inside the display. Gesture duration ≥ 1 ms (use 50 ms for taps).

### #20 — `ACTION_SET_TEXT` ignored
**Fix:** first `ACTION_FOCUS` or click the field, then SET_TEXT on the **editable node itself** (not its container). Some apps (custom inputs, WebViews) ignore it → fallback: put text in clipboard (`ClipboardManager.setPrimaryClip`) then `ACTION_PASTE` on the focused node. Verify by re-reading the node text.

### #21 — Stale node / `IllegalStateException` / actions on wrong element
**Fix:** never keep `AccessibilityNodeInfo` across observations. Use `NodeResolver` (Phase 3.5) to re-find by window id + child path + signature right before acting. If the screen changed since the observation (contentFp differs a lot), re-observe and ask the agent again instead of acting.

---

## Voice

### #22 — AudioRecord gives silence (all zeros)
**Causes:** mic FGS type not active (Android 14+ silences background capture), another app holds the mic (call, camera, voice recorder), privacy toggle "Microphone access" off in Quick Settings.
**Fix:** start AudioRecord only after microphone FGS is active; check `AudioRecord.getState()==STATE_INITIALIZED` and `recordingState`; detect silencing with `AudioManager.getActiveRecordingConfigurations()` / `isClientSilenced`; tell the user to enable the mic privacy toggle.

### #23 — sherpa-onnx crash on model load
**Symptoms:** native crash (SIGSEGV/SIGABRT) creating `OnlineRecognizer`, or "file not found".
**Fix:**
- When models are in `filesDir`, pass `assetManager = null` and **absolute** paths; with assets, pass the AssetManager and relative asset paths. Mixing them crashes.
- The model type in config must match the files (transducer → encoder/decoder/joiner; paraformer/ctc differ). Copy the exact config from the sherpa-onnx Android example for that model.
- Use the Kotlin API files and AAR **from the same version**.
- Check files fully copied (compare sizes with `ls -l` via run-as).

### #24 — TTS output gets recognized as a command
**Fix:** mute ASR while speaking (`setMuted(true)` before speak, unmute 250 ms after `onDone`); keep KWS active; use `VOICE_COMMUNICATION` source + `AcousticEchoCanceler`. Ignore finals that match the last spoken reply text.

### #25 — Keyword spotter false triggers / misses
**Fix:** tune `keywordsThreshold` and `keywordsScore` per keyword in `keywords.txt`; require VAD speech for non-critical words (yes/no/continue); keep "stop" sensitive. Test with TV noise.

---

## LLM

### #26 — Model outputs nonsense / ignores instructions
**Fix:**
- Use `llama_chat_apply_template` with the model's own template (instruct models are useless with the wrong format).
- Use the **instruct/chat** GGUF, not the base model.
- Keep temperature 0.1–0.3; grammar on.
- Disable "thinking" for reasoning models (e.g. `/no_think` for Qwen3) or the output budget gets used by thoughts.
- Shorten observation (≤ 60 elements) — small models get lost in long lists.
- Add 2–3 short examples in the system prompt. Re-run the eval harness after every prompt change.

### #27 — Generation too slow
**Fix:** Release CMake build (`-DCMAKE_BUILD_TYPE=Release`, `-O3`); Q4_K_M or Q4_0 quantization (Q4_0 has fast ARM repack paths in recent llama.cpp); threads = performance cores (try 4); KV/prompt cache reuse (Phase 5.1); smaller context (4096); fewer elements; use FastRouter more; Home Brain for heavy tasks.

### #28 — App killed / OOM when loading a model
**Fix:** check RAM tier; use a smaller model or lower quantization; `mmap` is default in llama.cpp (keep it); set `android:largeHeap="true"` (helps only Java heap, not native); load one large model at a time (`ModelMemoryManager`); unload VLM before loading LLM on ≤ 8 GB phones; catch load failure and show a message instead of crashing.

### #29 — Grammar parse error
**Errors:** `parse: error parsing grammar`, `Undefined rule`, `expecting ...`.
**Fix:** test the grammar on the Mac first: `llama-cli -m model.gguf --grammar-file assets/grammar/action.gbnf -p "..."`. Escape quotes as `\"` inside literals; every rule referenced must be defined; `{m,n}` repetition requires a recent llama.cpp — else use `*`. Read the grammar from assets as UTF-8 without BOM.

### #30 — Home Brain unreachable
**Fix:** phone and Mac on the same Wi-Fi (not guest network with client isolation); server listens on `0.0.0.0` not `127.0.0.1`; macOS firewall allows incoming connections; use the Mac's LAN IP (`ipconfig getifaddr en0`); Android 9+ blocks cleartext HTTP by default → add a `network_security_config.xml` allowing cleartext **only** for private IP ranges / the configured host (document this choice), or use HTTPS with a self-signed cert pinned.

---

## Vision

### #31 — `takeScreenshot` fails with interval error
**Fix:** enforce ≥ 1.1 s between calls (ScreenshotManager rate limiter) and reuse the cached bitmap if `contentFp` unchanged.

### #32 — Screenshots are black
**Fix:** the app uses `FLAG_SECURE` (banking, payments, some video players, incognito tabs) → this is by Android design; mark `isSecure` and hand off. Also ensure `HardwareBuffer` → software bitmap copy is done before closing the buffer.

### #33 — CLIP matching gives random results
**Fix:** almost always tokenizer or preprocessing mismatch. Compare Kotlin tokens and image tensors against `tools/vision/reference_outputs.json`; check mean/std values, RGB vs BGR order, CHW layout, resize/crop method, L2-normalization of embeddings before cosine similarity, and that INT8 quantization didn't destroy accuracy (test FP16).

---

## Navigator

### #34 — Screen graph explodes (thousands of nodes)
**Fix:** `structureFp` includes dynamic content. Exclude list item texts, numbers, timestamps, usernames; keep only viewIds, tab/button labels, field hints. Cap nodes per app (e.g. 500, prune least-used).

### #35 — Routine replay clicks the wrong item
**Fix:** signature too weak (no viewId, generic text like "More"). Add relative position + parent container text to the signature; for list items prefer a semantic step ("click first item in list") over a fixed text; verify expected screen label after each step and fall back to the LLM when it doesn't match.

### #36 — `open_url` opens the wrong browser or app
**Fix:** to force Chrome: `intent.setPackage("com.android.chrome")` if installed, else let the system choose. Some links open their app (App Links) — that's expected unless the user said "in Chrome".

---

## Agent workflow issues (for Antigravity itself)

### A1 — Agent keeps "fixing" the same error in circles
Follow the **When stuck protocol** (00_GLOBAL_RULES §7): stop after 3 attempts, revert, write `docs/STUCK.md`, ask the user.

### A2 — Agent wants to switch library / downgrade SDK to escape an error
Not allowed without asking the user. Find the matching item here first.

### A3 — Build passes but feature doesn't work on phone
Always check `adb logcat | grep -i omni` and `adb logcat *:E` for exceptions; never mark a task done without the phone check listed in the phase.

### A4 — Gradle daemon / cache weirdness
`./gradlew --stop && ./gradlew clean :app:assembleDebug`. If still strange: delete `~/.gradle/caches/transforms-*` and `.gradle/` in the project (safe), then sync again.
