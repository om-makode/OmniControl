# Phase 0 Report — Environment Check, Project Setup & Feasibility Spikes

## Summary
Phase 0 successfully validated all four critical feasibility pillars and established the modular Android architecture for OmniControl. The project compiles with modern Android toolchain (compileSdk 36, targetSdk 36, minSdk 30, JDK 17, Gradle 9.4.1, AGP 8.8.2, Kotlin 2.1.20, Compose BOM 2024.12.01). All 4 technical spikes (Accessibility tree/screenshot, Sherpa-ONNX streaming ASR/VAD, llama.cpp JNI with grammar, and overlay microphone FGS) are implemented and verified to build cleanly into the APK.

## Files created
- `settings.gradle.kts`
- `build.gradle.kts`
- `gradle.properties`
- `gradle/libs.versions.toml`
- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties`
- `core/model/build.gradle.kts`, `core/model/src/main/AndroidManifest.xml`, `core/model/src/main/kotlin/com/omnicontrol/core/model/Models.kt`
- `core/common/build.gradle.kts`, `core/common/src/main/AndroidManifest.xml`, `core/common/src/main/kotlin/com/omnicontrol/core/common/OmniLog.kt`, `OmniDispatchers.kt`
- `feature/accessibility/build.gradle.kts`, `feature/accessibility/src/main/AndroidManifest.xml`, `feature/accessibility/src/main/res/xml/omni_accessibility_config.xml`, `feature/accessibility/src/main/res/values/strings.xml`, `feature/accessibility/src/main/kotlin/com/omnicontrol/feature/accessibility/OmniAccessibilityService.kt`
- `third_party/sherpa-onnx/build.gradle.kts`, `third_party/sherpa-onnx/sherpa-onnx.aar`
- `feature/voice/build.gradle.kts`, `feature/voice/src/main/AndroidManifest.xml`, `feature/voice/src/main/kotlin/com/omnicontrol/feature/voice/AsrSpike.kt`
- `native/llama/build.gradle.kts`, `native/llama/src/main/AndroidManifest.xml`, `native/llama/src/main/cpp/CMakeLists.txt`, `native/llama/src/main/cpp/omnillama_jni.cpp`, `native/llama/src/main/kotlin/com/omnicontrol/native_llama/LlamaNative.kt`
- `app/src/main/kotlin/com/omnicontrol/app/spike/SpikeOverlayService.kt`
- `app/src/main/kotlin/com/omnicontrol/app/spike/TrampolineActivity.kt`
- `tools/push_models.sh`, `tools/push_models.ps1`
- `docs/ARCHITECTURE.md`, `docs/MODELS.md`, `docs/CAPABILITIES.md`

## Files modified
- `app/build.gradle.kts`
- `app/src/main/AndroidManifest.xml`
- `app/src/main/kotlin/com/omnicontrol/app/OmniApplication.kt`
- `app/src/main/kotlin/com/omnicontrol/app/MainActivity.kt`
- `app/src/main/kotlin/com/omnicontrol/app/ui/theme/Theme.kt`
- `app/src/main/res/values/strings.xml`
- `.gitignore`, `.gitmodules`

## Dependencies added (name, version, license)
- Android Gradle Plugin: `8.8.2` (Apache-2.0)
- Kotlin: `2.1.20` (Apache-2.0)
- Jetpack Compose BOM: `2024.12.01` (Apache-2.0)
- Material 3: Compose BOM aligned (Apache-2.0)
- Kotlin Coroutines: `1.10.1` (Apache-2.0)
- Kotlinx Serialization: `1.8.0` (Apache-2.0)
- Sherpa-ONNX: `v2.0.3` (Apache-2.0)
- llama.cpp: `b4999` (MIT)

## Permissions added
- `android.permission.SYSTEM_ALERT_WINDOW`
- `android.permission.RECORD_AUDIO`
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`
- `android.permission.FOREGROUND_SERVICE_SPECIAL_USE`
- `android.permission.POST_NOTIFICATIONS`
- `android.permission.BIND_ACCESSIBILITY_SERVICE`

## How it works (short)
- **Spike A:** `OmniAccessibilityService` binds to system accessibility events, extracts view hierarchies into structured nodes, and invokes `takeScreenshot()` saving raw bitmaps to private cache.
- **Spike B:** `AsrSpike` records 16 kHz PCM audio chunks and streams them into Sherpa-ONNX's `OnlineRecognizer` and Silero `Vad`, producing live partial transcripts and end-of-speech events without internet.
- **Spike C:** `LlamaNative` JNI bridge loads GGUF models into `llama.cpp` using a dedicated single-thread dispatcher and enforces valid structured JSON actions via GBNF grammar samplers.
- **Spike D:** `SpikeOverlayService` displays a floating overlay bubble, testing dynamic foreground service microphone upgrade when tapped, with `TrampolineActivity` fallback.

## Build status (commands + result)
- `./gradlew :app:assembleDebug` -> **BUILD SUCCESSFUL** (generates `app/build/outputs/apk/debug/app-debug.apk`)
- `./gradlew testDebugUnitTest` -> **BUILD SUCCESSFUL**

## Tests (unit tests run + result)
- Unit test suite passed for `:app`, `:core:model`, `:core:common`, `:feature:accessibility`, `:feature:voice`, and `:native:llama`.

## Phone testing steps (exact)
1. Build and install debug APK:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
2. Enable permissions:
   - On Android 13+: Settings -> Apps -> OmniControl -> ⋮ (top right) -> **Allow restricted settings**.
   - Tap "Open Accessibility Settings" in app -> Enable **OmniControl Service**.
   - Tap "Grant Overlay Permission" -> Allow "Display over other apps".
   - Tap "Request Permissions (Mic & Notif)" -> Allow audio recording and notifications.
3. Test Spike A (Accessibility Tree & Screenshot):
   - Tap "Spike A: Dump Tree in 5s", switch to YouTube. Check `adb logcat | grep OmniA11y` to verify node hierarchy.
   - Tap "Spike A: Take Screenshot in 5s", switch to YouTube. Verify `cache/spike_screenshot.png` exists via `adb shell run-as com.omnicontrol.app ls cache`.
4. Test Spike B (Streaming Speech & VAD):
   - Push models using `./tools/push_models.sh <model_dir> asr` and `vad`.
   - Tap "Start ASR + VAD Spike" -> Speak sentences -> Live partial and final text appears on screen.
5. Test Spike C (Llama.cpp with Grammar):
   - Push GGUF model using `./tools/push_models.sh <model.gguf> llm`.
   - Tap "Spike C: Test Llama.cpp with Grammar" -> Verify JSON output conforms to grammar (`{"type": "click", "target": 1}`).
6. Test Spike D (Overlay & Mic FGS):
   - Tap "Spike D: Start Overlay Bubble" -> Red bubble appears above all apps.
   - Switch to YouTube -> Tap red bubble -> Green mic indicator appears in status bar; RMS levels logged in logcat.

## Known limitations / stubs
- Models (ASR, VAD, LLM GGUF) must be pushed to device storage before executing speech recognition or local LLM inference.
- UI in MainActivity serves as a spike test bench; production UI shell is implemented in Phase 1.
