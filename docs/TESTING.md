# OmniControl — Manual & Edge Case Testing Guide

This guide covers real-world test scenarios across daily usage, device interruptions, and cross-app workflows.

## Interruption & Edge-Case Matrix

| # | Scenario | Procedure | Expected Outcome |
|---|---|---|---|
| 1 | Incoming Phone Call | Call the test phone during an active voice session | Mic is immediately released; assistant pauses or cleanly ends session |
| 2 | Screen Off / Lock | Press Power button while assistant is active | `screenReceiver` detects `ACTION_SCREEN_OFF`, ends session, releases mic |
| 3 | Screen Rotation | Rotate phone between Portrait and Landscape with panel open | Floating bubble and glassmorphic panel adjust smoothly without crashing |
| 4 | Notification Shade Pull | Swipe down notification shade while bubble is floating | Bubble remains touchable or status pill updates cleanly |
| 5 | Background Noise / Silence | Leave mic open with ambient talking/music | VAD suppresses false triggers; barge-in keyword spotter only triggers on "stop"/"cancel" |
| 6 | Secure / Black Screen | Open bank app / lock screen / private browsing | `ScreenshotManager` detects secure window (>98% black pixels) and pauses for manual handoff |
| 7 | Annoying Popups | Open an app showing a "Rate this app" or "Not now" modal | `PopupHandler` auto-dismisses promo modal and logs to panel |
| 8 | Accessibility Disable | Turn off OmniControl in Accessibility Settings | App detects disabled service and shows informative banner |
| 9 | Uncaught Crash Handling | Trigger unexpected runtime exception | `CrashHandler` logs stack trace to `files/crashes/` without losing persistent database state |
| 10 | Airplane Mode | Enable Airplane Mode and run navigation commands | FastRouter, offline TTS, SpeechRecognizer, and on-device agent work completely offline |

## 5 Cross-App Workflows

1. **WhatsApp → Chrome:** Spoken command: *"Open WhatsApp, find the message with a link, and open it in Chrome."*
2. **Chrome Summarize:** Spoken command: *"Open Chrome, search for Supabase documentation, open first result."*
3. **YouTube Subscribe:** Spoken command: *"Open YouTube, search for concurrency tutorials, open the second video."*
4. **File Manager:** Spoken command: *"Open my files and open the latest PDF."*
5. **Universal Navigation:** Spoken command: *"Open settings, go to Wi-Fi settings."*
