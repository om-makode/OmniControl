# Phase 7 — Universal Navigator, Navigation Memory, Routines & Cross-App Workflows

**Goal:** the assistant finds its way inside apps without deep links, remembers how it got somewhere, gets faster each time, recovers when an app changes, and can chain apps together. The user can also teach routines by demonstration.

**Prerequisite:** Phase 6 accepted (Phase 7 can start after Phase 5 if vision is delayed).

---

## Task 7.1 — Element signatures & screen nodes

**ElementSignature** (stable way to describe "the same button" across visits, independent of coordinates):
```kotlin
@Serializable data class ElementSignature(
    val viewId: String?,            // strongest when present
    val role: Role,
    val textPattern: String?,       // normalized text/desc; digits → '#', lowercased, max 40 chars
    val relX: Float, val relY: Float, // center position as fraction of screen (tolerance 0.12)
    val inListItem: Boolean
)
fun ElementSignature.matches(e: UiElement, screen: Size): Float  // 0..1 score
```
Scoring: viewId equal +0.5, role equal +0.15, text pattern equal +0.25 (fuzzy ≥ 0.85 → +0.15), position within tolerance +0.1. Match if ≥ 0.6.

**ScreenNode** = `structureFp` (Phase 3) + package + a human label (best title text on the screen, e.g. "Your Orders") + set of stable labels (for search: "does this screen contain 'Orders'?").

## Task 7.2 — Screen graph recording (Room migration v1 → v2)

New entities: `ScreenEntity(id, pkg, fp, label, labelsJson, appVersionCode, lastSeen, visits)`, `EdgeEntity(id, fromScreenId, toScreenId, actionType, signatureJson, textArg, successCount, failCount, lastUsed, verified)`, `RoutineEntity`, `RoutineStepEntity`. Write a proper `Migration(1, 2)` and a migration test.

`GraphRecorder` subscribes to every executed action (agent, router, console, and teach mode):
- Before action: `fromNode = upsert(obs)`. After success: `toNode = upsert(after)`. If `fromNode != toNode` → upsert edge (signature of the target element, action type, text arg for type_text only if not sensitive) and `successCount++`. On failure → `failCount++`.
- Don't record edges for: Back/Home (Back is implicit reverse), typing into fields of messaging apps (privacy), screens marked secure.
- App version: `packageManager.getPackageInfo(pkg, 0).longVersionCode` stored on each node; when it changes, mark that app's edges `verified=false` (kept, but lower priority).

## Task 7.3 — Route finding & stale edges

`Navigator.navigate(pkg, goal: String): NavResult`:
1. **Goal resolution:** find target nodes in this app whose label or labels fuzzy-match the goal ("orders" → node labeled "Your Orders"). Also check the routine table (7.5).
2. **Locate current node** (current obs `structureFp`; if unknown → assume app start node after relaunch).
3. **Path search:** Dijkstra over edges with cost = `1 + failCount*2 − min(successCount,5)*0.1 + (if !verified) 1`. Max path length 8.
4. **Execute edge by edge:** for each edge find the element on the current screen whose signature matches best; if none ≥ 0.6 → edge stale → mark `failCount++` → **fall back to LLM exploration** from here with the goal ("Find 'orders' in this app"); new successful steps are recorded automatically → graph heals itself.
5. **Verify** destination: target labels visible.
6. If no path → LLM exploration with hints: "Common places for '<goal>': Profile, Account, You tab, ☰ menu, Settings, More."
- Expose to the agent as a high-level action `navigate(text=goal)` (add to grammar + validator) — the LLM can call it for "go to my orders", and the FastRouter calls it when the goal matches a known node label in the current app.

## Task 7.4 — Deep link / intent table

`assets/deeplinks.json`, small and safe (only VIEW intents):
```json
[
  {"app":"youtube","pattern":"search (?<q>.+)","uri":"https://www.youtube.com/results?search_query={q}","pkg":"com.google.android.youtube"},
  {"app":"maps","pattern":"(navigate to|directions to) (?<q>.+)","uri":"google.navigation:q={q}","pkg":"com.google.android.apps.maps"},
  {"app":"maps","pattern":"(find|show) (?<q>.+) on maps","uri":"geo:0,0?q={q}"},
  {"app":"playstore","pattern":"(find|install) (?<q>.+) on play store","uri":"market://search?q={q}"},
  {"app":"chrome","pattern":"search (the web|google) for (?<q>.+)","uri":"https://www.google.com/search?q={q}","pkg":"com.android.chrome"}
]
```
Plus Settings screens (`Settings.ACTION_WIFI_SETTINGS`, `ACTION_BLUETOOTH_SETTINGS`, `ACTION_DISPLAY_SETTINGS`, `ACTION_SOUND_SETTINGS`, …) mapped from phrases. Always URL-encode `{q}`. Verify the target app opened; if not, fall back to UI navigation.
Priority order (from master plan): routine → deep link → graph → exploration → vision.

## Task 7.5 — Routines & aliases

**Routine** = named list of steps, each step = (action type, element signature or text arg, optional parameter placeholder, expected screen label).
- Created by: (a) "Save as routine?" offer after a successful multi-step command (user says yes + name), (b) Teach mode (7.6), (c) manual editing (simple list UI: rename, reorder, delete step, test-run).
- Trigger phrases: name + variants ("show my orders", "my orders"). Parameters: `{query}` captured from phrases like "search YouTube for {query}".
- Execution: like edges — match signatures, verify each step, fall back to LLM on mismatch, then offer to update the routine.
- Routines manager screen in the main app + export/import JSON (SAF).

**Aliases** (`AliasEntity`): phrase → value, kinds `app` ("my shopping app" → amazon pkg), `contact` ("mom" → "Amma"), `place`, `text` ("my email" → user's email — stored locally, only typed when user asks). UI to manage. Learned automatically when the user corrects: "No, I meant Flipkart" → offer to save alias.

## Task 7.6 — Teach mode

- Start: say "watch me" / "teach mode" or panel button. Bubble shows a red recording dot; agent stops acting.
- Record from accessibility events: `TYPE_VIEW_CLICKED`, `TYPE_VIEW_LONG_CLICKED`, `TYPE_VIEW_TEXT_CHANGED` (final text only, and **never** from password fields), `TYPE_VIEW_SCROLLED`, `TYPE_WINDOW_STATE_CHANGED` (app switches). For each event, read the source node **inside the callback**, build an `ElementSignature`, and capture the current `structureFp`.
- Collapse noise: consecutive scrolls → one scroll step; text changes → one type step with final text (ask: "Should this text be a parameter?").
- Stop: say "stop recording" / "done teaching" → ask for a name → save routine → offer an immediate test run.
- Teach mode respects SafetyGuard: steps that would be CONFIRM are saved with `requiresConfirm=true`.

## Task 7.7 — Session clipboard, Find, popup handler

**Session clipboard:**
- `copy` intents ("copy the link", "copy this URL") → extract from current observation: URL regex over element texts (prefer the latest message bubble / address bar), or the selected/focused text. Store in `ctx.sessionClipboard` and `entities["the link"]`. Optionally also `ClipboardManager.setPrimaryClip` (writing is allowed).
- "open the link I copied" → session clipboard first; if empty, explain that Android blocks background clipboard reading and offer: "Tap the bubble's paste button" (panel text field focus → user long-press paste) — never silent reads.
- Add action `copy_text(text=what)` to the grammar (resolved locally, no LLM free text).

**Find** (exists from Phase 3) — now used by navigator and LLM: "scroll until you see X".

**Popup handler** (runs after every settle, before the next agent step):
- Known safe dismissals: "Not now", "Skip", "Skip ad" (wait until enabled), "No thanks", "Maybe later", "Close", "Dismiss", "Got it", "×" close buttons on promo dialogs, "Continue in app" vs "Open in browser" banners (choose app).
- Permission dialogs from other apps (`com.google.android.permissioncontroller`) → **never** auto-grant; ask the user.
- Unknown dialogs → included in observation as `DIALOG:` and handled by LLM / user.
- Rules in `assets/popups.json`, each with package (optional), text pattern and button to press. Log every auto-dismiss in the panel ("Closed a popup: Not now").

## Task 7.8 — Five cross-app workflows

Make each pass end-to-end by voice (as one command, and also as step-by-step commands):

1. **WhatsApp → Chrome:** "Open WhatsApp, find the latest message with a link, and open it in Chrome."
   open_app → click top chat (or chat matching contact if said) → find URL text (scroll up up to 5 times) → copy_text → open_url (with `pkg=com.android.chrome` intent) → verify Chrome domain.
2. **Chrome summarize:** "Open Chrome, search for Supabase documentation, open the first result and summarize it." → deep link search → click first result → read_screen (3 scrolls max) → 2–3 sentence summary spoken.
3. **YouTube subscribe:** "Open YouTube, search iOS concurrency tutorials, open the second result and subscribe to the channel." → … → Subscribe → **Confirming** → yes → verify "Subscribed".
4. **Files:** "Open my file manager and open the latest PDF." → app alias "file manager" → Recent/Documents → sort by date if available → first `.pdf` → open.
5. **Shopping:** "Open Amazon/Flipkart, go to my orders and open the latest order." → navigate("orders") → click first order.

Record each run with traces on; failures become eval cases (Phase 5 harness) and graph/routine fixes.

---

## Acceptance checklist

- [ ] "Show my orders" second time is ≥ 2× faster than the first (uses graph/routine) — log both times.
- [ ] After app update or a moved button (test by clearing the app's data or changing language), navigation rediscovers and repairs the graph.
- [ ] Teach mode: record a 4-step task, name it, replay works by voice.
- [ ] Parameter routine: "search YouTube for <anything>" works from a routine.
- [ ] Aliases: "open my shopping app" opens the right app after the alias is saved.
- [ ] Popups ("Not now", "Skip ad") are handled automatically and shown in the panel; permission dialogs are not auto-granted.
- [ ] All 5 workflows pass (at least 4/5 runs each).
- [ ] Migration test v1→v2 passes; existing history is kept.

## Common problems
TROUBLESHOOTING.md #21 (stale nodes), #34 (graph explodes with too many nodes → fingerprint too specific), #35 (routine replays click wrong item), #36 (open_url opens wrong browser).
