# Phase 5 Report — AI Brain & Agent Loop

## Summary
Phase 5 implemented the complete offline AI brain, structured GBNF grammar parser, compact ReAct prompt builder, safety guard classification system, conversation memory manager, and multi-step autonomous agent execution loop in `:feature:agent`.

## Files created
- `feature/agent/build.gradle.kts`
- `feature/agent/consumer-rules.pro`
- `feature/agent/src/main/kotlin/com/omnicontrol/feature/agent/llm/ActionGrammar.kt`
- `feature/agent/src/main/kotlin/com/omnicontrol/feature/agent/llm/LlmEngine.kt`
- `feature/agent/src/main/kotlin/com/omnicontrol/feature/agent/prompt/PromptBuilder.kt`
- `feature/agent/src/main/kotlin/com/omnicontrol/feature/agent/safety/SafetyGuard.kt`
- `feature/agent/src/main/kotlin/com/omnicontrol/feature/agent/memory/ConversationManager.kt`
- `feature/agent/src/main/kotlin/com/omnicontrol/feature/agent/loop/AgentEventListener.kt`
- `feature/agent/src/main/kotlin/com/omnicontrol/feature/agent/loop/AgentLoop.kt`
- `docs/PENDING_TESTS.md`

## Files modified
- `settings.gradle.kts` (added `:feature:agent`)
- `app/build.gradle.kts` (added `:feature:agent`)
- `feature/overlay/build.gradle.kts` (added `:feature:agent` and `:core:database`)
- `feature/overlay/src/main/kotlin/com/omnicontrol/feature/overlay/state/AssistantStateStore.kt` (added `SetAssistantState` event)
- `feature/overlay/src/main/kotlin/com/omnicontrol/feature/overlay/AssistantService.kt` (wired `AgentLoop`, `AgentEventListener`, and panel text input trigger)

## How it works
- **GBNF Constrained Decoding:** `ActionGrammar` defines an exact GBNF grammar guaranteeing that the LLM outputs strict JSON with fields `"thought"`, `"action"`, and optional `"say"`.
- **Offline Llama Engine & Fallback:** `LlamaCppEngine` connects directly to `libomnillama.so` (built in Phase 0) for on-device inference using quantized GGUF weights. When no local GGUF model is present, `FallbackLlmEngine` performs rule-based intelligent planning so the entire app is testable out of the box.
- **Compact System Prompt (<600 tokens):** `PromptBuilder` formats screen observations, active package, element summaries (IDs, types, text), sub-goals, and recent turn history into a highly token-efficient prompt for fast local inference.
- **SafetyGuard Risk Classifier:** Analyzes every planned action against the current screen observation, classifying actions into:
  - `SAFE`: Non-destructive navigation, reading, clicking harmless UI elements.
  - `CONFIRM`: Irreversible actions (purchases, money transfers, sending messages, deletions). Prompts the user via Compose dialog / Status Pill.
  - `HANDOFF`: High-security screens (lock screens, credential entry, banking apps). Prompts the user to take over manually.
  - `BLOCKED`: Restricted actions or prohibited operations.
- **Conversation & App Memory:** `ConversationManager` maintains current session history, active app stack, sub-goals, and persists sessions and messages into Room (`OmniDatabase`).
- **Autonomous ReAct Agent Loop:** `AgentLoop` executes the Observe -> Think -> Act -> Verify cycle with a 10-step budget, updating the UI state store through `AgentEventListener` and voicing progress updates via `TtsEngine`.

## Build status (commands + result)
- `./gradlew :app:assembleDebug` -> **BUILD SUCCESSFUL**
- `./gradlew testDebugUnitTest` -> **BUILD SUCCESSFUL**

## Pending Tests (per user instruction)
- Tracked in `docs/PENDING_TESTS.md` to prioritize fast device testing of the APK.
