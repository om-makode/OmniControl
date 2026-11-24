# Phase 4 — Offline Continuous Voice Engine

**Goal:** after one tap on the bubble, the user can speak command after command, hands-free, fully offline. Live transcript, fast end-of-speech detection, instant "stop", optional spoken replies.

**Prerequisite:** Phase 3 accepted. Spike B and Spike D results known.

---

## Architecture

```
            ┌──────────────── VoiceEngine (in AssistantService scope) ───────────────┐
AudioRecord │  AudioCapture ──float[1600] (100ms)──► SharedFlow<AudioChunk>          │
 16 kHz     │        │                       │                    │                  │
 mono       │        ▼                       ▼                    ▼                  │
            │   LevelMeter (RMS)       VadGate (Silero)     KeywordSpotter (always)  │
            │   → waveform             speech start/end     "stop","cancel","done"   │
            │                                │                                      │
            │                                ▼                                      │
            │                    StreamingAsr (Zipformer) → partial / final text     │
            │                                │                                      │
            └────────────────────────────────┼──────────────────────────────────────┘
                                             ▼
                                 VoiceSession → Utterance flow → (Phase 5) Agent
```

Interface (in `:core:model`) so the rest of the app doesn't depend on sherpa-onnx:

```kotlin
interface VoiceEngine {
    val events: SharedFlow<VoiceEvent>     // Partial(text), Final(text), SpeechStart, SpeechEnd,
                                           // Keyword(word), Level(rms), Error(msg)
    suspend fun start()                    // opens mic, loads models (if not loaded)
    suspend fun stop()
    fun setMuted(asrMuted: Boolean)        // during TTS: ASR muted, KWS still active
    fun setHotwords(words: List<String>)   // app names, on-screen labels
}
```

---

## Task 4.1 — Models

Download from the official sherpa-onnx GitHub releases (record exact names, sizes, licenses in `docs/MODELS.md`):

| Slot | Model | Folder on phone |
|---|---|---|
| ASR (streaming) | best English streaming Zipformer from Spike B (transducer: encoder/decoder/joiner + tokens.txt; int8 versions preferred) | `files/models/asr/` |
| VAD | `silero_vad.onnx` | `files/models/vad/` |
| KWS | English keyword-spotting Zipformer (e.g. the `kws-zipformer-gigaspeech` package) | `files/models/kws/` |
| ASR 2nd pass (optional) | Whisper tiny.en/base.en int8 or Moonshine (sherpa-onnx offline models) | `files/models/asr2/` |

Model Manager (Phase 1) must show these slots as installed. `ModelPaths` helper resolves absolute paths and checks every required file exists → otherwise `VoiceEvent.Error("ASR model missing: …")` and a clear message in the panel.

## Task 4.2 — AudioCapture

```kotlin
class AudioCapture(private val dispatcher: CoroutineDispatcher) {
    val chunks = MutableSharedFlow<FloatArray>(extraBufferCapacity = 32,
                                               onBufferOverflow = BufferOverflow.DROP_OLDEST)
    // AudioRecord: source VOICE_COMMUNICATION (enables platform AEC/NS on most phones;
    // if recognition quality is worse on this phone, switch to VOICE_RECOGNITION — make it a setting)
    // sampleRate 16000, CHANNEL_IN_MONO, ENCODING_PCM_16BIT
    // bufferSize = max(getMinBufferSize * 2, 3200 * 2)
    // loop on dispatcher: read ShortArray(1600) → FloatArray (s / 32768f) → emit
}
```
- If available, enable `AcousticEchoCanceler.create(sessionId)` and `NoiseSuppressor.create(sessionId)` (check `isAvailable()`), release on stop.
- Handle `read()` errors (negative values) → emit Error and stop.
- Handle mic taken by another app (phone call, camera): `AudioManager.AudioRecordingCallback` / `isSilenced` (API 29+ `AudioRecordingConfiguration.isClientSilenced`) → emit `Error("Microphone in use by another app")`, set state Paused, resume when free.
- `AudioRecord` must be created **after** the service has the microphone FGS type (otherwise silent audio on Android 14+).

## Task 4.3 — VAD + streaming ASR

- One dedicated single-thread dispatcher `voiceDispatcher` for all sherpa objects (not thread-safe across threads).
- Pipeline per chunk:
  1. Feed VAD (Silero window 512 samples; buffer leftovers). Track `speaking` state.
  2. Keep a **pre-roll ring buffer** of 400 ms so the first word is not cut.
  3. On VAD speech start → emit `SpeechStart`, feed pre-roll + chunks to the online ASR stream.
  4. While speaking → `acceptWaveform` → while `isReady` → `decode` → emit `Partial(text)` if changed.
  5. On VAD speech end (silence ≥ `endOfSpeechMs`, default 700 ms) **or** recognizer `isEndpoint` → `inputFinished()`, decode remaining, emit `Final(text.trim())`, `reset(stream)`.
  6. Ignore finals shorter than 2 characters or only filler words ("uh", "um").
- Adaptive end-of-speech: if partial text ends with a word like "and", "then", "the", "to", extend silence to 1200 ms (user is mid-sentence).
- Optional second pass: run offline Whisper/Moonshine on the buffered utterance audio and use its text if confidence/length look better (setting, default off; measure latency).
- Normalize: lowercase→keep case for display, strip trailing punctuation, map number words for "second/third result" (keep as words; the router handles them).

## Task 4.4 — Keyword spotter (always on during a session)

- sherpa-onnx `KeywordSpotter` with keywords file built for: `stop`, `cancel`, `wait`, `done`, `stop listening`, `yes`, `no`, `continue` (generate keyword tokens with the sherpa-onnx tool `text2token` as described in its KWS docs; include the generated `keywords.txt` in `assets/` and copy to filesDir — it is tiny).
- Runs on **every** chunk, also while ASR is muted (TTS speaking) and while the agent executes.
- `stop`/`cancel`/`wait` → `VoiceEvent.Keyword` → agent cancels current task immediately (Phase 5), state → Waiting, short beep.
- `done`/`stop listening` → end session.
- `yes`/`no` used only in `Confirming` state; `continue` only in `Paused`.
- Tune keyword threshold to avoid false triggers from TV/music; make it a setting.

## Task 4.5 — VoiceSession + mic foreground service

`VoiceSession` (owned by `AssistantService`):

```kotlin
suspend fun run() {
    stateStore.set(Listening)
    engine.start()
    engine.events.collect { e ->
        when (e) {
            is Partial  -> stateStore.setPartial(e.text)
            is Final    -> if (StopPhrases.isEnd(e.text)) endSession("Session completed")
                           else utterances.emit(e.text)          // Phase 5 agent consumes
            is Keyword  -> handleKeyword(e.word)
            is Level    -> stateStore.setLevel(e.rms)
            is Error    -> stateStore.error(e.msg)
            else -> {}
        }
    }
}
```

- **Starting:** bubble tap (or mic button in panel) → upgrade FGS type to microphone using the method proven in Spike D → `VoiceSession.run()`. Mic permission missing → open MainActivity permission step.
- **Ending:** "done"/"stop listening"/"that's all"/"session complete", Stop button, notification Stop, **screen off** (`ACTION_SCREEN_OFF` receiver), silence timeout (`silenceTimeoutMin`), panic keys. On end: stop AudioRecord, downgrade FGS type back to `specialUse` (call `startForeground` again with only SPECIAL_USE), state → Stopped → after 1.5 s → Idle.
- **Queued commands:** utterances spoken while the agent is busy are queued (max 3) and shown as "queued" in the panel.
- **Sounds:** short earcons (start listening, stop, error) via `ToneGenerator` or tiny bundled OGG (respect silent mode).
- Visual: bubble ring cyan + waveform while `Listening`; Android's green mic indicator appears automatically.
- Keep models loaded for the whole session; unload 2 minutes after session end (memory).

## Task 4.6 — Hotword biasing

- sherpa-onnx transducer models support **hotwords / contextual biasing** (`hotwordsFile`/`hotwordsScore` with `modified_beam_search` decoding). Check this works with the chosen model in the pinned version.
- Hotword list = launchable app labels (from AppLauncher) + clickable labels from the latest Observation (top 50) + routine names.
- If the recognizer must be recreated to change hotwords, do it only between utterances and only when the list changed significantly (it's costly). If hotwords are not supported in this setup, skip and document — the fuzzy matching in the router covers most cases.

## Task 4.7 — Text-to-speech

`Speaker` wrapper around Android `TextToSpeech`:
- Init once; pick an installed **offline** voice: `tts.voices.filter { !it.isNetworkConnectionRequired && it.locale.language == "en" }`. If none, show a hint: "Install an offline voice in Settings → Text-to-speech".
- `speak(text)`: before speaking → `engine.setMuted(true)` (ASR muted, KWS on); `UtteranceProgressListener.onDone` → wait 250 ms → `setMuted(false)`.
- Request transient audio focus (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`) so music ducks.
- Replies are short. `speakReplies=false` → text only.

## Task 4.8 — Temporary router hookup (until Phase 5)

Connect `Final` utterances to a minimal rule router so the voice loop can be tested end-to-end now:
- "open X" → OpenApp, "go back" → Back, "go home" → Home, "scroll down/up" → Scroll, "click/tap X" → Click on fuzzy text match in current observation.
- Everything else → assistant message "I can't do that yet."
This code moves into the real `FastRouter` in Phase 5 (don't duplicate later — refactor).

---

## Acceptance checklist

- [ ] Airplane mode ON. Tap bubble → speak 10 commands in a row without touching the phone ("open youtube", "scroll down", "go back", "open chrome", "go home", "open settings", "scroll down", "go back", "open youtube", "done"). All recognized and executed; session ends on "done".
- [ ] Partial words appear while speaking (< 0.5 s lag); command runs within ~1 s after you stop talking.
- [ ] Saying "stop" while something runs cancels it within 0.5 s.
- [ ] TTS reply does not get transcribed as a new command.
- [ ] Screen off ends the session; mic indicator disappears.
- [ ] Incoming phone call → session pauses/ends cleanly, no crash.
- [ ] 15-minute session: no crash, no growing memory (`dumpsys meminfo` start vs end).
- [ ] Record: CPU % while listening, battery % used in 15 min.

## Common problems
TROUBLESHOOTING.md #6 (AAR), #9 (duplicate libonnxruntime.so), #12 (mic FGS), #22 (silent audio), #23 (sherpa model load crash), #24 (TTS hears itself), #25 (KWS false triggers).
