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
- [ ] 4.1 Model setup (ASR, VAD, KWS)
- [ ] 4.2 AudioCapture
- [ ] 4.3 VAD + streaming ASR pipeline
- [ ] 4.4 Keyword spotter (stop/cancel/done)
- [ ] 4.5 VoiceSession loop + mic FGS
- [ ] 4.6 Hotword biasing
- [ ] 4.7 TTS
- [ ] 4.8 Temporary FastRouter hookup
- [ ] 4.9 Phase 4 report

## Phase 5 — AI Agent
- [ ] 5.1 LlmEngine interface + llama.cpp backend
- [ ] 5.2 Action grammar + schema + validator
- [ ] 5.3 Observation serializer + PromptBuilder
- [ ] 5.4 FastRouter (full)
- [ ] 5.5 ConversationManager
- [ ] 5.6 Agent loop (ReAct) + sub-goals
- [ ] 5.7 SafetyGuard + confirm/handoff
- [ ] 5.8 Home Brain backend (optional)
- [ ] 5.9 Evaluation harness
- [ ] 5.10 Phase 5 report

## Phase 6 — Visual Understanding
- [ ] 6.1 ScreenshotManager
- [ ] 6.2 OCR + merge
- [ ] 6.3 Color analysis
- [ ] 6.4 Image-text matcher (CLIP-style)
- [ ] 6.5 Set-of-Marks renderer
- [ ] 6.6 On-demand VLM
- [ ] 6.7 Vision tool in agent + clarification
- [ ] 6.8 Phase 6 report

## Phase 7 — Navigator, Memory, Routines
- [ ] 7.1 Element signatures + screen nodes
- [ ] 7.2 Screen graph recording
- [ ] 7.3 Route finding + stale edge handling
- [ ] 7.4 Deep link table
- [ ] 7.5 Routines + aliases
- [ ] 7.6 Teach mode
- [ ] 7.7 Session clipboard + find + popup handler
- [ ] 7.8 Five cross-app workflows pass
- [ ] 7.9 Phase 7 report

## Phase 8 — Hardening & Polish
- [ ] 8.1 Interruption tests
- [ ] 8.2 Performance + battery
- [ ] 8.3 Privacy audit
- [ ] 8.4 Final docs
- [ ] 8.5 Phase 8 report
