# 00 — Global Rules (read before every phase)

These rules apply to every phase. Antigravity must follow them exactly.

---

## 1. Project facts

| Item | Value |
|---|---|
| App name | OmniControl |
| Application ID | `com.omnicontrol.app` |
| Base package | `com.omnicontrol` |
| Language | Kotlin only (C++ only inside `:native:llama`) |
| UI | Jetpack Compose + Material 3 |
| minSdk / targetSdk / compileSdk | 30 / 36 / 36 |
| ABI | `arm64-v8a` only (smaller APK, all modern phones) |
| JDK | 17 (or the JDK bundled with Android Studio) |
| Build | Gradle Kotlin DSL (`*.gradle.kts`) + version catalog `gradle/libs.versions.toml` |
| DI | Hilt (with KSP, **not** kapt) |
| DB | Room (with KSP) + DataStore Preferences |
| Async | Coroutines + Flow. No RxJava. No `GlobalScope`. |
| JSON | kotlinx.serialization |
| No | React Native, Flutter, Java source files, paid APIs, analytics, Firebase |

---

## 2. Versions policy (avoids most build errors)

1. Use the versions that **Android Studio's latest stable "Empty Activity (Compose)" template** generates for AGP, Kotlin, Compose BOM. Do not mix in versions from old blog posts.
2. Kotlin 2.x uses the **Compose Compiler Gradle plugin** (`org.jetbrains.kotlin.plugin.compose`). Never set `composeOptions.kotlinCompilerExtensionVersion`.
3. KSP version must match the Kotlin version (KSP versions look like `<kotlin-version>-<ksp-version>`). Check the KSP releases page if Gradle complains.
4. Hilt: use the same version for `hilt-android`, `hilt-compiler` and the Hilt Gradle plugin.
5. Put **every** version in `gradle/libs.versions.toml`. No hardcoded versions in module build files.
6. Native: install NDK **r28 or newer** (it builds 16 KB page-aligned libraries by default — needed on newer Android devices) and CMake 3.22+ from SDK Manager. Pin the NDK with `ndkVersion = "..."` in `:native:llama`.
7. Pin third-party native sources (llama.cpp) to a **specific git tag/commit**. Never track `master`.
8. Before adding any dependency not listed in the phase file → stop and ask the user.

---

## 3. Module layout (create a module only in the phase that first needs it)

```
OmniControl/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle/libs.versions.toml
├── app/                                  (Phase 0)
├── core/model/                           (Phase 0)  pure Kotlin data classes, no Android deps if possible
├── core/common/                          (Phase 0)  dispatchers, logging, Result, settings
├── core/database/                        (Phase 1)
├── feature/permissions/                  (Phase 1)
├── feature/overlay/                      (Phase 2)
├── feature/accessibility/                (Phase 0 spike → Phase 3)
├── feature/voice/                        (Phase 0 spike → Phase 4)
├── third_party/sherpa-onnx/              (Phase 0)  AAR wrapper module, see TROUBLESHOOTING #6
├── native/llama/                         (Phase 0 spike → Phase 5)
├── feature/llm/                          (Phase 5)
├── feature/agent/                        (Phase 5)
├── feature/vision/                       (Phase 6)
├── feature/navigator/                    (Phase 7)
├── feature/debug/                        (Phase 3)
├── feature/shizuku/                      (Phase 9, optional)
├── testdata/                             (Phase 3+)
├── tools/                                (scripts)
└── docs/                                 (these guides + reports)
```

Module rules:
- Library modules use `com.android.library` and namespace `com.omnicontrol.<area>.<name>` (e.g. `com.omnicontrol.feature.voice`).
- Modules depend on `:core:model` and `:core:common` — **never on each other's internals**. Cross-module communication goes through interfaces defined in `:core:model` (or a small `api` package) and is wired by Hilt in `:app`.
- `:app` is the only module that knows all modules.

---

## 4. Build & run commands (use these exact commands)

```bash
# build only the app (avoids building AARs of library modules — see TROUBLESHOOTING #6)
./gradlew :app:assembleDebug

# unit tests for all modules
./gradlew testDebugUnitTest

# install on phone
adb install -r app/build/outputs/apk/debug/app-debug.apk

# see app logs (all our log tags start with "Omni")
adb logcat -v time | grep -i "Omni"

# clear app data (fresh start)
adb shell pm clear com.omnicontrol.app
```

Every task ends with `./gradlew :app:assembleDebug` succeeding. Never leave the build broken between tasks.

---

## 5. Code rules

1. **Main thread:** never do file I/O, model loading, inference, DB, or tree walking on the main thread. Use `Dispatchers.IO` / `Dispatchers.Default` (inject dispatchers from `:core:common` so tests can replace them).
2. **Lifecycles:** services own a `CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)` and cancel it in `onDestroy()`.
3. **Logging:** use one `OmniLog` helper with tags like `OmniOverlay`, `OmniA11y`, `OmniVoice`, `OmniAgent`, `OmniLLM`. Never log full screen text or transcripts in release builds (privacy).
4. **Errors:** functions that can fail return `Result<T>` or a sealed `Outcome`. No silent `catch {}`. Every catch logs.
5. **No fake features:** a stub must be named clearly (`NotImplementedYet`), and the report must list it. Never write "done" for something that isn't working on the phone.
6. **Small files:** one class per file, files under ~400 lines when possible.
7. **Tests:** pure logic (parsers, routers, validators, matchers, graph) must have JUnit tests.
8. **Strings** shown to the user go in `strings.xml`.
9. **Privacy:** no network calls except (a) model downloads, (b) optional Home Brain on LAN. Both blocked when "Offline mode" is on.

---

## 6. Workflow per task

```
1. Read the task in the phase file.
2. Write/modify the code.
3. ./gradlew :app:assembleDebug   → fix errors (check TROUBLESHOOTING.md first)
4. ./gradlew testDebugUnitTest     → fix failures
5. If the task has a phone check: give the user the exact adb + manual steps.
6. Tick the task in docs/PROGRESS.md with a one-line note.
```

---

## 7. "When stuck" protocol (important)

If the same error is not fixed after **3 different attempts**:

1. Stop changing code randomly.
2. Search `docs/TROUBLESHOOTING.md` for the error text.
3. Revert to the last working state of the files you changed (git).
4. Write `docs/STUCK.md` with: the exact error, what you tried (3 attempts), versions involved, your best guess.
5. Tell the user and wait. Do **not** switch libraries, architecture, or languages to get around the problem without asking.

Never:
- Delete modules or large code blocks to "make the build pass".
- Downgrade minSdk/targetSdk.
- Replace a real implementation with a mock to make a test pass.
- Add `@Suppress` / `!!` everywhere to silence errors.

---

## 8. Git

- `git init` in Phase 0. Commit after every finished task: `phase<N>: <task title>`.
- `.gitignore` must exclude: `build/`, `.gradle/`, `local.properties`, `*.gguf`, `*.onnx`, `models/`, `*.apk`, `.idea/` (except shared settings).
- Model files are **never** committed (too big). They live on the phone (see §9).

---

## 9. Model files (how they get onto the phone)

Models are large (50 MB – 3 GB). Never put them in `assets/` (APK becomes huge and slow to install).

Location on phone: `context.filesDir/models/<model-folder>/…` (app-private).

Two ways to get them there:

**A. Developer way (fast, debug builds only):**
```bash
adb push my-model.gguf /data/local/tmp/
adb shell run-as com.omnicontrol.app mkdir -p files/models/llm
adb shell run-as com.omnicontrol.app cp /data/local/tmp/my-model.gguf files/models/llm/
adb shell rm /data/local/tmp/my-model.gguf
```
For folders, push a `.tar` and extract with `run-as ... tar -xf`.

**B. User way (Model Manager screen):** download from the model's official URL (HTTPS) with SHA-256 check, or "Import from file" via the Storage Access Framework (copy into `filesDir/models`).

`tools/push_models.sh` (created in Phase 0) automates way A.

---

## 10. Phase report template

At the end of each phase create `docs/phases/PHASE_<N>_REPORT.md`:

```markdown
# Phase N Report
## Summary
## Files created
## Files modified
## Dependencies added (name, version, license)
## Permissions added
## How it works (short)
## Build status (commands + result)
## Tests (unit tests run + result)
## Phone testing steps (exact)
## Known limitations / stubs
## Measurements (latency, RAM, etc. if relevant)
```
