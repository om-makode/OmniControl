# OmniControl — Phase Guides for Antigravity

This folder is the complete, step-by-step build guide for **OmniControl**, a personal AI Android phone operator.
Every file is written so an AI coding agent (Antigravity) can follow it without guessing.

## Files in this folder

| Order | File | What it is |
|---|---|---|
| — | `OMNICONTROL_PLAN.md` | Master plan: vision, architecture, decisions. Read once. |
| 1 | `00_GLOBAL_RULES.md` | Rules Antigravity must follow in **every** phase (versions, structure, code style, reporting, "when stuck" protocol). |
| 2 | `PHASE_0_Environment_and_Spikes.md` | Check Mac + phone, create the project, prove the 4 risky parts work. |
| 3 | `PHASE_1_App_Shell_and_Permissions.md` | Main app UI, permission wizard, settings, database skeleton. |
| 4 | `PHASE_2_Floating_Overlay.md` | Floating bubble, panel, state machine, notification. |
| 5 | `PHASE_3_Accessibility_and_Actions.md` | Read the screen, perform taps/typing/scrolls, manual test console. |
| 6 | `PHASE_4_Voice_Engine.md` | Offline continuous voice, VAD, keyword "stop", TTS. |
| 7 | `PHASE_5_AI_Agent.md` | Local LLM, fast command router, agent loop, safety. |
| 8 | `PHASE_6_Visual_Understanding.md` | Screenshots, OCR, "video with a car" matching, small vision model. |
| 9 | `PHASE_7_Navigator_Memory_Routines.md` | Screen graph, navigation memory, routines, teach mode, cross-app tasks. |
| 10 | `PHASE_8_Hardening_and_Polish.md` | Stability, battery, privacy checks, final docs. |
| 11 | `PHASE_9_Extras.md` | Optional extras (Shizuku, widget, multilingual, …). |
| any | `TROUBLESHOOTING.md` | Known errors and exact fixes. Antigravity should check here first when a build or runtime error appears. |
| live | `PROGRESS.md` | Checklist Antigravity updates after every task. |

> Where a phase file and the master plan differ, **the phase file wins** (phase files are more detailed and newer).

## How to work with Antigravity

1. Create an empty folder for the project, e.g. `~/Desktop/Om_Projects/OmniControl/`.
2. Copy this whole folder into it as `OmniControl/docs/` (so the agent can read the guides inside the repo).
3. Start a new Antigravity task for **each phase**. Paste this:

```
Read docs/00_GLOBAL_RULES.md, docs/TROUBLESHOOTING.md and docs/PHASE_<N>_*.md completely before doing anything.
Do only Phase <N>. Follow the tasks in order. After each task: build, fix errors, tick it in docs/PROGRESS.md.
If an error is not solved after 3 attempts, follow the "When stuck" protocol in 00_GLOBAL_RULES.md.
At the end, write docs/phases/PHASE_<N>_REPORT.md and give me the testing steps for my phone.
```

4. You test on your phone using the **Acceptance checklist** at the end of the phase file.
5. Only when all boxes pass, start the next phase in a new task.

## Rough timeline

| Phase | Effort (part-time) |
|---|---|
| 0 | 2–4 days |
| 1 | 3–4 days |
| 2 | 4–5 days |
| 3 | 6–8 days |
| 4 | 5–7 days |
| 5 | 8–12 days |
| 6 | 6–8 days |
| 7 | 7–10 days |
| 8 | 5–7 days |
| 9 | optional |
