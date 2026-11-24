# OmniControl — Personal AI Android Phone Operator

OmniControl is an autonomous, on-device AI voice and screen operator for Android. It perceives your phone's screen using high-frequency accessibility trees, OCR, and vision, executes precise native and gesture actions, speaks with real-time barge-in protection, and finds its way across apps using an on-device screen graph.

---

## Key Features

- **Draggable Floating Overlay:**
  - Glassmorphic Compose UI in `WindowManager` (`TYPE_APPLICATION_OVERLAY`).
  - 56dp animated edge-snapping bubble with dynamic keyboard focus management.
  - Full-screen high-contrast target highlight pulse layer.
  - Dynamic status pill showing listening/thinking/executing states with one-tap Stop.

- **Real-Time Voice Engine & Barge-In:**
  - Continuous 16 kHz mono audio capture with acoustic echo cancellation (`VOICE_COMMUNICATION`).
  - Streaming Zipformer ASR, Silero VAD, and sub-500ms keyword spotting ("stop", "cancel", "wait").
  - FastRouter (<300ms) for navigation and app control without touching heavy neural weights.
  - Half-duplex barge-in protection with Android offline Text-To-Speech.

- **Autonomous ReAct Agent Loop:**
  - On-device GBNF grammar constrained decoding for structured `{ thought, action, say }` generation.
  - Token-efficient screen observation prompt builder (<600 tokens).
  - SafetyGuard 4-tier risk classification: `SAFE`, `CONFIRM`, `HANDOFF`, `BLOCKED`.
  - Room SQLite conversation history and app stack memory.

- **Visual Understanding Pipeline:**
  - Rate-limited screen capture with secure black-screen window detection.
  - HSV dominant color analyzer for natural phrases ("the red button", "blue icon").
  - Bundled ML Kit text recognition for extracting text on custom canvases/views.
  - Set-of-Marks numbered visual disambiguation overlays.

- **Universal Navigator & Routines:**
  - Persistent screen transition graph with Dijkstra shortest-path navigation in Room.
  - DeepLink and system Settings intent registry.
  - Interactive teach-mode recording for custom macro routines.
  - Automatic promotional popup dismissal.

---

## Architecture

```
OmniControl/
├── app/                      # App shell, DI, Navigation, Compose screens
├── core/
│   ├── model/                # Domain models, Observation, UiElement, AssistantState
│   ├── common/               # Dispatchers, OmniLog, CrashHandler
│   └── database/             # Room v2 SQLite database, DAOs, Entities
├── feature/
│   ├── permissions/          # Permission checker & onboarding wizard
│   ├── overlay/              # WindowManager Compose bubble, panel, status pill, highlights
│   ├── accessibility/        # AccessibilityService, tree reader, action executor, verifier
│   ├── voice/                # AudioRecord, Sherpa-ONNX ASR, FastRouter, TTS
│   ├── agent/                # GBNF grammar, ReAct loop, SafetyGuard, ConversationManager
│   ├── vision/               # ScreenshotManager, ML Kit OCR, ColorAnalyzer, Set-of-Marks
│   └── navigator/            # ElementSignature, ScreenGraph Dijkstra, Routines, Popups
└── native/
    └── llama/                # llama.cpp b4999 JNI (libomnillama.so)
```

---

## Quick Start

### Build & Run
```bash
./gradlew :app:assembleDebug testDebugUnitTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

For complete setup instructions, see [docs/SETUP.md](docs/SETUP.md).
For manual test procedures, see [docs/TESTING.md](docs/TESTING.md).
For performance specifications, see [docs/PERFORMANCE.md](docs/PERFORMANCE.md).
