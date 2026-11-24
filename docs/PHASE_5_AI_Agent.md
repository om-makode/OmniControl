# Phase 5 — AI Agent (local LLM, fast router, agent loop, safety)

**Goal:** natural-language commands work. Simple ones run instantly through rules; complex ones go through a local LLM that observes the screen, chooses one action at a time, and verifies. Sensitive actions always ask first.

**Prerequisite:** Phase 4 accepted. LLM model chosen in Phase 0 (`docs/MODELS.md`).

---

## Overview

```
Utterance ─► Normalizer ─► FastRouter ─(match)─► Action(s) ─► SafetyGuard ─► Executor ─► Verifier
                               │
                               └(no match)─► AgentLoop:
                                     observe → PromptBuilder → LlmEngine (grammar JSON)
                                     → ActionValidator → SafetyGuard → Executor → Verifier
                                     → ConversationManager.update → repeat (budget)
```

Modules: `:feature:llm` (engines), `:feature:agent` (router, loop, conversation, safety).

---

## Task 5.1 — LlmEngine interface + llama.cpp backend

```kotlin
interface LlmEngine {
    val name: String
    suspend fun load(config: LlmConfig): Result<Unit>
    suspend fun complete(req: LlmRequest): Result<LlmResponse>
    suspend fun countTokens(text: String): Int
    suspend fun unload()
    val isLoaded: Boolean
}
data class LlmConfig(val modelPath: String, val contextSize: Int = 4096, val threads: Int = 4,
                     val gpuLayers: Int = 0)
data class LlmRequest(val messages: List<ChatMessage>, val grammar: String?,
                      val maxTokens: Int = 256, val temperature: Float = 0.2f,
                      val stop: List<String> = emptyList())
data class LlmResponse(val text: String, val promptTokens: Int, val genTokens: Int, val ms: Long)
```

`LlamaCppEngine` (uses `:native:llama` from Phase 0, now completed):
- JNI additions: `applyChatTemplate(handle, rolesArray, contentsArray, addAssistant): String` using `llama_chat_apply_template` with the model's built-in template (don't hand-write chat formats).
- **Prompt cache / KV reuse:** keep the context alive between calls; if the new prompt starts with the same tokens as the previous one (system prompt + earlier turns), only evaluate the new suffix (compare token arrays, `llama_memory_seq_rm`/KV cache remove from the first different position — use whatever the pinned version provides). This makes steps 2–10× faster.
- Cancellation: a JNI `abort` flag checked between tokens (Kotlin `cancel` → set flag).
- Disable model "thinking" mode if the model has one (e.g. for Qwen3 add `/no_think` per its docs, or use the template flag) — we want short outputs.
- Load once per session start (or at app start if RAM allows); unload after idle (setting).
- Run on the single llama dispatcher.

## Task 5.2 — Action format, grammar, validator

The LLM outputs **one flat JSON object** (flat is easier for small models than nested):

```json
{"thought":"Search box is #2","type":"type_text","target":2,"text":"swift tutorials","direction":null,"say":"Searching for Swift tutorials"}
```

Field rules:
| type | target | text | direction |
|---|---|---|---|
| open_app | null | app name | null |
| open_url | null | url | null |
| click / long_press / clear_text / expand / collapse / select | id | null | null |
| type_text | id or null (focused) | text (end with `\n` = submit) | null |
| scroll | id or null | null | up/down/left/right |
| back / home / recents / notifications | null | null | null |
| find | null | query | null |
| wait | null | null | null |
| read_screen | null | question/what to extract | null |
| ask_user | null | question | null |
| done | null | summary | null |

`assets/grammar/action.gbnf`:
```
root      ::= "{" ws "\"thought\":" ws str80 "," ws "\"type\":" ws type "," ws "\"target\":" ws (id | "null") "," ws "\"text\":" ws (str300 | "null") "," ws "\"direction\":" ws (dir | "null") "," ws "\"say\":" ws str80 ws "}"
type      ::= "\"open_app\"" | "\"open_url\"" | "\"click\"" | "\"long_press\"" | "\"type_text\"" | "\"clear_text\"" | "\"scroll\"" | "\"back\"" | "\"home\"" | "\"recents\"" | "\"notifications\"" | "\"expand\"" | "\"collapse\"" | "\"select\"" | "\"find\"" | "\"wait\"" | "\"read_screen\"" | "\"ask_user\"" | "\"done\""
dir       ::= "\"up\"" | "\"down\"" | "\"left\"" | "\"right\""
id        ::= [1-9] [0-9]? [0-9]?
str80     ::= "\"" char{0,80} "\""
str300    ::= "\"" char{0,300} "\""
char      ::= [^"\\\x00-\x1F] | "\\" ["\\/nt]
ws        ::= [ ]?
```
(If the pinned llama.cpp doesn't support `{m,n}` repetition, use `char*` and limit with `maxTokens`.)

`ActionValidator.toAction(json, obs): Result<Action>`:
- Parse with kotlinx.serialization (`ignoreUnknownKeys`).
- Check required fields per type (table above); target exists in `obs` and is enabled; scroll target is scrollable (or null); type_text target is editable or a focused field exists; URL is http/https; text ≤ 300 chars.
- On invalid → do **not** execute; feed the error back to the LLM once ("Invalid: element 42 does not exist. Choose from the list."), then ask the user.
- Unit tests with good and bad JSON samples.

## Task 5.3 — Observation serializer + PromptBuilder

`ObservationSerializer.toText(obs, maxElements = 60)` → compact format:
```
APP: YouTube  SCREEN-FP: a1b2  KEYBOARD: hidden  FOCUSED: none
[1] button "Back"
[2] textfield "Search YouTube" (focused)
[3] list ↕
  [4] card "Swift Tutorial for Beginners · CodeWithChris · 1.2M views"
  [5] card "Learn Swift in 1 hour · Programming with Mosh"
[7] tab "Home" (selected)   [8] tab "Shorts"
… 12 more items below (scroll down)
```
Rules: indent children of lists; skip decorative TEXT elements far from any interactive element when over budget; truncate texts to 80 chars; mark `(password)`, `(disabled)`, `(checked)`, `(selected)`; dialogs first with header `DIALOG:`.

`PromptBuilder.build(ctx: AgentContext): List<ChatMessage>`:

**System message** (fixed → cached):
```
You are OmniControl, an agent that operates an Android phone for the user.
Each turn you get the user's goal, recent history and the CURRENT SCREEN as a numbered list.
Reply with exactly ONE JSON action. Rules:
- Use only element numbers from the CURRENT SCREEN. Never invent numbers.
- Prefer the most direct path. One action per reply.
- To search: tap the search field/icon, then type_text with "\n" at the end.
- If the target is not visible, scroll or use find. If unsure which one, use ask_user.
- When the goal is reached, reply done with a short summary.
- Never type passwords, PINs or OTPs. If login, payment, CAPTCHA or a protected screen appears, use ask_user to hand over.
- "say" is a very short message for the user (max 8 words).
Actions: open_app(text=name) open_url(text) click(target) long_press(target) type_text(target,text)
clear_text(target) scroll(target?,direction) back home recents notifications expand(target)
collapse(target) select(target) find(text=query) wait read_screen(text=question) ask_user(text) done(text)
Examples:
<3 short examples: search flow, scroll+find, done>
```

**User message** (changes every step):
```
GOAL: <current goal or sub-goal>
SESSION: <compressed conversation context, ≤ 300 tokens>
STEPS SO FAR: <last 5 steps: action → ok/fail + reason>
CURRENT SCREEN:
<serialized observation>
```
Budget check with `countTokens`; shrink observation (fewer elements) then history if over `contextSize - maxTokens - 200`.

## Task 5.4 — FastRouter (full)

Pure Kotlin, unit-tested, runs before the LLM. Input: normalized utterance + current observation + conversation context. Output: `List<Action>` or `null` (→ LLM).

| Pattern (examples) | Result |
|---|---|
| open/launch/start/go to `<app>` (fuzzy app match ≥ 0.88, unambiguous) | OpenApp |
| go back / back / previous screen | Back |
| go home / home screen | Home |
| recent apps / switch apps | Recents |
| scroll / scroll down/up/left/right / next page | Scroll |
| tap/click/press/open `<label>` — exactly 1 element fuzzy-matches label ≥ 0.85 | Click |
| (open/play/tap) the `first/second/…/last/1st/2nd/number N` (result/video/item/one) | Click Nth LIST_ITEM/CARD of the main list (from `lastResults` in context if screen unchanged) |
| type `<text>` / enter `<text>` (field focused or exactly 1 editable) | TypeText |
| search (for) `<q>` — when a search field or search button is visible | [Click search if needed] + TypeText(q + "\n") — as a 2-step mini plan with verification between |
| open `<url-like>` | OpenUrl |
| notifications / quick settings | global action |
| yes / no (state Confirming) · continue (state Paused) | confirmation handling |
| done / stop listening / that's all | end session |

Anything with "and then", multiple verbs, references ("the one with…", "that video"), or no unique match → LLM.

## Task 5.5 — ConversationManager

Holds the session memory (kept in RAM, messages also saved to Room):

```kotlin
data class AgentContext(
    val turns: ArrayDeque<Turn>,            // last 8 user/assistant turns
    val summary: String,                    // LLM-free rolling summary: "User opened YouTube, searched 'swift tutorials', opened 2nd result"
    val appStack: List<String>,             // packages used this session
    val lastResults: ResultSet?,            // last list shown: items with id, text, box, contentFp, package
    val entities: Map<String, String>,      // "the link" -> url, "that video" -> title
    val sessionClipboard: String?,          // Phase 7 uses it
    val pending: List<String>,              // queued utterances
    val preferences: Map<String, String>    // session prefs ("browser" -> chrome)
)
```
- Update after every action: append a one-line step summary (template-based, not LLM) and set `lastResults` when the screen has a list of ≥ 3 cards/items.
- Reference resolution helpers (used by router + included in prompt): "it/that/this one" → last clicked entity; "the previous screen" → Back; "go back and open the X one" → Back + re-use `lastResults` if contentFp matches.

## Task 5.6 — AgentLoop (ReAct) + sub-goals

```kotlin
suspend fun handle(utterance: String) = coroutineScope {
    val fast = router.route(utterance, observe(), ctx)
    if (fast != null) return@coroutineScope runActions(fast)

    val goals = if (looksMultiStep(utterance)) planner.subGoals(utterance) else listOf(utterance)
    for (goal in goals) {
        var steps = 0
        while (steps++ < settings.maxStepsPerCommand) {
            ensureActive()                                     // stop keyword → cancel
            state.set(Observing); val obs = observe()
            if (obs.isSecure) { handoff("This screen is protected. Please do it yourself and say continue."); return@coroutineScope }
            state.set(Processing)
            val out = llm.complete(promptBuilder.build(ctx.with(goal, obs)))
            val action = validator.toAction(out, obs).getOrElse { retryOnceOrAsk(it); continue }
            when (action) {
                is Done    -> { say(action.summary); break }
                is AskUser -> { askAndWait(action.question); break }
                is ReadScreen -> { answer(readScreen(action, obs)); break }
                else -> {
                    if (!safety.allow(action, obs)) return@coroutineScope   // confirm/handoff/blocked
                    state.set(Executing(describe(action), steps, null))
                    val res = executor.execute(action, obs)
                    ctx.record(action, res)
                    detectStuckOrLoop()?.let { askUser(it); return@coroutineScope }
                }
            }
        }
    }
}
```
- `looksMultiStep`: contains "and", "then", ",", or ≥ 2 action verbs. `planner.subGoals` asks the LLM (separate small grammar: JSON array of 1–5 short strings). Show the checklist in the panel.
- **Stuck:** contentFp unchanged after 2 actions → add hint to prompt "Last actions changed nothing; try another element or scroll". After 3 → ask user.
- **Loop:** same (structureFp, action signature) 3 times → ask user.
- Per-command timeout 60 s; cancellation from "stop" keyword cancels the coroutine (executor and LLM must react to cancellation).
- `read_screen`: collect text of the current screen (and up to 3 scrolls for "summarize"), then call the LLM **without grammar** with a "answer in 2–3 short sentences" prompt; speak it.
- Every step goes into the panel as an action card; failures show the reason.

## Task 5.7 — SafetyGuard

```kotlin
enum class Risk { SAFE, CONFIRM, HANDOFF, BLOCKED }
fun assess(action: Action, obs: Observation, ctx: AgentContext): Risk
```
- **BLOCKED:** any action inside Settings → Accessibility / OmniControl app info / Developer options / factory reset / device admin; uninstalling OmniControl.
- **HANDOFF:** target or screen contains password/PIN/OTP fields; package in sensitive list (banking, UPI/payment apps — editable list in Settings, prefilled with common ones); screen text contains "Pay", "Payment", "Enter UPI PIN", "CVV", "Place order" *and* the action would tap it; CAPTCHA words; `isSecure`.
- **CONFIRM:** target label (en) matches `send|post|publish|share|delete|remove|uninstall|call|subscribe|follow|like|buy|order|book|confirm|transfer|submit|reply|forward|block|report|archive|erase|reset|log ?out|sign ?out` (word boundaries), or TypeText followed by submit in a messaging app, or toggling Settings switches.
- **SAFE:** everything else.
- CONFIRM flow: state `Confirming("Send this message to Mom?")`, TTS asks, wait for "yes"/"no" (KWS or ASR) or panel buttons, timeout 20 s → cancel. Never auto-confirm.
- HANDOFF flow: state `Paused(reason)`; agent waits for "continue" or panel button, then re-observes.
- Per-app policy table (`AppPolicyEntity`): allowAgent=false → agent refuses to operate in that app.
- Unit tests with sample observations.

## Task 5.8 — Home Brain backend (optional)

`HomeBrainEngine : LlmEngine` using HTTP to a server on the user's LAN:
- Support **llama.cpp `llama-server`** (`POST /v1/chat/completions` with `grammar` or `response_format`/`json_schema`) and **Ollama** (`POST /api/chat` with `format` = JSON schema). Setting chooses type + URL.
- Client: `HttpURLConnection` or OkHttp (ask before adding OkHttp). Timeout 20 s. Only allow private IP ranges (10.x, 172.16–31.x, 192.168.x, `.local`). Blocked when Offline mode is on.
- Health check every 30 s during a session; if unreachable → fall back to on-device engine, show badge change.
- Docs: `docs/HOME_BRAIN.md` with exact commands for the Mac (e.g. `brew install llama.cpp` then `llama-server -m <model>.gguf --host 0.0.0.0 --port 8080`, or `ollama serve` with `OLLAMA_HOST=0.0.0.0`).

## Task 5.9 — Evaluation harness

- `testdata/eval/cases.jsonl`, ≥ 40 cases, each:
```json
{"id":"yt_search_1","screen":"screens/youtube_home.json","context":"","command":"search for swift tutorials","expect":{"type":["click","type_text"],"target_any":[3,5]}}
```
- **JVM test runner** (`:feature:agent` test source set) that uses the real `PromptBuilder`, `ObservationSerializer`, `ActionValidator` and `HomeBrainEngine` pointed at a `llama-server` on the Mac running the **same GGUF model** as the phone (fast iteration). Skipped automatically if the server isn't running (`assumeTrue`).
- Developer screen "Run eval on device" runs the same cases with `LlamaCppEngine`.
- Output: valid-JSON %, correct-type %, correct-target %, avg latency → append to `docs/MODELS.md`.
- Re-run after any prompt or model change. Target: ≥ 90% valid, ≥ 75% correct target.

---

## Acceptance checklist

- [ ] Voice session: "Open YouTube" → "Search for Swift programming tutorials" → "Open the second result" → "Go to the channel" → "Go back" → "Done" — all correct.
- [ ] "Open Chrome and search for Kotlin coroutines" works as one command (sub-goals shown).
- [ ] "Go back and open the third one" uses the previous results correctly.
- [ ] Saying "stop" mid-task cancels within 0.5 s, also during LLM generation.
- [ ] "Send hi to <contact> on WhatsApp" stops at **Confirming** before pressing Send; "no" cancels.
- [ ] Banking app / password field → handoff message, no typing.
- [ ] Fast-path commands respond in < 300 ms after end of speech (log timings).
- [ ] Eval results recorded; targets met (or documented with plan, e.g. use Home Brain).
- [ ] Airplane mode: everything above works with the on-device model.

## Common problems
TROUBLESHOOTING.md #7, #26 (model outputs garbage / wrong template), #27 (slow generation), #28 (OOM / app killed when loading model), #29 (grammar parse error), #30 (Home Brain unreachable).
