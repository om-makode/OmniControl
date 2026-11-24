# OmniControl — Architecture

## 1. System Overview

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
│  │            │   │                         │ → Executor →     │ │   │
│  │            │   │                         │   Verifier       │ │   │
│  │            │   │                         └──┬─────────┬─────┘ │   │
│  ┌────────────┐   │                            ▼         ▼       │   │
│  │ Overlay UI │◄──┤ state flows            LlmEngine   SafetyGuard│  │
│  │ (Compose in│   │                       (llama.cpp)            │   │
│  │ WindowMgr) │   │                                              │   │
│  └────────────┘   └──────────────────────────────────────────────┘   │
└──────────────────────────────────────┬───────────────────────────────┘
                                       │ binds
                                       ▼
                    ┌──────────────────────────────────────┐
                    │  OmniAccessibilityService            │
                    │  • Screen tree (AccessibilityNodeInfo)│
                    │  • Gestures (dispatchGesture)        │
                    │  • Screenshots (takeScreenshot API)  │
                    └──────────────────────────────────────┘
```

## 2. Technical Findings from Phase 0 Spikes

1. **Accessibility Subsystem (Spike A):**
   - Implemented in `:feature:accessibility` via `OmniAccessibilityService`.
   - Uses `canRetrieveWindowContent="true"`, `canPerformGestures="true"`, and `canTakeScreenshot="true"`.
   - Native `takeScreenshot()` (API 30+) captures `HardwareBuffer` directly to `Bitmap.Config.ARGB_8888` without requiring `MediaProjection` permissions or recording prompts.

2. **Offline Speech & VAD Engine (Spike B):**
   - Implemented in `:feature:voice` wrapping `sherpa-onnx` (v2.0.3) via `:third_party:sherpa-onnx`.
   - Streaming Zipformer transducer handles live real-time speech transcription.
   - Silero VAD runs concurrently on 100ms PCM chunks (1600 samples) to detect voice onset/offset.

3. **Local LLM Engine & Grammar Constrained Generation (Spike C):**
   - Implemented in `:native:llama` (`libomnillama.so`) linking `llama.cpp` (b4999) via CMake and JNI.
   - Grammar-constrained sampling (`llama_sampler_init_grammar`) enforces strict JSON output matching action schemas.
   - Dedicated single-thread dispatcher (`Executors.newSingleThreadExecutor`) protects thread-unsafe llama contexts.

4. **Floating Overlay & Foreground Microphone Access (Spike D):**
   - Android 14+ FGS microphone permission is managed by starting with `specialUse` for the assistant bubble, and dynamically upgrading to `microphone` on user interaction.
   - `TrampolineActivity` is provided as an immediate fallback for OEM skins that enforce strict activity foreground requirements before mic capture.
