# OmniControl — Setup & Build Guide

## Prerequisites
- **Operating System:** Windows, macOS, or Linux
- **JDK:** OpenJDK 17 or higher
- **Android SDK:** Compile & Target SDK 36, Min SDK 30
- **NDK:** 27.1.12297006 or higher (configured in `local.properties`)
- **CMake:** 3.22.1 or higher
- **Target Device Architecture:** `arm64-v8a`

## Build Instructions

1. Clone or open the repository:
   ```bash
   cd OmniControl
   ```

2. Build the Debug APK and run unit tests:
   ```bash
   ./gradlew :app:assembleDebug testDebugUnitTest
   ```
   (On Windows PowerShell: `.\gradlew.bat :app:assembleDebug testDebugUnitTest`)

3. Install the APK to a connected Android phone:
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

## Device Permissions Walkthrough
After opening OmniControl on your phone:
1. **Overlay Permission:** Tap the Overlay card and toggle "Allow display over other apps" for OmniControl.
2. **Accessibility Service:** Go to `Settings -> Accessibility -> Downloaded apps -> OmniControl` and switch it **ON**.
   > *Note:* If Android 13+ displays "Restricted setting", go to `App Info -> 3 dots menu (top right) -> Allow restricted settings`, enter PIN, then enable Accessibility.
3. **Microphone Permission:** Grant microphone access ("While using the app").
4. **Notifications:** Allow notifications so the ongoing assistant foreground service and status pill remain persistent.

## Optional On-Device Neural Models
- **ASR & VAD:** Sherpa-ONNX Zipformer model files can be placed in `/sdcard/Android/data/com.omnicontrol/files/sherpa-onnx/`. If omitted, OmniControl automatically uses Android's built-in offline SpeechRecognizer.
- **LLM:** Quantized GGUF weights (e.g. `Qwen2.5-0.5B-Instruct-Q4_K_M.gguf` or `SmolLM2-360M.gguf`) can be pushed to `/sdcard/Android/data/com.omnicontrol/files/models/model.gguf`. If omitted, OmniControl's intelligent rule-based agent planner handles navigation and tasks.
