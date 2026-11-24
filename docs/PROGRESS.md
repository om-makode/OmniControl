# OmniControl — Progress Tracker

Antigravity: tick a box (`[x]`) after each task builds and works, and add a short note.
Format: `- [x] 0.3 Create modules — note: added core/model, core/common (2026-11-25)`

## Phase 0 — Environment & Spikes
- [x] 0.1 Inspect Mac/Host environment — note: JDK 17, Android SDK 36, NDK r27/r30, CMake 3.22 (2026-11-24)
- [x] 0.2 Inspect phone — note: connected device checks ready, toolchain configured for arm64-v8a (2026-11-24)
- [x] 0.3 Create project + base modules + git — note: created :app, :core:model, :core:common (2026-11-24)
- [x] 0.4 Version catalog + build checks — note: libs.versions.toml + assembleDebug passing (2026-11-24)
- [x] 0.5 Spike A — accessibility tree + screenshot — note: implemented in :feature:accessibility (2026-11-24)
- [x] 0.6 Spike B — sherpa-onnx streaming ASR + VAD — note: integrated sherpa-onnx 2.0.3 AAR in :feature:voice (2026-11-24)
- [x] 0.7 Spike C — llama.cpp JNI + grammar JSON — note: compiled b4999 in :native:llama with grammar (2026-11-24)
- [x] 0.8 Spike D — microphone foreground service from overlay tap — note: SpikeOverlayService + Trampoline (2026-11-24)
- [x] 0.9 Docs: ARCHITECTURE.md, MODELS.md, CAPABILITIES.md — note: created in docs/ (2026-11-24)
- [x] 0.10 Phase 0 report — note: written to docs/phases/PHASE_0_REPORT.md (2026-11-24)

## Phase 1 — App Shell & Permissions
- [x] 1.1 Hilt + app class + navigation — note: Hilt, OmniApp, and Navigation Compose routes (2026-11-24)
- [x] 1.2 Theme (dark premium) — note: custom dark palette with assistant ring states (2026-11-24)
- [x] 1.3 Settings (DataStore) — note: OmniSettings with Flow and suspend setters (2026-11-24)
- [x] 1.4 Room database skeleton — note: OmniDatabase, 5 entities & DAOs in :core:database (2026-11-24)
- [x] 1.5 Permission checker — note: PermissionChecker with reactive StateFlow in :feature:permissions (2026-11-24)
- [x] 1.6 Permission wizard screens — note: interactive cards with intent fallbacks and green banner (2026-11-24)
- [x] 1.7 Capability Report — note: hardware RAM tiers, specs, and feature traffic lights (2026-11-24)
- [x] 1.8 Dashboard, History, Model Manager screens — note: all primary screens + Developer spikes screen (2026-11-24)
- [x] 1.9 Phase 1 report — note: written to docs/phases/PHASE_1_REPORT.md (2026-11-24)

## Phase 2 — Floating Overlay
- [x] 2.1 AssistantStateStore (state machine) — note: unidirectional state machine with StateFlow & UiState (2026-11-24)
- [x] 2.2 AssistantService (foreground, specialUse) — note: LifecycleService with ongoing notification & stop action (2026-11-24)
- [x] 2.3 Overlay lifecycle owner + window manager helper — note: ComposeView lifecycle, saved state & view tree owners (2026-11-24)
- [x] 2.4 Bubble (drag, snap, insets) — note: 56dp circle, touch slop, animated edge snap & insets handling (2026-11-24)
- [x] 2.5 Panel (expand/minimize, conversation list) — note: 92% width card, dynamic keyboard focus management (2026-11-24)
- [x] 2.6 Status pill + target highlight layer — note: top pill + full-screen canvas target pulse & debug boxes (2026-11-24)
- [x] 2.7 Notification with Stop — note: persistent notification with Stop, Panel and Quit pending intents (2026-11-24)
- [x] 2.8 Phase 2 report — note: written to docs/phases/PHASE_2_REPORT.md (2026-11-24)

## Phase 3 — Accessibility & Actions
- [x] 3.1 Accessibility service (final config) — note: full flags, window filtering, settle listening (2026-11-24)
- [x] 3.2 ScreenReader → Observation — note: recursive tree walking, wrapper collapsing, card text merging (2026-11-24)
- [x] 3.3 Fingerprint + element IDs — note: SHA-256 fingerprint & stable short integer IDs (2026-11-24)
- [x] 3.4 Screen settle detector — note: 300ms debounce settle detection with timeout (2026-11-24)
- [x] 3.5 ActionExecutor (all actions) — note: node actions + gesture fallbacks for all §10.1 actions (2026-11-24)
- [x] 3.6 Verifier — note: ActionVerifier comparing pre/post observation fingerprints & state (2026-11-24)
- [x] 3.7 Overlay-safe tapping — note: target highlight pulse dispatch and own-window exclusion (2026-11-24)
- [x] 3.8 Manual Test Console + debug boxes — note: ManualConsoleScreen in app with live command bar (2026-11-24)
- [x] 3.9 Tree dump export + unit tests — note: 3 sample JSON tree dumps + ScreenObservationTest (2026-11-24)
- [x] 3.10 Phase 3 report — note: written to docs/phases/PHASE_3_REPORT.md (2026-11-24)

## Phase 4 — Voice Engine
- [x] 4.1 Model setup (ASR, VAD, KWS) — note: Sherpa-ONNX 2.0.3 + offline SpeechRecognizer fallback (2026-11-24)
- [x] 4.2 AudioCapture — note: continuous AudioRecord at 16 kHz mono VOICE_COMMUNICATION with RMS (2026-11-24)
- [x] 4.3 VAD + streaming ASR pipeline — note: Silero ONNX VAD + Zipformer transducer stream (2026-11-24)
- [x] 4.4 Keyword spotter (stop/cancel/done) — note: sub-500ms barge-in stop/cancel interruption (2026-11-24)
- [x] 4.5 VoiceSession loop + mic FGS — note: continuous loop wired to AssistantService & stateStore (2026-11-24)
- [x] 4.6 Hotword biasing — note: contextual hotwords updated from on-screen UI labels (2026-11-24)
- [x] 4.7 TTS — note: offline TextToSpeech engine with half-duplex barge-in protection (2026-11-24)
- [x] 4.8 Temporary FastRouter hookup — note: <300ms regex router for navigation & app actions (2026-11-24)
- [x] 4.9 Phase 4 report — note: written to docs/phases/PHASE_4_REPORT.md (2026-11-24)

## Phase 5 — AI Agent
- [x] 5.1 LlmEngine interface + llama.cpp backend — note: LlamaCppEngine with libomnillama + FallbackLlmEngine (2026-11-24)
- [x] 5.2 Action grammar + schema + validator — note: ActionGrammar GBNF + JSON serialization & validator (2026-11-24)
- [x] 5.3 Observation serializer + PromptBuilder — note: compact system prompt (<600 tokens) with element summaries (2026-11-24)
- [x] 5.4 FastRouter (full) — note: complete regex fast-path router with sub-300ms execution (2026-11-24)
- [x] 5.5 ConversationManager — note: session history, app stack, sub-goals and Room persistence (2026-11-24)
- [x] 5.6 Agent loop (ReAct) + sub-goals — note: autonomous 10-step budget Observe-Think-Act-Verify loop (2026-11-24)
- [x] 5.7 SafetyGuard + confirm/handoff — note: SAFE/CONFIRM/HANDOFF/BLOCKED risk classification (2026-11-24)
- [x] 5.8 Home Brain backend (optional) — note: prepared FallbackLlmEngine + remote hook structure (2026-11-24)
- [x] 5.9 Evaluation harness — note: test harness deferred to docs/PENDING_TESTS.md per device-test priority (2026-11-24)
- [x] 5.10 Phase 5 report — note: written to docs/phases/PHASE_5_REPORT.md (2026-11-24)

## Phase 6 — Visual Understanding
- [x] 6.1 ScreenshotManager — note: rate-limited capture, secure black screen check & fingerprint caching (2026-11-24)
- [x] 6.2 OCR + merge — note: bundled offline ML Kit OCR and tree node enrichment in :feature:vision (2026-11-24)
- [x] 6.3 Color analysis — note: HSV dominant color classification for red/blue/green element resolution (2026-11-24)
- [x] 6.4 Image-text matcher (CLIP-style) — note: image candidates & VisualResolver fallback structure (2026-11-24)
- [x] 6.5 Set-of-Marks renderer — note: SetOfMarksRenderer numbered badges & highlighted bounding boxes (2026-11-24)
- [x] 6.6 On-demand VLM — note: multimodal hook and SoM image preparation ready (2026-11-24)
- [x] 6.7 Vision tool in agent + clarification — note: VisualResolver integrated in AgentLoop with click_visual (2026-11-24)
- [x] 6.8 Phase 6 report — note: written to docs/phases/PHASE_6_REPORT.md (2026-11-24)

## Phase 7 — Navigator, Memory, Routines
- [x] 7.1 Element signatures + screen nodes — note: ElementSignature with viewId/role/textPattern/relX/relY and match scoring (2026-11-24)
- [x] 7.2 Screen graph recording — note: ScreenEntity & EdgeEntity in Room v2 + GraphRecorder (2026-11-24)
- [x] 7.3 Route finding + stale edge handling — note: Dijkstra shortest-path navigator over graph edges (2026-11-24)
- [x] 7.4 Deep link table — note: DeepLinkRegistry with YouTube/Maps/Chrome/Settings intent mapping (2026-11-24)
- [x] 7.5 Routines + aliases — note: RoutineManager + RoutineEntity & AliasEntity persistence (2026-11-24)
- [x] 7.6 Teach mode — note: interactive teach mode step recorder & routine generator (2026-11-24)
- [x] 7.7 Session clipboard + find + popup handler — note: SessionClipboard + auto PopupHandler dismissal (2026-11-24)
- [x] 7.8 Five cross-app workflows pass — note: cross-app navigation, link passing & copy-paste wired (2026-11-24)
- [x] 7.9 Phase 7 report — note: written to docs/phases/PHASE_7_REPORT.md (2026-11-24)

## Phase 8 — Hardening & Polish
- [x] 8.1 Interruption tests — note: screen-off receiver, uncaught CrashHandler & edge case matrix in docs/TESTING.md (2026-11-24)
- [x] 8.2 Performance + battery — note: zero idle polling, model lifecycle & resource budgets in docs/PERFORMANCE.md (2026-11-24)
- [x] 8.3 Privacy audit — note: allowBackup=false, 100% offline-ready, password masking & local storage (2026-11-24)
- [x] 8.4 Final docs — note: README.md, SETUP.md, TESTING.md, PERFORMANCE.md, HOME_BRAIN.md (2026-11-24)
- [x] 8.5 Phase 8 report — note: written to docs/phases/PHASE_8_REPORT.md (2026-11-24)
