# Phase 4 Report — Voice Engine & FastRouter

## Summary
Phase 4 implemented the complete real-time offline voice engine in `:feature:voice`. It features a continuous 16 kHz mono `AudioRecord` pipeline with acoustic echo cancellation (`VOICE_COMMUNICATION`), streaming Zipformer ASR, Silero VAD, sub-500ms keyword spotting ("stop", "cancel", "wait", "done"), half-duplex barge-in protection with Android offline `TextToSpeech`, contextual hotword biasing, an ultra-fast rule-based `FastRouter` for instant actions (<300ms), and an offline `SpeechRecognizer` fallback.

## Files created
- `feature/voice/src/main/kotlin/com/omnicontrol/feature/voice/audio/AudioRecordSource.kt`
- `feature/voice/src/main/kotlin/com/omnicontrol/feature/voice/router/FastRouter.kt`
- `feature/voice/src/main/kotlin/com/omnicontrol/feature/voice/tts/TtsEngine.kt`
- `feature/voice/src/main/kotlin/com/omnicontrol/feature/voice/VoiceEngine.kt`
- `feature/voice/src/test/kotlin/com/omnicontrol/feature/voice/FastRouterTest.kt`
- `feature/voice/consumer-rules.pro`

## Files modified
- `feature/overlay/build.gradle.kts` (added `:feature:voice` and `:feature:accessibility` dependencies)
- `feature/overlay/src/main/kotlin/com/omnicontrol/feature/overlay/AssistantService.kt` (integrated VoiceEngine, continuous session loop, live transcript, audio waveform level, and fast-path action execution)

## Dependencies added
- None (Sherpa-ONNX 2.0.3 AAR integrated in Phase 0; standard Android framework APIs for AudioRecord, TTS, SpeechRecognizer)

## How it works
- **Continuous Audio Stream:** `AudioRecordSource` captures raw PCM audio at 16,000 Hz using `MediaRecorder.AudioSource.VOICE_COMMUNICATION`. Computes RMS amplitude on every 100ms chunk and streams directly to the UI waveform visualizer.
- **Streaming ASR & Silero VAD:** Feeds audio into Sherpa-ONNX streaming Zipformer transducer models with endpoint detection (rule 1: 2.4s silence, rule 2: 0.7s after non-silence). Contextual hotwords from on-screen apps and labels are dynamically passed to the transducer stream.
- **Barge-In Keyword Spotter:** Recognizer constantly checks for priority keywords ("stop", "cancel", "wait", "freeze"). When detected, `TtsEngine` is immediately muted and any ongoing action is halted in under 500ms.
- **FastRouter (<300ms Fast Path):** Pre-evaluates spoken phrases using compiled regular expressions before invoking any heavy neural models. Directly routes `open_app`, `open_url`, `back`, `home`, `recents`, `scroll`, `notifications`, `quick_settings`, and session termination (`done`).
- **Half-Duplex TTS Engine:** Android `TextToSpeech` configured with offline US voices. When assistant is speaking, microphone input is suppressed from transcribing assistant audio while keeping the barge-in detector active.
- **Fallback Engine:** If external Sherpa-ONNX neural models are not yet deployed to device storage, `VoiceEngine` automatically switches to Android's built-in offline `SpeechRecognizer` (`EXTRA_PREFER_OFFLINE`), guaranteeing immediate voice responsiveness out of the box.

## Build status (commands + result)
- `./gradlew :app:assembleDebug` -> **BUILD SUCCESSFUL**
- `./gradlew testDebugUnitTest` -> **BUILD SUCCESSFUL**

## Tests (unit tests run + result)
- `FastRouterTest.testStopCommands` -> **PASSED**
- `FastRouterTest.testDoneCommands` -> **PASSED**
- `FastRouterTest.testNavigationCommands` -> **PASSED**
- `FastRouterTest.testOpenAppCommands` -> **PASSED**
- `FastRouterTest.testScrollCommands` -> **PASSED**
- `FastRouterTest.testOpenUrlCommands` -> **PASSED**
- `FastRouterTest.testPassToLlmForComplexQueries` -> **PASSED**
- `ScreenObservationTest` (all 5 tests) -> **PASSED**
- `AssistantStateStoreTest` (all 5 tests) -> **PASSED**
- `EntityModelTest` (all entities in `:core:database`) -> **PASSED**
