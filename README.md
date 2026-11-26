# OmniControl

A floating AI assistant that operates your Android phone by voice. Tap the orb, talk, and it opens apps, searches, taps, types, scrolls and works through multi-step tasks while you watch a glowing **touching ball** do each touch.

```
"Open YouTube and play the first video"
"Search Kotlin coroutines on YouTube" → "play the second one" → "go back"
"Open ChatGPT, ask how to turn on dark mode in Android, then do it"
"Open the video with a car in the thumbnail"          (needs an AI brain with vision)
```

## How it works

| Layer | What it does |
|---|---|
| **Voice** (`feature/voice`) | Android's speech recognizer in a self-healing loop: prefers the on-device recognizer (offline), restarts after every phrase, recovers from every error code. Mutes itself while it talks, fixes common mis-hearings ("you tube", "chat gpt") and picks the best of five guesses using your installed app names. |
| **Quick commands** (`feature/agent/quick`) | Instant, no AI: open apps, search (uses YouTube/Google/Play/Maps deep links), "play the Nth video/result", tap things by name, type, scroll, swipe, back/home. Compound commands are split ("open X, search Y and play the second"). |
| **AI brain** (`feature/agent/brain`) | For everything else. Every step it reads the screen (plus a screenshot with numbered boxes) and chooses one action, then looks again. It keeps notes, so it can read instructions in one app (e.g. ChatGPT) and carry them out in another. |
| **Hands & eyes** (`feature/accessibility`) | Accessibility service. Reads every window, taps the exact node (or does a real finger gesture if the app ignores the tap), types, scrolls, takes screenshots. Checks after every action whether the screen changed. |
| **Overlay** (`feature/overlay`) | The orb, a live caption ("what you said / what I'm doing"), a chat panel and the touching ball. |
| **Safety** | Asks before send/buy/delete/post/subscribe/call. Never types passwords or touches payment PINs. Say "stop", tap the orb, or press Volume Up + Volume Down together to stop instantly. |

## Safety rules (enforced in code, the AI cannot override them)

The AI only *proposes* actions; `feature/agent/safety/SafetyPolicy.kt` decides.

| Level | What |
|---|---|
| **Never** | Payments, UPI, bank transfers, approving payment/collect requests; banking, wallet, password-manager and identity apps; typing/reading passwords, PINs, OTPs, CVVs, recovery codes; CAPTCHA/fingerprint/identity checks; turning off screen lock, Play Protect or other security; factory reset/erase. Control is handed back to you. |
| **Proposed action card, every time** | Calls, SMS/WhatsApp/email sends, replies, forwards, shares (shows recipient + full message); posts, comments, likes, subscribes, follows; orders and bookings (stops before payment); deleting/moving/sharing files, contacts, data; setting changes, permissions, installs/uninstalls; sharing location; camera/recording; unfamiliar links and downloads. One approval = one action. Approval only by your voice ("confirm"/"cancel") or a tap, never by the AI. Auto-cancels after 30 s. Repeating the same send/call within 3 minutes is flagged. |
| **Voice** | Speech that contains adult/offensive words is treated as misheard and never run ("Please repeat it"). Low-confidence speech is never run. Only listens after you tap the orb; stops after 4 quiet minutes. "Stop"/"cancel" cancels instantly and nothing runs afterwards. |
| **Privacy** | Online AIs (Gemini, Groq, OpenRouter) never receive messages, email, contacts, photos, files, notifications or banking screens (unless you allow personal apps in Settings). Everything sent is redacted first (emails, phone numbers, card/ID numbers, codes). Screenshots are off by default for online AIs. Logs contain no screen text or speech. |

## AI brain options (all free)

Pick one in the app: **AI brain**. Then press **Test connection**.

| Option | Setup | Notes |
|---|---|---|
| Quick commands only | none | Works offline. No multi-step reasoning. |
| **My computer (Ollama)** | On your Mac: `brew install ollama` → `ollama pull qwen2.5vl:7b` → `OLLAMA_HOST=0.0.0.0 ollama serve`. In the app, URL `http://<mac-ip>:11434/v1`. | Private: everything stays on your Wi-Fi. Sees screenshots. |
| **Gemini (free key)** | Key from aistudio.google.com | Smart + vision. Screen data goes to Google. |
| **Groq (free key)** | Key from console.groq.com | Very fast. |
| OpenRouter | Key from openrouter.ai, any `:free` model | |
| On-device | Import a `.gguf` (e.g. Qwen2.5-3B-Instruct Q4_K_M) or `./tools/push_model.sh model.gguf` | Fully offline but slow and less capable; no vision. |

Model names change over time. If a default model name stops working, put in the current one from the provider's model list.

## Build & install

Requirements: Android Studio (JDK 17), Android SDK 36, NDK 27.1.12297006, CMake 3.22.1.

```bash
git clone --recurse-submodules https://github.com/om-makode/OmniControl.git
cd OmniControl
./gradlew :app:assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

If you cloned without `--recurse-submodules`: `git submodule update --init --depth 1`.

## First run on the phone

1. Open OmniControl and finish **Setup**:
   - Display over other apps
   - Screen control (Accessibility). If the switch is greyed out: Settings → Apps → OmniControl → ⋮ → **Allow restricted settings**, then try again.
   - Microphone, Notifications. Battery "Unrestricted" is optional but stops Android from killing the assistant.
2. For offline, beep-free listening: Settings → Google → Speech (or "On-device recognition") → download your language.
3. Tap **Start assistant**. The orb appears.
4. **Tap the orb** to start listening and speak. Keep giving commands, no need to tap again.
   - Tap the orb while it works = stop that task. Tap again when idle = stop listening.
   - **Long-press the orb** = chat panel (type commands, see history, confirm actions).
   - Say **"stop"** to cancel, **"done"** to stop listening.

## Tests

```bash
./gradlew testDebugUnitTest
```

Covers command parsing, speech clean-up, AI reply parsing and on-screen item matching.

## Limits

- Apps that block screen reading (banking, some payment screens) can't be seen by any accessibility app. The assistant tells you and stops.
- Visual requests ("the video with a car") need an AI brain with image support.
- Android's recognizer needs either internet or a downloaded offline language pack.
