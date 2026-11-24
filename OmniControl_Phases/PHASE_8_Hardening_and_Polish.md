# Phase 8 — Hardening, Performance, Privacy & Final Docs

**Goal:** OmniControl is stable for daily personal use: survives interruptions, uses little battery when idle, never leaks data, and is fully documented.

**Prerequisite:** Phase 7 accepted.

---

## Task 8.1 — Interruption & edge-case tests

Run each scenario, fix every crash/hang, and record result in `docs/TESTING.md`:

| # | Scenario | Expected |
|---|---|---|
| 1 | Incoming phone call during listening | Session pauses, mic released; resumes or ends cleanly after call |
| 2 | Incoming call during agent execution | Agent cancels current step, state Paused |
| 3 | Bluetooth headset connected/disconnected mid-session | Audio continues (or restarts AudioRecord) without crash |
| 4 | Wired/USB-C headset mic | Works |
| 5 | Screen locks during session | Session ends, mic off, overlay hidden on keyguard |
| 6 | Rotate phone with panel open | Panel and bubble reposition correctly |
| 7 | Split-screen / pop-up window apps | Observation uses the focused window; actions land correctly |
| 8 | Dark mode / light mode / large font / display size changed | Overlay readable; element boxes align |
| 9 | App killed by system (`adb shell am kill com.omnicontrol.app`) | Accessibility service reconnects; bubble restarts from notification/Dashboard |
| 10 | Accessibility service disabled while running | Panel shows "Screen control disabled — re-enable", no crash |
| 11 | Overlay permission revoked while running | Service stops gracefully, notification explains |
| 12 | Low memory (open many apps) during LLM load | Model load fails gracefully with message; fast-path still works |
| 13 | Airplane mode entire session | All core features work |
| 14 | Home Brain goes offline mid-session | Falls back to on-device model within one step |
| 15 | Very long command (30+ words) | Handled or politely split |
| 16 | Background noise / TV on | No false commands; KWS threshold OK |
| 17 | Phone reboot | App works after reboot; bubble not auto-started unless user enables "start on boot" (optional `BOOT_COMPLETED` → only a notification "Tap to start", never auto mic) |
| 18 | App update (`adb install -r`) during idle | Settings, routines, history preserved |
| 19 | Non-English UI app (e.g. app language Hindi) | Tree-based actions still work by ID |
| 20 | 30-minute mixed session | No crash, memory stable |

Add `Thread.setDefaultUncaughtExceptionHandler` that writes crash stack traces to `files/crashes/` (local only) and shows them in Developer screen.

## Task 8.2 — Performance & battery

- **Idle (bubble only):** no audio, no models loaded, no polling. Accessibility events: the service must do almost nothing when no session is active (early return in `onAccessibilityEvent` unless teach mode/session active or needed for `currentPackage`).
- **Model lifecycle:** load LLM on session start (show "Warming up…"), keep warm during session, unload after N minutes idle (setting, default 2). `ModelMemoryManager` ensures one large model at a time on ≤ 8 GB phones.
- **Threads:** LLM threads = number of performance cores (test 4 vs 6); don't block voice thread.
- **Measure** and write to `docs/PERFORMANCE.md`:
  - `adb shell dumpsys batterystats --reset` → 10-minute active session → `adb shell dumpsys batterystats com.omnicontrol.app` (or Battery usage screen) → % per 10 min.
  - RAM: `adb shell dumpsys meminfo com.omnicontrol.app` idle / listening / LLM loaded / VLM loaded.
  - Latencies (from logs): end-of-speech → action start for fast path and LLM path; observe(); screenshot; OCR; CLIP.
- **Overlay smoothness:** optional Macrobenchmark or at least `adb shell dumpsys gfxinfo com.omnicontrol.app` after dragging/expanding 20 times — janky frames < 5%.
- Thermal: if `PowerManager.currentThermalStatus ≥ THERMAL_STATUS_SEVERE`, reduce LLM threads / prefer fast path / suggest Home Brain.

## Task 8.3 — Privacy audit

- [ ] Search code for any network use: `grep -rn "http\|URL(\|OkHttp\|Socket" --include=*.kt` → only Model download + Home Brain, both behind `offlineMode` check.
- [ ] Verify no traffic in Offline mode: `adb shell dumpsys netstats detail | grep -A3 com.omnicontrol` before/after a session (no growth), or use a local proxy.
- [ ] `android:allowBackup="false"`, data extraction rules exclude all.
- [ ] Release logs contain no transcripts/screen text (use `if (BuildConfig.DEBUG)` guards in `OmniLog`).
- [ ] Traces off by default; auto-delete after 7 days; "Delete all data" button (history, traces, graph, routines, aliases) in Settings with confirmation.
- [ ] Mic only active during a user-started session; green privacy indicator matches our UI state.
- [ ] Password fields never read into prompts (serializer shows `(password)` without value) and never typed.
- [ ] Manifest permissions list is minimal — review each one.

## Task 8.4 — Release build & final docs

- **Release build:** `minifyEnabled true` with R8; keep rules for: JNI classes (`-keep class com.omnicontrol.native.llama.** { *; }`), sherpa-onnx (`-keep class com.k2fsa.sherpa.onnx.** { *; }`), kotlinx.serialization models, ONNX Runtime/LiteRT classes. Test the release APK fully (R8 often breaks JNI/serialization — see TROUBLESHOOTING #37).
- Sign with a local keystore (`keystore.jks`, never commit; path + passwords in `~/.gradle/gradle.properties`).
- Docs (in repo):
  - `README.md` — what it is, features, screenshots, quick start.
  - `docs/SETUP.md` — Mac tools, clone + submodules, build, install, permissions walkthrough (with restricted settings step), model download/push commands.
  - `docs/TESTING.md` — manual test guide (all acceptance checklists merged) + scenario table results.
  - `docs/CAPABILITIES.md` — final supported / limited / impossible list with reasons.
  - `docs/MODELS.md` — final models, licenses, SHA-256, benchmarks.
  - `docs/HOME_BRAIN.md` — PC setup.
  - `docs/PERFORMANCE.md`.

---

## Acceptance checklist

- [ ] All 20 scenarios pass or have documented, acceptable behaviour.
- [ ] Release APK works identically to debug.
- [ ] Idle battery drain negligible over 2 hours with bubble visible.
- [ ] Privacy checklist all ticked.
- [ ] Docs complete; a fresh clone can be built by following SETUP.md only.
- [ ] Final demo (master plan §22) passes end-to-end.
