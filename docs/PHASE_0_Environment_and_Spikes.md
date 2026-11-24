# Phase 0 — Environment Check, Project Setup & Feasibility Spikes

**Goal:** prove that the four risky technical parts work on the user's real phone *before* building features:
(A) reading the screen with accessibility, (B) offline streaming speech, (C) a local LLM through llama.cpp, (D) starting the microphone from the floating bubble.

**Output:** a building multi-module project, 4 working spikes, and 3 docs with real measurements.

**Do not** build real features, UI polish or the agent in this phase.

---

## Task 0.1 — Inspect the Mac environment

Run and record the output in `docs/phases/PHASE_0_REPORT.md`:

```bash
sw_vers
uname -m                                   # arm64 = Apple Silicon
java -version
echo $JAVA_HOME
echo $ANDROID_HOME                         # usually ~/Library/Android/sdk
ls $ANDROID_HOME/platforms
ls $ANDROID_HOME/build-tools
ls $ANDROID_HOME/ndk 2>/dev/null
ls $ANDROID_HOME/cmake 2>/dev/null
which adb && adb version
git --version
python3 --version
```

If missing, tell the user to install via **Android Studio → Settings → Languages & Frameworks → Android SDK**:
- SDK Platforms: **Android 16 (API 36)** and **Android 15 (API 35)**.
- SDK Tools: **NDK (Side by side) r28+**, **CMake 3.22+**, Android SDK Platform-Tools, Build-Tools latest.

If `ANDROID_HOME` is empty, add to `~/.zshrc`:
```bash
export ANDROID_HOME=$HOME/Library/Android/sdk
export PATH=$PATH:$ANDROID_HOME/platform-tools
```

## Task 0.2 — Inspect the phone

User must enable **Developer options → USB debugging** and accept the RSA prompt.

```bash
adb devices -l
adb shell getprop ro.product.manufacturer
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release      # Android version (15 / 16)
adb shell getprop ro.build.version.sdk          # 35 / 36
adb shell getprop ro.product.cpu.abi            # must be arm64-v8a
adb shell getprop ro.soc.model                  # chip (may be empty on some phones)
adb shell cat /proc/meminfo | head -3           # RAM
adb shell getconf PAGE_SIZE                     # 4096 or 16384
adb shell df -h /data | tail -1                 # free storage
```

Decide the **RAM tier** (write it in the report):
| RAM | LLM size to test | Vision model on device? |
|---|---|---|
| ≤ 6 GB | 0.6B–1.7B | No (use Home Brain later) |
| 8 GB | 1.5B–3B | Small, on demand |
| ≥ 12 GB | 3B–4B | Yes, on demand |

## Task 0.3 — Create project, base modules, git

1. Android Studio → New Project → **Empty Activity** (Compose). Name `OmniControl`, package `com.omnicontrol.app`, min SDK **API 30**, build config language **Kotlin DSL**.
2. Set in `app/build.gradle.kts`:
```kotlin
android {
    namespace = "com.omnicontrol.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.omnicontrol.app"
        minSdk = 30
        targetSdk = 36
        ndk { abiFilters += listOf("arm64-v8a") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures { compose = true; buildConfig = true }
}
kotlin { jvmToolchain(17) }
```
3. Create modules `:core:model` and `:core:common` (Android Library, Kotlin). Add to `settings.gradle.kts`:
```kotlin
include(":app", ":core:model", ":core:common")
```
4. `git init`, add `.gitignore` from 00_GLOBAL_RULES §8, first commit.
5. Copy these guides into `docs/`.

## Task 0.4 — Version catalog & build check

- Move all versions into `gradle/libs.versions.toml`.
- Add (versions from the template / latest stable): Compose BOM, Material3, lifecycle-runtime-compose, activity-compose, kotlinx-coroutines-android, kotlinx-serialization-json, junit, kotlinx-coroutines-test.
- `./gradlew :app:assembleDebug` must pass. Install it and open the app on the phone.

## Task 0.5 — Spike A: Accessibility tree + screenshot

Create module `:feature:accessibility`.

**Manifest (in the module):**
```xml
<service
    android:name=".OmniAccessibilityService"
    android:exported="true"
    android:label="@string/a11y_label"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/omni_accessibility_config" />
</service>
```

**`res/xml/omni_accessibility_config.xml`:**
```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged|typeWindowsChanged|typeViewClicked|typeViewScrolled|typeViewFocused|typeViewTextChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagDefault|flagIncludeNotImportantViews|flagReportViewIds|flagRetrieveInteractiveWindows|flagRequestFilterKeyEvents"
    android:canRetrieveWindowContent="true"
    android:canPerformGestures="true"
    android:canTakeScreenshot="true"
    android:canRequestFilterKeyEvents="true"
    android:notificationTimeout="50"
    android:description="@string/a11y_description" />
```

`a11y_description`: "OmniControl reads the screen and performs taps only when you ask it to by voice or text. Nothing is uploaded."

**Spike code:** in `onServiceConnected()` log "connected". Add a debug method `dumpTree()` that walks `windows` → `root` recursively and logs: class name, text, contentDescription, viewIdResourceName, bounds, isClickable, isScrollable, isEditable, depth. Add `takeTestScreenshot()` using:
```kotlin
takeScreenshot(Display.DEFAULT_DISPLAY, mainExecutor,
    object : TakeScreenshotCallback {
        override fun onSuccess(result: ScreenshotResult) {
            val bmp = Bitmap.wrapHardwareBuffer(result.hardwareBuffer, result.colorSpace)
                ?.copy(Bitmap.Config.ARGB_8888, false)
            result.hardwareBuffer.close()
            // save to cacheDir/spike_screenshot.png on Dispatchers.IO, log path + size
        }
        override fun onFailure(errorCode: Int) { OmniLog.e("OmniA11y", "screenshot failed $errorCode") }
    })
```
Trigger both from a temporary button in MainActivity via a static `instance` reference (`OmniAccessibilityService.instance?.dumpTree()`) — acceptable for a spike only.

**Phone test:**
1. Install. **Sideloaded app:** Settings → Apps → OmniControl → ⋮ (top right) → **Allow restricted settings** (needed on Android 13+ before the accessibility toggle can be enabled).
2. Settings → Accessibility → OmniControl → enable.
3. Open YouTube, switch back to OmniControl, press "Dump" after a 5 s delay (add `delay(5000)` so you can switch to YouTube first).
4. `adb logcat | grep OmniA11y` → tree visible. `adb shell run-as com.omnicontrol.app ls cache` → screenshot exists.

**Record:** node count for YouTube home, time to dump (ms), screenshot time (ms).

## Task 0.6 — Spike B: sherpa-onnx streaming ASR + VAD

1. Download the latest **sherpa-onnx Android AAR** from the official GitHub releases page of `k2-fsa/sherpa-onnx` (file named like `sherpa-onnx-<version>.aar`). Record the version.
2. Create wrapper module `:third_party:sherpa-onnx` (see TROUBLESHOOTING #6 — direct local AAR deps in library modules break the build):
```kotlin
// third_party/sherpa-onnx/build.gradle.kts
configurations.maybeCreate("default")
artifacts.add("default", file("sherpa-onnx.aar"))
```
   Put the AAR at `third_party/sherpa-onnx/sherpa-onnx.aar` (git-ignored if > 50 MB, else commit; document either way). Include in settings. Then in `:feature:voice`: `implementation(project(":third_party:sherpa-onnx"))`.
   *Alternative if this fails:* copy the Kotlin API source files from the sherpa-onnx repo (`sherpa-onnx/kotlin-api/`) of the same version into `:feature:voice` (package must stay `com.k2fsa.sherpa.onnx`) and put the `.so` files from the Android release archive into `feature/voice/src/main/jniLibs/arm64-v8a/`.
3. Download models from the sherpa-onnx releases (`asr-models` and the VAD model):
   - Streaming English Zipformer (small first, e.g. the `streaming-zipformer-en-20M` package; then try a bigger streaming zipformer for accuracy).
   - `silero_vad.onnx`.
   Push with `tools/push_models.sh` to `files/models/asr/` and `files/models/vad/`.
4. Create module `:feature:voice` with a spike class `AsrSpike`:
   - `AudioRecord` 16 kHz, mono, `ENCODING_PCM_FLOAT` (or PCM_16BIT → convert to float −1..1), source `MediaRecorder.AudioSource.VOICE_RECOGNITION`.
   - Read 100 ms chunks (1600 samples) in a loop on `Dispatchers.IO`.
   - Feed each chunk to `OnlineRecognizer` stream (`acceptWaveform`, while `isReady` → `decode`, read `getResult(stream).text`). Log partial text. On `isEndpoint(stream)` → log FINAL, `reset(stream)`.
   - Create recognizer with `assetManager = null` and **absolute file paths** to the models in `filesDir`.
   - Also run `Vad` on the same chunks (window size 512 samples for Silero) and log speech start/end.
5. Temporary "Start ASR spike" / "Stop" buttons in MainActivity (request `RECORD_AUDIO` at runtime first; the app is in foreground so no service is needed for the spike).

**Record:** model names + sizes, partial-result delay (feel), CPU % (`adb shell top -n 1 | grep omnicontrol`), accuracy on 10 sentences ("open youtube", "search for swift tutorials", "go back", …), works in **airplane mode**: yes/no.

## Task 0.7 — Spike C: llama.cpp via JNI with grammar

1. Create module `:native:llama` (Android Library with C++).
2. Add llama.cpp as a submodule **pinned to a release tag**:
```bash
git submodule add https://github.com/ggml-org/llama.cpp native/llama/src/main/cpp/llama.cpp
cd native/llama/src/main/cpp/llama.cpp && git checkout <latest-release-tag> && cd -
```
3. `native/llama/build.gradle.kts`:
```kotlin
android {
    namespace = "com.omnicontrol.native.llama"
    compileSdk = 36
    ndkVersion = "<installed r28+ version>"
    defaultConfig {
        minSdk = 30
        ndk { abiFilters += listOf("arm64-v8a") }
        externalNativeBuild { cmake {
            arguments += listOf(
                "-DLLAMA_CURL=OFF", "-DLLAMA_BUILD_TESTS=OFF", "-DLLAMA_BUILD_EXAMPLES=OFF",
                "-DLLAMA_BUILD_SERVER=OFF", "-DGGML_OPENMP=OFF", "-DBUILD_SHARED_LIBS=OFF",
                "-DCMAKE_BUILD_TYPE=Release")
            cppFlags += listOf("-O3")
        } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
}
```
   *Note:* `CMAKE_BUILD_TYPE=Release` matters — debug native builds are 5–20× slower.
4. `src/main/cpp/CMakeLists.txt`:
```cmake
cmake_minimum_required(VERSION 3.22.1)
project(omnillama)
add_subdirectory(llama.cpp)
add_library(omnillama SHARED omnillama_jni.cpp)
target_link_libraries(omnillama llama common android log)
```
   (If `common` target is not available in the pinned version, link only `llama` and implement sampling with `llama_sampler_*` APIs directly.)
5. `omnillama_jni.cpp`: implement 4 JNI functions — `loadModel(path, nCtx, nThreads): Long`, `generate(handle, prompt, grammar, maxTokens): String`, `tokenCount(handle, text): Int`, `free(handle)`.
   - Use the APIs present in the pinned tag (look at `examples/llama.android` and `examples/simple` in the **same tag** — the C API changes between versions; do not copy code from old blog posts).
   - Sampler chain: grammar sampler (`llama_sampler_init_grammar(vocab, grammar, "root")`) → top-k 40 → temperature 0.2 → dist.
   - Threads: number of big cores (start with 4).
6. Kotlin wrapper `LlamaNative` with `System.loadLibrary("omnillama")`. All calls on a single dedicated thread (`Executors.newSingleThreadExecutor().asCoroutineDispatcher()`) — llama contexts are not thread-safe.
7. Test grammar (put in spike):
```
root ::= "{" ws "\"type\":" ws type "," ws "\"target\":" ws num ws "}"
type ::= "\"click\"" | "\"scroll\"" | "\"back\""
num  ::= [0-9] [0-9]?
ws   ::= [ \t\n]*
```
   Prompt: a tiny element list + "Tap the search button". Output must be valid JSON every time.
8. Download 2 candidate GGUF models (Q4_K_M) suitable for the RAM tier (see `OMNICONTROL_PLAN.md` §11.4: e.g. Qwen3 1.7B and Qwen2.5 3B Instruct, or Llama 3.2 3B). Push to `files/models/llm/`.

**Record per model:** file size, load time, RAM (`adb shell dumpsys meminfo com.omnicontrol.app | grep TOTAL`), prompt processing tokens/s, generation tokens/s, JSON valid (10/10?), correct target (x/10).

## Task 0.8 — Spike D: microphone foreground service started from an overlay tap

Why: Android 14+ only lets a `microphone` foreground service start when the app counts as "in use". We must confirm that tapping our overlay bubble while another app is in front is allowed on this phone.

1. Manifest (`:app` for now):
```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<service android:name=".spike.SpikeOverlayService"
    android:exported="false"
    android:foregroundServiceType="specialUse|microphone">
    <property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="Floating assistant bubble that the user taps to give voice commands" />
</service>
```
2. `SpikeOverlayService`: on start → `startForeground(ID, notif, FOREGROUND_SERVICE_TYPE_SPECIAL_USE)` and add a plain 56dp `View` (red circle) with `TYPE_APPLICATION_OVERLAY`, `FLAG_NOT_FOCUSABLE`.
3. On bubble tap → call `startForeground(ID, notif, FOREGROUND_SERVICE_TYPE_SPECIAL_USE or FOREGROUND_SERVICE_TYPE_MICROPHONE)` then start `AudioRecord` and log the audio level (RMS) every second for 10 s. Catch `SecurityException` / `ForegroundServiceStartNotAllowedException` and log clearly.
4. **Fallback to test if step 3 fails:** bubble tap launches a transparent `TrampolineActivity` (`Theme.Translucent.NoTitleBar`, `excludeFromRecents`, `taskAffinity=""`) that sends an intent to the service to upgrade to microphone type, then `finish()`es immediately.

**Phone test:** grant overlay + mic + notifications, start service from app, go to YouTube, tap the red bubble. RMS values must change when you speak. Android shows the green mic dot.

**Record:** which method works (direct tap or trampoline) on Android version X. Phase 2 and 4 use the working method.

## Task 0.9 — Write the docs

- `docs/ARCHITECTURE.md`: copy §5 of `OMNICONTROL_PLAN.md`, update with what spikes proved (e.g. trampoline needed or not, sherpa integration method).
- `docs/MODELS.md`: table of every model tested: name, file, size, license, source URL, SHA-256, measured numbers, chosen yes/no.
- `docs/CAPABILITIES.md`: table of features → "works / works with limits / not possible" on this phone, with reason.
- `tools/push_models.sh`: script implementing 00_GLOBAL_RULES §9 way A, usage `./tools/push_models.sh <local-file-or-dir> <subfolder>`.

## Task 0.10 — Phase report

Write `docs/phases/PHASE_0_REPORT.md` using the template, including all measurements.

---

## Acceptance checklist (user verifies)

- [ ] `./gradlew :app:assembleDebug` passes from a clean clone (after submodule init).
- [ ] Spike A: tree dump of YouTube shows titles/buttons; screenshot PNG saved.
- [ ] Spike B: live words appear while speaking, final sentence on pause, works in airplane mode.
- [ ] Spike C: at least one model returns valid JSON 10/10 times, generation ≥ 5 tokens/s.
- [ ] Spike D: mic works after tapping the bubble over another app (method recorded).
- [ ] MODELS.md and CAPABILITIES.md contain real numbers, not guesses.

## Common problems in this phase
See TROUBLESHOOTING.md: #1 (SDK/NDK not found), #4 (accessibility toggle greyed), #6 (local AAR), #7 (CMake/llama build), #8 (libcurl), #9 (duplicate .so), #10 (16 KB alignment), #12 (FGS start not allowed), #15 (run-as not working).
